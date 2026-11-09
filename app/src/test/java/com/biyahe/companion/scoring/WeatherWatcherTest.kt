package com.biyahe.companion.scoring

import com.biyahe.companion.tracking.PressureSample
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWatcherTest {

    /** One reading every 5 minutes for [minutes], pressure falling [dropHpaPerHour] hPa per hour. */
    private fun readings(minutes: Int, dropHpaPerHour: Double): List<PressureSample> =
        (0..minutes step 5).map { m ->
            PressureSample(m * 60_000L, (1013.0 - dropHpaPerHour * m / 60.0).toFloat())
        }

    @Test fun steadyPressureIsNoRisk() =
        assertEquals(RainRisk.NONE, WeatherWatcher.assess(readings(30, 0.0)).risk)

    @Test fun fallingOnePointFiveIsPossible() =
        assertEquals(RainRisk.POSSIBLE, WeatherWatcher.assess(readings(30, 1.5)).risk)

    @Test fun fallingThreeIsLikely() =
        assertEquals(RainRisk.LIKELY, WeatherWatcher.assess(readings(30, 3.0)).risk)

    @Test fun lessThanTenMinutesOfReadingsGivesNoWarning() =
        assertEquals(RainRisk.NONE, WeatherWatcher.assess(readings(5, 6.0)).risk)

    @Test fun tooFewReadingsGivesNoWarning() =
        assertEquals(RainRisk.NONE, WeatherWatcher.assess(readings(10, 6.0).take(3)).risk)

    @Test fun risingPressureIsNoRisk() =
        assertEquals(RainRisk.NONE, WeatherWatcher.assess(readings(30, -3.0)).risk)
}
