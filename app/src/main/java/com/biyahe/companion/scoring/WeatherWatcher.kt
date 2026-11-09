package com.biyahe.companion.scoring

import com.biyahe.companion.tracking.PressureSample

enum class RainRisk(val label: String) {
    NONE("None"), POSSIBLE("Possible"), LIKELY("Likely")
}

data class WeatherAssessment(
    val risk: RainRisk,
    /** Negative = pressure falling. */
    val changeHpaPerHour: Double,
    val spanMinutes: Double
) {
    val shouldWarn: Boolean get() = risk != RainRisk.NONE

    val message: String
        get() = when (risk) {
            RainRisk.NONE -> "No sign of a weather change"
            RainRisk.POSSIBLE -> "Air pressure is dropping - rain is possible"
            RainRisk.LIKELY -> "Air pressure is dropping fast - rain or a storm is likely"
        }
}

/**
 * Rain/storm warning from the barometer (Purpose #3 in the doc).
 * Falling air pressure is the classic early sign of bad weather. We compare the
 * average of the first few readings with the average of the latest few, and turn
 * that into hPa per hour:
 *
 *   falling 1 hPa/h or more  -> rain POSSIBLE
 *   falling 2 hPa/h or more  -> rain LIKELY
 *
 * Needs at least [MIN_SPAN_MINUTES] of readings. Honest limits: climbing a flyover
 * or a tall building also lowers pressure (about 0.12 hPa per metre), so this is a
 * hint, not a forecast. Pure Kotlin.
 */
object WeatherWatcher {
    const val MIN_SPAN_MINUTES = 10.0
    const val POSSIBLE_DROP_PER_HOUR = 1.0
    const val LIKELY_DROP_PER_HOUR = 2.0

    private val NONE = WeatherAssessment(RainRisk.NONE, 0.0, 0.0)

    fun assess(samples: List<PressureSample>): WeatherAssessment {
        if (samples.size < 4) return NONE
        val k = minOf(3, samples.size / 2)
        val head = samples.take(k)
        val tail = samples.takeLast(k)

        val spanMinutes = (samples.last().timeMs - samples.first().timeMs) / 60_000.0
        if (spanMinutes < MIN_SPAN_MINUTES) return NONE

        val hours = (tail.map { it.timeMs }.average() - head.map { it.timeMs }.average()) / 3_600_000.0
        if (hours <= 0.0) return NONE
        val change = (tail.map { it.hpa.toDouble() }.average() - head.map { it.hpa.toDouble() }.average()) / hours

        val risk = when {
            -change >= LIKELY_DROP_PER_HOUR -> RainRisk.LIKELY
            -change >= POSSIBLE_DROP_PER_HOUR -> RainRisk.POSSIBLE
            else -> RainRisk.NONE
        }
        return WeatherAssessment(risk, change, spanMinutes)
    }
}
