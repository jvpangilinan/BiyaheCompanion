package com.biyahe.companion.safety

import com.biyahe.companion.sensors.MotionAnalyzer
import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningTest {
    @Test fun presetsGoFromGentleToSensitive() {
        val g = TuningPresets.comfort(Tuning.DEFAULT, Level.GENTLE)
        val s = TuningPresets.comfort(Tuning.DEFAULT, Level.SENSITIVE)
        assertTrue(g.bumpThreshold > Tuning.DEFAULT.bumpThreshold)
        assertTrue(s.bumpThreshold < Tuning.DEFAULT.bumpThreshold)
    }

    @Test fun defaultsAreNormalEverywhere() {
        val t = Tuning.DEFAULT
        assertEquals(Level.NORMAL, TuningPresets.levelOf(t, TuningPresets::comfort))
        assertEquals(Level.NORMAL, TuningPresets.levelOf(t, TuningPresets::speed))
        assertEquals(Level.NORMAL, TuningPresets.levelOf(t, TuningPresets::crash))
        assertEquals(Level.NORMAL, TuningPresets.levelOf(t, TuningPresets::weaving))
        assertEquals(Level.NORMAL, TuningPresets.levelOf(t, TuningPresets::guards))
    }

    @Test fun fineTuningShowsAsCustom() =
        assertNull(TuningPresets.levelOf(Tuning.DEFAULT.copy(bumpThreshold = 2.7), TuningPresets::comfort))

    @Test fun calibrationSetsFloorAndThreshold() {
        val t = Calibration.apply(Tuning.DEFAULT, 0.7)
        assertEquals(0.7, t.vibrationFloor, 1e-9)
        assertEquals(2.8, t.bumpThreshold, 1e-9)
    }

    @Test fun calibratedFloorRemovesTheCarsNormalShaking() {
        fun bumpiness(floor: Double): Double {
            val m = MotionAnalyzer(vibrationFloor = floor)
            var t = 0L
            repeat(500) { i -> m.addAccelerometer(Vec3(0f, if (i % 2 == 0) 0.6f else -0.6f, 9.81f, t)); t += 20 }
            return m.summary().bumpinessIndex
        }
        val raw = bumpiness(0.0)
        val calibrated = bumpiness(raw)
        assertTrue(raw > 0.3)
        assertTrue(calibrated < 0.05)   // idle engine shake is no longer "roughness"
    }
}
