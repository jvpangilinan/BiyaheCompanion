package com.biyahe.companion.scoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastRunTest {
    @Test fun quickAndSmoothIsFastRun() = assertTrue(FastRun.isFastRun(avgSpeedKmh = 45.0, bumpinessIndex = 0.6))

    @Test fun exactlyFortyKmhCounts() = assertTrue(FastRun.isFastRun(40.0, 0.6))

    @Test fun slowTripIsNotFastRun() = assertFalse(FastRun.isFastRun(39.9, 0.6))

    @Test fun quickButBumpyIsNotFastRun() = assertFalse(FastRun.isFastRun(60.0, 1.2))

    @Test fun justUnderBumpLimitStillCounts() = assertTrue(FastRun.isFastRun(60.0, 1.19))
}
