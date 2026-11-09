package com.biyahe.companion.safety

import android.hardware.SensorManager
import com.biyahe.companion.data.model.HazardType
import com.biyahe.companion.data.repository.HazardRepository
import com.biyahe.companion.scoring.WeatherWatcher
import com.biyahe.companion.sensors.SensorHub
import com.biyahe.companion.sensors.StepLog
import com.biyahe.companion.tracking.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One line in the trip's safety log. */
data class GuardLogEntry(val timeMs: Long, val title: String, val text: String)

/**
 * The "more guards", running while a trip records:
 *
 *   Tunnel & night   ambient light   sudden dark = tunnel, slow dark = night: dim the screen, "headlights on"
 *   Rain mode        barometer       rain likely: speed limit 10 km/h lower, "roads may be slippery"
 *   Hazard ahead     GPS + camera + compass   a reported pothole/flood within 300 m in front of you
 *   Break reminder   step counter + clock     every 2 h of driving; a stop with walking counts as a break
 *   Loud cabin       microphone      85 dB+ for 30 s: you may not hear horns or sirens
 *   Mount check      accelerometer   the phone slid or fell out of its holder
 *
 * Every warning is spoken and logged, never something to read while driving. Each guard also has a
 * stage test (live for light and mount; spoken examples for the rest).
 */
class SafetyGuards(
    private val store: SafetyStore,
    private val alerts: SafetyAlerts,
    private val recorder: TripRecorder,
    private val hub: SensorHub,
    private val hazards: HazardRepository,
    private val stepLog: StepLog,
    private val scope: CoroutineScope,
    private val tuning: () -> Tuning = { Tuning.DEFAULT }
) {
    private val _nightMode = MutableStateFlow(false)
    /** True while it is dark (tunnel / night): the app dims the screen. */
    val nightMode: StateFlow<Boolean> = _nightMode.asStateFlow()

    private val _rainActive = MutableStateFlow(false)
    /** True while rain is likely (or simulated): the speed limit is lowered. */
    val rainActive: StateFlow<Boolean> = _rainActive.asStateFlow()

    private val _log = MutableStateFlow<List<GuardLogEntry>>(emptyList())
    /** This trip's safety log, newest first. */
    val log: StateFlow<List<GuardLogEntry>> = _log.asStateFlow()

    private val _lux = MutableStateFlow<Float?>(null)
    val lux: StateFlow<Float?> = _lux.asStateFlow()
    private val _tilt = MutableStateFlow(0.0)
    val tilt: StateFlow<Double> = _tilt.asStateFlow()
    private val _lightTest = MutableStateFlow(false)
    val lightTestOn: StateFlow<Boolean> = _lightTest.asStateFlow()
    private val _mountTest = MutableStateFlow(false)
    val mountTestOn: StateFlow<Boolean> = _mountTest.asStateFlow()

    private var tripJob: Job? = null
    private var lightTestJob: Job? = null
    private var mountTestJob: Job? = null
    private var rainSimUntil = 0L

    private var light = LightGuard()
    private var loud = LoudGuard()
    private var mount = MountGuard()
    private var breaks = BreakTimer()
    private var stoppedAt: Long? = null
    private val warnedHazards = mutableSetOf<Long>()
    @Volatile private var hazardCache: List<HazardPoint> = emptyList()

    /** The speed limit to enforce right now (rain mode included). */
    fun effectiveLimit(): Int? {
        val s = store.settings.value
        return RainMode.limit(s.activeLimitKmh, _rainActive.value, s.rainModeOn)
    }

    /** Called once by AppContainer: follows the recorder and runs the guards during every trip. */
    fun start() {
        scope.launch {
            recorder.state.map { it.isRecording }.distinctUntilChanged().collect { recording ->
                if (recording) startTrip() else stopTrip()
            }
        }
    }

    private fun startTrip() {
        val s = store.settings.value
        val t = tuning()
        light = LightGuard(darkLux = t.tunnelLux, exitLux = t.tunnelLux * 3f)
        loud = LoudGuard(thresholdDb = t.loudDb, forMs = t.loudSec * 1_000L)
        mount = MountGuard(tiltDeg = t.mountTiltDeg)
        warnedHazards.clear(); stoppedAt = null
        breaks = BreakTimer(everyMs = s.breakEveryMin * 60_000L)
        _log.value = emptyList()
        if (s.tunnelGuardOn && isNightHour()) {
            notify("Night drive", "It's dark out. Headlights on, and watch for pedestrians.")
        }
        tripJob?.cancel()
        tripJob = scope.launch {
            launch { hub.ambientLux().collect { onLux(it, fromTest = false) } }
            launch {
                hub.accelerometer(SensorManager.SENSOR_DELAY_NORMAL).collect { v ->
                    val st = store.settings.value
                    if (st.mountCheckOn && recorder.mode == TripMode.DRIVING && recorder.state.value.speedKmh >= 10.0 && mount.update(v)) {
                        notify("Mount check", "Your phone moved. Please check that it's mounted, once you've stopped.")
                    }
                }
            }
            launch { hazards.allHazards.collect { list -> hazardCache = list.map { HazardPoint(it.id, it.type, it.latitude, it.longitude) } } }
            launch {
                while (isActive) {
                    tick()
                    delay(1_000)
                }
            }
        }
    }

    private fun stopTrip() {
        tripJob?.cancel()
        tripJob = null
        if (lightTestJob?.isActive != true) _nightMode.value = false
        if (System.currentTimeMillis() >= rainSimUntil) _rainActive.value = false
    }

    private fun tick() {
        val s = store.settings.value
        val st = recorder.state.value
        val now = System.currentTimeMillis()

        // Rain mode (barometer)
        val rainy = WeatherWatcher.assess(recorder.pressureSnapshot()).shouldWarn || now < rainSimUntil
        if (rainy != _rainActive.value) {
            _rainActive.value = rainy
            if (rainy && s.rainModeOn && s.speedLimitOn) announceRain()
        }

        // Hazard ahead (GPS position + course, compass when slow)
        val lat = st.lastLat; val lon = st.lastLon
        if (s.hazardAheadOn && lat != null && lon != null && st.speedKmh >= 10.0) {
            val heading = courseDeg() ?: st.headingDeg?.toDouble()
            if (heading != null) {
                HazardAhead.find(lat, lon, heading, hazardCache, warnedHazards)?.let { (h, d) ->
                    warnedHazards += h.id
                    notify("Hazard ahead", "${HazardType.fromName(h.type).label} reported ${roundTo10(d)} meters ahead.")
                }
            }
        }

        // Break reminder (step counter proves the break)
        if (st.speedKmh < 3.0) { if (stoppedAt == null) stoppedAt = now } else stoppedAt = null
        val steps = stoppedAt?.let { stepLog.stepsBetween(it, now) } ?: 0
        if (s.breakReminderOn && recorder.mode == TripMode.DRIVING) {
            when (breaks.update(now, st.speedKmh, steps)) {
                BreakEvent.REMIND -> notify("Break time", breakMessage(s.breakEveryMin))
                BreakEvent.BREAK_LOGGED -> log("Break logged", "$steps steps walked. Break timer restarted.")
                null -> Unit
            }
        }

        // Loud cabin (microphone)
        if (s.loudCabinOn && loud.update(st.noiseDb, now)) {
            notify("Loud cabin", "It's very loud in here. You may not hear horns or sirens.")
        }
    }

    private fun onLux(lux: Float, fromTest: Boolean) {
        _lux.value = lux
        if (!fromTest && !store.settings.value.tunnelGuardOn) return
        when (light.update(lux, System.currentTimeMillis())) {
            LightChange.ENTER_TUNNEL -> {
                _nightMode.value = true
                notify("Tunnel", "Tunnel. Headlights on. I've dimmed the screen.")
            }
            LightChange.ENTER_NIGHT -> {
                _nightMode.value = true
                notify("Night", "It's getting dark. Headlights on. I've dimmed the screen.")
            }
            LightChange.EXIT_DARK -> {
                _nightMode.value = false
                log("Daylight", "Back in the light. Screen brightness restored.")
            }
            null -> Unit
        }
    }

    // ---- Stage tests ------------------------------------------------------------------------------

    /** Live: watch the light sensor for 90 s. Cover the top of the phone to make a "tunnel". */
    fun startLightTest() {
        if (lightTestJob?.isActive == true) return
        light = LightGuard(darkLux = tuning().tunnelLux, exitLux = tuning().tunnelLux * 3f)
        _lightTest.value = true
        lightTestJob = scope.launch {
            val until = System.currentTimeMillis() + 90_000L
            hub.ambientLux().collect {
                onLux(it, fromTest = true)
                if (System.currentTimeMillis() > until) stopLightTest()
            }
        }
    }

    fun stopLightTest() {
        lightTestJob?.cancel(); lightTestJob = null
        _lightTest.value = false
        if (tripJob == null) _nightMode.value = false
    }

    /** Live: hold the phone still for 3 s, then tip it over 30° to set off the mount check. */
    fun startMountTest() {
        if (mountTestJob?.isActive == true) return
        val guard = MountGuard(tiltDeg = tuning().mountTiltDeg, cooldownMs = 5_000L)
        _mountTest.value = true
        mountTestJob = scope.launch {
            hub.accelerometer(SensorManager.SENSOR_DELAY_UI).collect { v ->
                if (guard.update(v)) notify("Mount check", "Your phone moved. Please check that it's mounted, once you've stopped.")
                _tilt.value = guard.tiltNow
            }
        }
    }

    fun stopMountTest() {
        mountTestJob?.cancel(); mountTestJob = null
        _mountTest.value = false
        _tilt.value = 0.0
    }

    /** Pretend the barometer says rain is coming, for 60 s. */
    fun simulateRain() {
        rainSimUntil = System.currentTimeMillis() + 60_000L
        _rainActive.value = true
        announceRain()
        scope.launch {
            delay(60_000L)
            if (tripJob == null || WeatherWatcher.assess(recorder.pressureSnapshot()).risk == com.biyahe.companion.scoring.RainRisk.NONE) {
                _rainActive.value = false
            }
        }
    }

    fun tryHazardAhead() {
        scope.launch {
            val h = runCatching { hazards.allHazards.first().firstOrNull() }.getOrNull()
            val label = h?.let { HazardType.fromName(it.type).label } ?: "Pothole"
            notify("Hazard ahead", "$label reported 200 meters ahead.")
        }
    }

    fun tryBreak() = notify("Break time", breakMessage(store.settings.value.breakEveryMin))
    fun tryLoud() = notify("Loud cabin", "It's very loud in here. You may not hear horns or sirens.")

    // ---- helpers -------------------------------------------------------------------------------------

    private fun announceRain() {
        val base = store.settings.value.activeLimitKmh
        val lowered = RainMode.limit(base, true, true)
        notify(
            "Rain mode",
            if (base != null) "Rain is likely and roads may be slippery. I've lowered your speed limit to $lowered."
            else "Rain is likely and roads may be slippery. Take it slow."
        )
    }

    private fun breakMessage(everyMin: Int) =
        "You've been driving for ${if (everyMin % 60 == 0) "${everyMin / 60} hour${if (everyMin == 60) "" else "s"}" else "$everyMin minutes"}. " +
            "Time for a break: stop somewhere safe and walk a little."

    private fun courseDeg(): Double? {
        val pts = recorder.routeSnapshot().takeLast(2)
        if (pts.size < 2) return null
        val (a, b) = pts
        if (HazardAhead.distanceM(a.lat, a.lon, b.lat, b.lon) < 5.0) return null
        return HazardAhead.bearingDeg(a.lat, a.lon, b.lat, b.lon)
    }

    private fun isNightHour(): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 18 || h < 6
    }

    private fun roundTo10(d: Double) = ((d / 10.0).roundToInt() * 10).coerceAtLeast(10)

    private fun notify(title: String, text: String) {
        log(title, text)
        alerts.notice(text)
    }

    /** A passenger's note (e.g. the vehicle sped): logged on the trip, not spoken. */
    fun logQuietly(title: String, text: String) = log(title, text)

    private fun log(title: String, text: String) {
        _log.update { (listOf(GuardLogEntry(System.currentTimeMillis(), title, text)) + it).take(30) }
    }
}
