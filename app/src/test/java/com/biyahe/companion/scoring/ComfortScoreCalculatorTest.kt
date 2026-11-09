package com.biyahe.companion.scoring

import com.biyahe.companion.sensors.MotionSummary
import com.biyahe.companion.tracking.NoiseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfortScoreCalculatorTest {

    private fun motion(bumpiness: Double, bumps: Int, turns: Int) =
        MotionSummary(bumpiness, bumps, turns, peakJolt = 0.0, accelSamples = 100, gyroSamples = 100)

    private fun noise(avgDb: Double, samples: Int = 50) = NoiseSummary(avgDb, avgDb + 10, samples)

    private val tenMinutes = 10 * 60_000L

    /** The worked example from the paper (Table 4): 0.7 m/s2, 0.3 bumps/min, 0.1 turns/min, 65 dB -> 86. */
    @Test fun paperWorkedExampleScoresEightySix() {
        val r = ComfortScoreCalculator.calculate(motion(0.7, bumps = 3, turns = 1), noise(65.0), tenMinutes)
        assertEquals(86, r.score)
        assertEquals(ComfortLabel.SMOOTH, r.label)
    }

    @Test fun everythingAtTheLimitGivesTen() {
        // 45 + 10 + 10 + 25 penalties = 90 points lost.
        val r = ComfortScoreCalculator.calculate(motion(3.5, bumps = 30, turns = 20), noise(95.0), tenMinutes)
        assertEquals(10, r.score)
        assertEquals(ComfortLabel.ROUGH, r.label)
    }

    @Test fun silkyQuietRideIsOneHundred() {
        val r = ComfortScoreCalculator.calculate(motion(0.2, 0, 0), noise(50.0), tenMinutes)
        assertEquals(100, r.score)
    }

    @Test fun noMicrophoneLeavesNoiseOutInsteadOfGuessing() {
        val r = ComfortScoreCalculator.calculate(motion(1.0, bumps = 10, turns = 5), noise(90.0, samples = 0), tenMinutes)
        assertFalse(r.noiseMeasured)
        assertEquals(0, r.noisePenalty)
        assertEquals(80, r.score)
    }

    @Test fun scoreAlwaysStaysBetweenZeroAndOneHundred() {
        val r = ComfortScoreCalculator.calculate(motion(99.0, 999, 999), noise(200.0), 1_000L)
        assertTrue(r.score in 0..100)
    }

    @Test fun labelBoundariesMatchTheDefinitionOfTerms() {
        assertEquals(ComfortLabel.SMOOTH, ComfortLabel.from(80))
        assertEquals(ComfortLabel.FAIR, ComfortLabel.from(79))
        assertEquals(ComfortLabel.FAIR, ComfortLabel.from(60))
        assertEquals(ComfortLabel.ROUGH, ComfortLabel.from(59))
    }
}
