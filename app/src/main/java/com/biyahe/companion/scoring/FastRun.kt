package com.biyahe.companion.scoring

import com.biyahe.companion.sensors.BumpLevel

/**
 * Fast Run: a trip that was quick AND smooth (v2 doc, Table 3).
 *
 * It is a tag, not a fourth score, and it is worked out from values already saved with every
 * trip (average speed and Bumpiness Index), so the database does not change and old trips
 * are tagged automatically.
 *
 * Pure Kotlin. Both limits are starting values: tune them after real rides.
 */
object FastRun {
    /** Average speed at or above this counts as "quick". */
    const val MIN_AVG_SPEED_KMH = 40.0

    /** "Low" bumpiness is anything under 1.2, taken from BumpLevel so the two can never disagree. */
    fun isFastRun(avgSpeedKmh: Double, bumpinessIndex: Double): Boolean =
        avgSpeedKmh >= MIN_AVG_SPEED_KMH && BumpLevel.from(bumpinessIndex) == BumpLevel.LOW
}
