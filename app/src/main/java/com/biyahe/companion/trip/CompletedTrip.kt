package com.biyahe.companion.trip

import com.biyahe.companion.scoring.ComfortResult
import com.biyahe.companion.data.model.RoutePoint
import com.biyahe.companion.scoring.FastRun
import com.biyahe.companion.scoring.WeatherAssessment
import com.biyahe.companion.tracking.RecordedTrip

/** A scored trip, ready to be stored. Plain values only, so any storage (Room, Firebase) can map it. */
data class CompletedTrip(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMs: Long,
    val distanceKm: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val comfortScore: Int,
    val comfortLabel: String,
    val bumpinessIndex: Double,
    val bumpLevel: String,
    val bumpCount: Int,
    val sharpTurnCount: Int,
    val avgNoiseDb: Double,
    val noiseLevel: String,
    val rainRisk: String,
    val stepsBefore: Int,
    val stepsAfter: Int,
    /** Whole route as one encoded string (decode with Polyline.decode). */
    val routePolyline: String,
    /** The route with roughness and bumps per point (empty for routes saved before the roughness map). */
    val roughRoute: List<RoutePoint> = emptyList(),
    /** Speeding episodes that got a warning, and total seconds over the limit. */
    val speedWarnings: Int = 0,
    val overspeedSec: Long = 0,
    /** Lane changes / swerves at driving speed (gyroscope). */
    val weavingCount: Int = 0,
    /** "DRIVING" or "RIDING". */
    val tripMode: String = "DRIVING",
    /** How the rider said the trip felt, 1-5 stars (0 = not rated). Checks the Comfort Score against real feelings. */
    val riderRating: Int = 0
) {
    /** Quick and smooth (see FastRun). Worked out on the fly, never stored. */
    val isFastRun: Boolean get() = FastRun.isFastRun(avgSpeedKmh, bumpinessIndex)
}

/** What the screens show right after End Trip. */
data class TripResult(
    val tripId: Long,
    val trip: CompletedTrip,
    val comfort: ComfortResult,
    val weather: WeatherAssessment
)

fun buildCompletedTrip(
    recorded: RecordedTrip,
    comfort: ComfortResult,
    weather: WeatherAssessment,
    stepsBefore: Int
) = CompletedTrip(
    startedAtMs = recorded.startedAtMs,
    endedAtMs = recorded.endedAtMs,
    durationMs = recorded.durationMs,
    distanceKm = recorded.distanceKm,
    avgSpeedKmh = recorded.avgSpeedKmh,
    maxSpeedKmh = recorded.maxSpeedKmh,
    comfortScore = comfort.score,
    comfortLabel = comfort.label.title,
    bumpinessIndex = recorded.motion.bumpinessIndex,
    bumpLevel = comfort.bumpLevel.label,
    bumpCount = recorded.motion.bumpCount,
    sharpTurnCount = recorded.motion.sharpTurnCount,
    avgNoiseDb = recorded.noise.averageDb,
    noiseLevel = comfort.noiseLevel.label,
    rainRisk = weather.risk.label,
    stepsBefore = stepsBefore,
    stepsAfter = 0,
    routePolyline = recorded.routePolyline(),
    roughRoute = recorded.roughRoute,
    speedWarnings = recorded.speedWarnings,
    overspeedSec = recorded.overspeedSec,
    weavingCount = recorded.weavingCount,
    tripMode = recorded.mode.name
)
