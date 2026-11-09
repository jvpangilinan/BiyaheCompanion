package com.biyahe.companion.scoring

import com.biyahe.companion.sensors.BumpLevel
import com.biyahe.companion.sensors.MotionSummary
import com.biyahe.companion.tracking.NoiseLevel
import com.biyahe.companion.tracking.NoiseSummary
import kotlin.math.roundToInt

enum class ComfortLabel(val title: String) {
    SMOOTH("Smooth"), FAIR("Fair"), ROUGH("Rough");

    companion object {
        fun from(score: Int): ComfortLabel = when {
            score >= SMOOTH_MIN -> SMOOTH
            score >= FAIR_MIN -> FAIR
            else -> ROUGH
        }
        const val SMOOTH_MIN = 80
        const val FAIR_MIN = 60
    }
}

data class ComfortResult(
    /** 0..100, higher = more comfortable. */
    val score: Int,
    val label: ComfortLabel,
    val bumpLevel: BumpLevel,
    val noiseLevel: NoiseLevel,
    /** false when the microphone gave no data (permission off); noise is then left out of the score. */
    val noiseMeasured: Boolean,
    val bumpPenalty: Int,
    val eventPenalty: Int,
    val noisePenalty: Int
)

/** The four penalties (points taken off 100), unrounded. */
data class ScoreBreakdown(
    val vibration: Double,
    val bumps: Double,
    val turns: Double,
    val noise: Double,
    val noiseMeasured: Boolean
) {
    val score: Int get() = (100.0 - vibration - bumps - turns - noise).roundToInt().coerceIn(0, 100)

    /**
     * Whole-number penalties [vibration, bumps, turns, noise] that add up to exactly 100 - score
     * (largest-remainder rounding), so the numbers on screen always add up.
     */
    fun wholePoints(): List<Int> {
        val raw = listOf(vibration, bumps, turns, noise)
        val target = 100 - score
        val floors = raw.map { it.toInt() }.toMutableList()
        var left = target - floors.sum()
        val order = raw.indices.sortedByDescending { raw[it] - floors[it] }
        var i = 0
        while (left > 0 && i < order.size) { floors[order[i]]++; left--; i++ }
        return floors
    }
}

/**
 * Comfort Score = 100 minus penalties, as the architecture doc describes:
 * "computed from bumpiness (accelerometer + gyroscope) and noise (microphone)".
 *
 *   vibration  up to 45  <- RMS jolt of the ride (0.4 m/s² = silky, 3.0 = very rough)
 *   bumps      up to 10  <- big jolts per minute (2 per minute = max)
 *   sharp turns up to 10 <- swerving per minute (1 per minute = max)
 *   noise      up to 25  <- average loudness (55 dB = calm, 95 dB = painful)
 *
 * Pure Kotlin. All the numbers are in the constants below: tune them after a few real rides.
 */
object ComfortScoreCalculator {

    const val VIBRATION_MIN = 0.4
    const val VIBRATION_MAX = 3.0
    const val VIBRATION_WEIGHT = 45.0

    const val BUMPS_PER_MIN_MAX = 2.0
    const val BUMP_WEIGHT = 10.0

    const val TURNS_PER_MIN_MAX = 1.0
    const val TURN_WEIGHT = 10.0

    const val NOISE_MIN_DB = 55.0
    const val NOISE_MAX_DB = 95.0
    const val NOISE_WEIGHT = 25.0

    fun calculate(motion: MotionSummary, noise: NoiseSummary, durationMs: Long): ComfortResult {
        val b = breakdown(
            bumpinessIndex = motion.bumpinessIndex,
            bumpCount = motion.bumpCount,
            sharpTurnCount = motion.sharpTurnCount,
            averageDb = if (noise.samples > 0) noise.averageDb else null,
            durationMs = durationMs
        )
        return ComfortResult(
            score = b.score,
            label = ComfortLabel.from(b.score),
            bumpLevel = BumpLevel.from(motion.bumpinessIndex),
            noiseLevel = NoiseLevel.from(noise.averageDb),
            noiseMeasured = b.noiseMeasured,
            bumpPenalty = b.vibration.roundToInt(),
            eventPenalty = (b.bumps + b.turns).roundToInt(),
            noisePenalty = b.noise.roundToInt()
        )
    }

    /**
     * The four penalties on their own. Works from values every saved trip already has, so the
     * Trip Summary can show "why this score" for old trips too. [averageDb] = null means the
     * microphone gave no data, and noise is left out.
     */
    fun breakdown(
        bumpinessIndex: Double,
        bumpCount: Int,
        sharpTurnCount: Int,
        averageDb: Double?,
        durationMs: Long
    ): ScoreBreakdown {
        val minutes = (durationMs / 60_000.0).coerceAtLeast(1.0)
        return ScoreBreakdown(
            vibration = scale(bumpinessIndex, VIBRATION_MIN, VIBRATION_MAX) * VIBRATION_WEIGHT,
            bumps = scale(bumpCount / minutes, 0.0, BUMPS_PER_MIN_MAX) * BUMP_WEIGHT,
            turns = scale(sharpTurnCount / minutes, 0.0, TURNS_PER_MIN_MAX) * TURN_WEIGHT,
            noise = if (averageDb != null) scale(averageDb, NOISE_MIN_DB, NOISE_MAX_DB) * NOISE_WEIGHT else 0.0,
            noiseMeasured = averageDb != null
        )
    }

    /** 0.0 at or below [low], 1.0 at or above [high], straight line between. */
    private fun scale(value: Double, low: Double, high: Double): Double =
        ((value - low) / (high - low)).coerceIn(0.0, 1.0)
}
