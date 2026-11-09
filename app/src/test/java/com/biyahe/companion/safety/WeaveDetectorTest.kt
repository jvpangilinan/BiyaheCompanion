package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drives a simulated car: the phone lies flat (gravity on z), the gyroscope reports the turn
 * rate the heading profile needs, and GPS fixes follow the car's real course once a second.
 */
class WeaveDetectorTest {

    private class Sim(val d: WeaveDetector, val speedKmh: Double = 60.0) {
        var t = 1_000L
        var heading = 0.0          // degrees, the car's true course
        var lat = 14.55
        var lon = 121.0
        var warned = false

        init { d.onAccel(Vec3(0f, 0f, 9.81f, t)) }

        /** Runs [seconds] of driving; [headingAt] gives the course at each moment (s since start). */
        fun drive(seconds: Double, phoneTurnExtra: (Double) -> Double = { 0.0 }, headingAt: (Double) -> Double) {
            val start = t
            val base = heading
            var lastFix = t
            val steps = (seconds * 20).toInt()            // 20 Hz gyroscope
            for (i in 1..steps) {
                val s = i / 20.0
                val newHeading = base + headingAt(s)
                val rate = (newHeading - heading) * 20.0 + (phoneTurnExtra(s) - phoneTurnExtra(s - 0.05)) * 20.0
                heading = newHeading
                t = start + (s * 1000).toLong()
                d.onAccel(Vec3(0f, 0f, 9.81f, t))
                d.onGyro(Vec3(0f, 0f, (-Math.toRadians(rate)).toFloat(), t)) // + heading rate = clockwise
                val mps = speedKmh / 3.6 / 20.0
                lat += mps * cos(Math.toRadians(heading)) / 111_320.0
                lon += mps * sin(Math.toRadians(heading)) / (111_320.0 * cos(Math.toRadians(lat)))
                if (t - lastFix >= 1_000L) {
                    lastFix = t
                    warned = d.onFix(lat, lon, speedKmh, t) || warned
                }
            }
        }

        fun straight(seconds: Double) = drive(seconds) { 0.0 }

        /** One lane change: heading swings ~6° and back over 3 s. */
        fun laneChange() = drive(3.0) { s -> 6.0 * sin(PI * s / 3.0) }
    }

    @Test fun oneLaneChangeIsCountedButNotWarned() {
        val sim = Sim(WeaveDetector())
        sim.straight(10.0); sim.laneChange(); sim.straight(10.0)
        assertEquals(1, sim.d.weaves)
        assertFalse(sim.warned)
    }

    @Test fun aLongCurveIsNotWeaving() {
        val sim = Sim(WeaveDetector())
        sim.straight(10.0)
        sim.drive(30.0) { s -> 3.0 * s }   // steady 90° bend
        sim.straight(20.0)
        assertEquals(0, sim.d.weaves)
    }

    @Test fun frequentWeavingWarns() {
        val sim = Sim(WeaveDetector())
        sim.straight(10.0)
        repeat(6) { sim.laneChange(); sim.straight(6.0) }
        assertTrue(sim.d.weaves >= 5)
        assertTrue(sim.warned)
    }

    @Test fun slowTrafficIsIgnored() {
        val sim = Sim(WeaveDetector(), speedKmh = 20.0)
        sim.straight(10.0)
        repeat(6) { sim.laneChange(); sim.straight(6.0) }
        assertEquals(0, sim.d.weaves)
        assertFalse(sim.warned)
    }

    @Test fun handlingThePhoneIsIgnored() {
        val sim = Sim(WeaveDetector())
        sim.straight(10.0)
        // The car goes straight but the phone is twisted 40° in someone's hand, back and forth.
        repeat(4) {
            sim.drive(6.0, phoneTurnExtra = { s -> 40.0 * sin(PI * s / 3.0) }) { 0.0 }
        }
        assertEquals(0, sim.d.weaves)
    }
}
