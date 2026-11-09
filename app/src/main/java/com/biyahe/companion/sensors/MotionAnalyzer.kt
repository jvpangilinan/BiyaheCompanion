package com.biyahe.companion.sensors

import kotlin.math.sqrt

enum class BumpLevel(val label: String) {
    LOW("Low"), MEDIUM("Medium"), HIGH("High");

    companion object {
        fun from(bumpinessIndex: Double): BumpLevel = when {
            bumpinessIndex < 1.2 -> LOW
            bumpinessIndex < 2.2 -> MEDIUM
            else -> HIGH
        }
    }
}

data class MotionSummary(
    /** RMS of vibration (m/s²) after removing gravity. Higher = rougher ride. */
    val bumpinessIndex: Double,
    val bumpCount: Int,
    val sharpTurnCount: Int,
    val peakJolt: Double,
    val accelSamples: Int,
    val gyroSamples: Int
)

/** Motion on one stretch of road between two GPS points. [roughness] is null if no samples came in. */
data class SegmentMotion(val roughness: Double?, val bumps: Int)

/**
 * Turns raw accelerometer + gyroscope samples into the numbers the Comfort
 * Score needs (Section 6). Pure Kotlin - no Android classes - so it is easy to test.
 *
 *  - Gravity is estimated with a low-pass filter and subtracted, leaving the
 *    "jolt" (vibration) in any phone orientation.
 *  - A bump = jolt above [bumpThreshold], counted at most once per [bumpGapMs].
 *  - A sharp turn = rotation faster than [turnThreshold] rad/s, once per [turnGapMs].
 */
class MotionAnalyzer(
    private val gravityAlpha: Float = 0.8f,
    private val bumpThreshold: Double = 2.5,
    private val bumpGapMs: Long = 500,
    private val turnThreshold: Double = 1.0,
    private val turnGapMs: Long = 1500,
    /** This car's / mount's normal shaking (RMS m/s², from Calibrate): removed before measuring roughness. */
    private val vibrationFloor: Double = 0.0
) {
    /** RMS with the calibrated floor taken out (energies subtract, so it's done on the squares). */
    private fun rmsAboveFloor(meanSquare: Double) = sqrt((meanSquare - vibrationFloor * vibrationFloor).coerceAtLeast(0.0))

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var gravityReady = false

    private var sumSquares = 0.0
    private var accelCount = 0
    private var peak = 0.0
    private var bumps = 0
    private var lastBumpAt = Long.MIN_VALUE / 2

    private var turns = 0
    private var gyroCount = 0
    private var lastTurnAt = Long.MIN_VALUE / 2

    private var emaSquare = 0.0

    // Window since the last GPS point (for the roughness map).
    private var segSumSquares = 0.0
    private var segCount = 0
    private var segBumps = 0

    @Synchronized
    fun addAccelerometer(sample: Vec3) {
        if (!gravityReady) {
            gx = sample.x; gy = sample.y; gz = sample.z
            gravityReady = true
            return
        }
        gx = gravityAlpha * gx + (1 - gravityAlpha) * sample.x
        gy = gravityAlpha * gy + (1 - gravityAlpha) * sample.y
        gz = gravityAlpha * gz + (1 - gravityAlpha) * sample.z

        val lx = (sample.x - gx).toDouble()
        val ly = (sample.y - gy).toDouble()
        val lz = (sample.z - gz).toDouble()
        val jolt = sqrt(lx * lx + ly * ly + lz * lz)

        sumSquares += jolt * jolt
        accelCount++
        if (jolt > peak) peak = jolt
        emaSquare += LIVE_ALPHA * (jolt * jolt - emaSquare)
        segSumSquares += jolt * jolt
        segCount++

        if (jolt >= bumpThreshold && sample.timestampMs - lastBumpAt >= bumpGapMs) {
            bumps++
            segBumps++
            lastBumpAt = sample.timestampMs
        }
    }

    @Synchronized
    fun addGyroscope(sample: Vec3) {
        gyroCount++
        if (sample.magnitude >= turnThreshold && sample.timestampMs - lastTurnAt >= turnGapMs) {
            turns++
            lastTurnAt = sample.timestampMs
        }
    }

    /** Smoothed "right now" bumpiness for the live trip screen (about the last 2 seconds). */
    @Synchronized
    fun liveBumpiness(): Double = rmsAboveFloor(emaSquare)

    /**
     * Vibration (RMS jolt) and big bumps since the previous call, then starts a new window.
     * Called once per accepted GPS point, so each stretch of road gets its own roughness.
     */
    @Synchronized
    fun takeSegment(): SegmentMotion {
        val seg = SegmentMotion(
            roughness = if (segCount == 0) null else rmsAboveFloor(segSumSquares / segCount),
            bumps = segBumps
        )
        segSumSquares = 0.0; segCount = 0; segBumps = 0
        return seg
    }

    @Synchronized
    fun summary(): MotionSummary = MotionSummary(
        bumpinessIndex = if (accelCount == 0) 0.0 else rmsAboveFloor(sumSquares / accelCount),
        bumpCount = bumps,
        sharpTurnCount = turns,
        peakJolt = peak,
        accelSamples = accelCount,
        gyroSamples = gyroCount
    )

    @Synchronized
    fun reset() {
        gravityReady = false
        sumSquares = 0.0; accelCount = 0; peak = 0.0
        bumps = 0; lastBumpAt = Long.MIN_VALUE / 2
        turns = 0; gyroCount = 0; lastTurnAt = Long.MIN_VALUE / 2
        emaSquare = 0.0
        segSumSquares = 0.0; segCount = 0; segBumps = 0
    }

    private companion object {
        const val LIVE_ALPHA = 0.01
    }
}
