package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftTrackerTest {

    private fun started(): DriftTracker = DriftTracker().apply { onCompass(90f); start() }

    @Test fun walkingStraightStaysInLane() {
        val t = started()
        repeat(8) { t.step() }
        val s = t.snapshot()
        assertEquals(0.0, s.x, 0.001)
        assertEquals(DriftStatus.OK, s.status)
        assertEquals(0, s.driftWarnings)
    }

    @Test fun veeringRightDriftsRight() {
        val t = started()
        t.setHeadingForTest(90.0 + 20.0) // 20 degrees right of straight ahead
        repeat(6) { t.step() }
        assertEquals(DriftStatus.DRIFT_RIGHT, t.status())
        assertEquals(1, t.snapshot().driftWarnings)
    }

    @Test fun veeringLeftDriftsLeft() {
        val t = started()
        t.setHeadingForTest(90.0 - 20.0)
        repeat(6) { t.step() }
        assertEquals(DriftStatus.DRIFT_LEFT, t.status())
    }

    @Test fun firstCompassReadingIsNotAFakeTurn() {
        val t = DriftTracker()
        t.start()            // no reading yet
        t.onCompass(200f)    // first reading arrives late
        assertEquals(0.0, t.snapshot().deviationDeg, 0.001)
    }

    @Test fun gyroscopeTurnLeftLowersHeading() {
        val t = DriftTracker()
        t.onAccel(Vec3(0f, 0f, 9.81f, 0))          // phone flat: gravity on z
        t.onCompass(0f); t.start()
        t.onGyro(Vec3(0f, 0f, 0f, 1_000))
        repeat(10) { i -> t.onGyro(Vec3(0f, 0f, 0.5f, 1_100L + i * 100)) } // turning left (counter-clockwise)
        assertTrue(t.snapshot().deviationDeg < -20)
    }

    @Test fun stopsAtTheFinishLine() {
        val t = started()
        repeat(30) { t.step() }
        assertTrue(t.snapshot().finished)
        assertEquals(9, t.snapshot().steps) // 6 m at 0.7 m a step
    }
}
