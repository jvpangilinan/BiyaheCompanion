package com.biyahe.companion.tracking

import com.biyahe.companion.sensors.MotionSummary
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.data.model.RoutePoint

/** One GPS fix. [speedMps] is -1 when the phone did not report a speed. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val speedMps: Float,
    val accuracyM: Float
)

data class LatLon(val lat: Double, val lon: Double)

data class PressureSample(val timeMs: Long, val hpa: Float)

enum class GpsStatus(val label: String) {
    OFF("GPS is off"), SEARCHING("Searching..."), WEAK("Weak signal"), GOOD("Good signal")
}

enum class NoiseLevel(val label: String) {
    LOW("Low"), MEDIUM("Medium"), HIGH("High");

    companion object {
        fun from(averageDb: Double): NoiseLevel = when {
            averageDb < 65.0 -> LOW
            averageDb < 78.0 -> MEDIUM
            else -> HIGH
        }
    }
}

data class NoiseSummary(val averageDb: Double, val peakDb: Double, val samples: Int) {
    val level: NoiseLevel get() = NoiseLevel.from(averageDb)
}

/** What the recorder shows live while a trip is running (Section 6/7 screens read this). */
data class RecorderState(
    val isRecording: Boolean = false,
    val startedAtMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val distanceM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
    val gpsAccuracyM: Float? = null,
    val pointCount: Int = 0,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val bumpiness: Double = 0.0,
    val bumpCount: Int = 0,
    val sharpTurnCount: Int = 0,
    val noiseDb: Double? = null,
    val avgNoiseDb: Double? = null,
    val headingDeg: Float? = null,
    val pressureHpa: Float? = null,
    /** Speed limit in force (null = warning off), whether we are above it, and warnings so far. */
    val speedLimitKmh: Int? = null,
    val overLimit: Boolean = false,
    val speedWarnings: Int = 0,
    /** Lane changes / swerves counted so far (gyroscope, at driving speed). */
    val weavingCount: Int = 0,
    val mode: TripMode = TripMode.DRIVING
)

/** Things the recorder noticed that need an immediate reaction. */
sealed interface TripEvent {
    data class Speeding(val limitKmh: Int, val speedKmh: Double) : TripEvent
    object CrashSuspected : TripEvent
    /** Many lane changes in a short time: possibly tired. */
    object Weaving : TripEvent
}

/** Everything the recorder captured, handed to Section 6 when the trip ends. */
data class RecordedTrip(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceM: Double,
    val maxSpeedKmh: Double,
    val motion: MotionSummary,
    val noise: NoiseSummary,
    val route: List<TrackPoint>,
    /** Same points as [route], each with the roughness and bumps felt on the way to it. */
    val roughRoute: List<RoutePoint> = emptyList(),
    val pressure: List<PressureSample>,
    val speedWarnings: Int = 0,
    val overspeedSec: Long = 0,
    val weavingCount: Int = 0,
    val mode: TripMode = TripMode.DRIVING
) {
    val durationMs: Long get() = (endedAtMs - startedAtMs).coerceAtLeast(0L)
    val distanceKm: Double get() = distanceM / 1000.0
    val avgSpeedKmh: Double
        get() = if (durationMs <= 0L) 0.0 else distanceM / (durationMs / 1000.0) * 3.6

    /** Compact text form of the route, ready to store in one database column. */
    fun routePolyline(): String = Polyline.encode(route.map { LatLon(it.lat, it.lon) })
}
