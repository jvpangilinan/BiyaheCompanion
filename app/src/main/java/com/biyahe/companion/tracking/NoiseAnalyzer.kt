package com.biyahe.companion.tracking

import kotlin.math.log10
import kotlin.math.pow

/**
 * Collects the microphone's loudness readings and summarises the ride's noise.
 * Pure Kotlin. Readings are averaged by energy (like a real sound meter's Leq),
 * not by simple mean, so a few loud horn blasts count properly.
 */
class NoiseAnalyzer {
    private var sumPower = 0.0
    private var count = 0
    private var peak = 0.0
    private var live = Double.NaN

    @Synchronized
    fun addDb(db: Double) {
        sumPower += 10.0.pow(db / 10.0)
        count++
        if (db > peak) peak = db
        live = if (live.isNaN()) db else live + LIVE_ALPHA * (db - live)
    }

    /** Smoothed "right now" loudness (about the last few seconds), or null before any reading. */
    @Synchronized fun liveDb(): Double? = if (live.isNaN()) null else live

    @Synchronized
    fun averageDb(): Double? = if (count == 0) null else 10.0 * log10(sumPower / count)

    @Synchronized
    fun summary() = NoiseSummary(
        averageDb = if (count == 0) 0.0 else 10.0 * log10(sumPower / count),
        peakDb = peak,
        samples = count
    )

    @Synchronized
    fun reset() {
        sumPower = 0.0; count = 0; peak = 0.0; live = Double.NaN
    }

    private companion object {
        const val LIVE_ALPHA = 0.2
    }
}
