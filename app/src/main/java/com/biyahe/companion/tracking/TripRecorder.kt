package com.biyahe.companion.tracking

import com.biyahe.companion.sensors.MotionAnalyzer
import com.biyahe.companion.data.model.RoutePoint
import com.biyahe.companion.sensors.SegmentMotion
import com.biyahe.companion.safety.CrashDetector
import com.biyahe.companion.safety.SpeedWatcher
import com.biyahe.companion.safety.WeaveDetector
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.safety.Tuning
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.biyahe.companion.sensors.SensorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The recording engine. While a trip runs it listens to the accelerometer, gyroscope,
 * magnetometer (heading), barometer, GPS and microphone at the same time and keeps
 * running totals. It does NOT score the trip or save it - that is Section 6.
 *
 *   TripService  --start(scope)-->  TripRecorder  --state-->  screens
 *   TripService  --stop()------->  TripRecorder  --finished-->  Section 6 (score + save)
 */
class TripRecorder(
    private val hub: SensorHub,
    private val location: LocationTracker,
    private val noiseMeter: NoiseMeter,
    /** Current speed limit (km/h), or null when the warning is off. Read every second. */
    private val speedLimit: () -> Int? = { null },
    private val crashDetectionOn: () -> Boolean = { false },
    private val weavingWarningOn: () -> Boolean = { false },
    /** Sensitivity settings, read when each trip starts. */
    private val tuning: () -> Tuning = { Tuning.DEFAULT }
) {
    private var weave = WeaveDetector()

    /** Driving or riding: set before start; decides which guards speak. */
    @Volatile var mode: TripMode = TripMode.DRIVING
    private var speedWatcher = SpeedWatcher()
    private var crashDetector = CrashDetector()
    private val _events = MutableSharedFlow<TripEvent>(extraBufferCapacity = 8)

    /** Speeding warnings and suspected crashes, for AppContainer to act on (voice, alarm). */
    val events: SharedFlow<TripEvent> = _events.asSharedFlow()

    private var motion = MotionAnalyzer()
    private val route = RouteAccumulator()
    private val noise = NoiseAnalyzer()
    private val pressureLog = CopyOnWriteArrayList<PressureSample>()
    /** One entry per accepted route point: the motion felt on the way to it. */
    private val segments = CopyOnWriteArrayList<SegmentMotion>()
    private val jobs = mutableListOf<Job>()

    @Volatile private var startedAt = 0L
    @Volatile private var heading: Float? = null
    @Volatile private var pressure: Float? = null
    @Volatile private var lastFixAt = 0L
    @Volatile private var lastAccuracy: Float? = null
    @Volatile private var lastPressureLoggedAt = 0L

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _finished = MutableStateFlow<RecordedTrip?>(null)

    /** The most recent finished recording, until [consumeFinished] is called. */
    val finished: StateFlow<RecordedTrip?> = _finished.asStateFlow()

    fun isRecording(): Boolean = _state.value.isRecording

    /** Called by Section 6 once the finished trip has been scored and saved. */
    fun consumeFinished() {
        _finished.value = null
    }

    /** Live route so far, for drawing on the map. */
    fun routeSnapshot(): List<TrackPoint> = route.snapshot()

    /** Live route with roughness per point, for the coloured map. */
    fun roughRouteSnapshot(): List<RoutePoint> = withRoughness(route.snapshot())

    private fun withRoughness(points: List<TrackPoint>): List<RoutePoint> {
        val segs = segments.toList()
        return points.mapIndexed { i, p ->
            val s = segs.getOrNull(i)
            RoutePoint(p.lat, p.lon, roughness = s?.roughness, bumps = s?.bumps ?: 0)
        }
    }

    /** Barometer readings logged so far (one per 30 s), for the live rain warning. */
    fun pressureSnapshot(): List<PressureSample> = pressureLog.toList()

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (_state.value.isRecording) return
        motion.reset(); route.reset(); noise.reset(); pressureLog.clear(); segments.clear()
        // Fresh detectors with this phone's sensitivity settings.
        val t = tuning()
        motion = MotionAnalyzer(bumpThreshold = t.bumpThreshold, turnThreshold = Math.toRadians(t.turnDps), vibrationFloor = t.vibrationFloor)
        speedWatcher = SpeedWatcher(overForMs = t.speedGraceSec * 1_000L, repeatMs = t.speedRepeatSec * 1_000L)
        crashDetector = CrashDetector(impactG = t.crashImpactG, fromKmh = t.crashFromKmh)
        weave = WeaveDetector(swerveDeg = t.weaveDeg, warnCount = t.weaveCount)
        heading = null; pressure = null; lastFixAt = 0L; lastAccuracy = null; lastPressureLoggedAt = 0L
        _finished.value = null
        startedAt = System.currentTimeMillis()
        _state.value = RecorderState(isRecording = true, startedAtMs = startedAt)

        jobs += scope.launch {
            hub.accelerometer().collect {
                motion.addAccelerometer(it)
                crashDetector.addAccel(it.magnitude, it.timestampMs)
                weave.onAccel(it)
            }
        }
        jobs += scope.launch {
            hub.gyroscope().collect {
                motion.addGyroscope(it)
                weave.onGyro(it)
            }
        }
        jobs += scope.launch { hub.heading().collect { heading = it } }
        jobs += scope.launch {
            hub.pressureHpa().collect { hpa ->
                pressure = hpa
                val now = System.currentTimeMillis()
                if (now - lastPressureLoggedAt >= PRESSURE_LOG_MS) {
                    lastPressureLoggedAt = now
                    pressureLog.add(PressureSample(now, hpa))
                }
            }
        }
        jobs += scope.launch {
            location.points().collect { p ->
                lastFixAt = System.currentTimeMillis()
                lastAccuracy = p.accuracyM
                if (route.add(p)) segments.add(motion.takeSegment())
                // Crash rules use only real GPS speeds from fresh fixes.
                if (p.speedMps >= 0f && crashDetectionOn() && crashDetector.addFix(p.speedMps * 3.6, p.timeMs)) {
                    _events.tryEmit(TripEvent.CrashSuspected)
                }
                if (p.speedMps >= 0f && weave.onFix(p.lat, p.lon, p.speedMps * 3.6, p.timeMs) && weavingWarningOn() && mode == TripMode.DRIVING) {
                    _events.tryEmit(TripEvent.Weaving)
                }
            }
        }
        jobs += scope.launch { noiseMeter.levels().collect { noise.addDb(it) } }
        jobs += scope.launch {
            while (isActive) {
                publish()
                delay(1_000)
            }
        }
    }

    /** Stops every sensor and returns what was recorded (also kept in [finished]). */
    @Synchronized
    fun stop(): RecordedTrip? {
        if (!_state.value.isRecording) return null
        jobs.forEach { it.cancel() }
        jobs.clear()
        val end = System.currentTimeMillis()
        val trip = RecordedTrip(
            startedAtMs = startedAt,
            endedAtMs = end,
            distanceM = route.distanceM(),
            maxSpeedKmh = route.maxSpeedMps() * 3.6,
            motion = motion.summary(),
            noise = noise.summary(),
            route = route.snapshot(),
            roughRoute = withRoughness(route.snapshot()),
            pressure = pressureLog.toList(),
            speedWarnings = speedWatcher.warnings,
            overspeedSec = speedWatcher.overspeedMs / 1000,
            weavingCount = weave.weaves,
            mode = mode
        )
        _finished.value = trip
        _state.value = _state.value.copy(isRecording = false)
        return trip
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val m = motion.summary()
        val last = route.lastPoint()
        val gps = when {
            !location.isGpsEnabled() -> GpsStatus.OFF
            lastFixAt == 0L || now - lastFixAt > FIX_STALE_MS -> GpsStatus.SEARCHING
            (lastAccuracy ?: Float.MAX_VALUE) > WEAK_ACCURACY_M -> GpsStatus.WEAK
            else -> GpsStatus.GOOD
        }
        val speedKmh = route.currentSpeedMps(now) * 3.6
        val limit = speedLimit()
        if (speedWatcher.update(speedKmh, limit, now) && limit != null) {
            _events.tryEmit(TripEvent.Speeding(limit, speedKmh))
        }
        _state.value = RecorderState(
            isRecording = true,
            startedAtMs = startedAt,
            elapsedMs = now - startedAt,
            distanceM = route.distanceM(),
            speedKmh = speedKmh,
            maxSpeedKmh = route.maxSpeedMps() * 3.6,
            gpsStatus = gps,
            gpsAccuracyM = lastAccuracy?.takeIf { it < Float.MAX_VALUE },
            pointCount = route.pointCount(),
            lastLat = last?.lat,
            lastLon = last?.lon,
            bumpiness = motion.liveBumpiness(),
            bumpCount = m.bumpCount,
            sharpTurnCount = m.sharpTurnCount,
            noiseDb = noise.liveDb(),
            avgNoiseDb = noise.averageDb(),
            headingDeg = heading,
            pressureHpa = pressure,
            speedLimitKmh = limit,
            overLimit = limit != null && speedKmh > limit,
            speedWarnings = speedWatcher.warnings,
            weavingCount = weave.weaves,
            mode = mode
        )
    }

    private companion object {
        const val PRESSURE_LOG_MS = 30_000L
        const val FIX_STALE_MS = 10_000L
        const val WEAK_ACCURACY_M = 20f
    }
}
