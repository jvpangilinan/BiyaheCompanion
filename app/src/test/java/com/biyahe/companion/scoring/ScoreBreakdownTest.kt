package com.biyahe.companion.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScoreBreakdownTest {

    private val tenMinutes = 10 * 60_000L

    @Test fun wholePointsAlwaysAddUpToTheScore() {
        for (b in listOf(0.5, 0.9, 1.3, 2.1, 2.8)) for (bumps in listOf(0, 3, 17)) for (db in listOf(60.0, 71.0, 88.0)) {
            val r = ComfortScoreCalculator.breakdown(b, bumps, 4, db, tenMinutes)
            assertEquals(100 - r.score, r.wholePoints().sum())
        }
    }

    @Test fun breakdownMatchesCalculate() {
        val r = ComfortScoreCalculator.breakdown(0.7, 3, 1, 65.0, tenMinutes)
        assertEquals(86, r.score) // the paper's worked example
    }

    @Test fun noMicrophoneMeansNoNoisePenalty() {
        val r = ComfortScoreCalculator.breakdown(1.0, 10, 5, null, tenMinutes)
        assertFalse(r.noiseMeasured)
        assertEquals(0, r.wholePoints()[3])
    }
}
