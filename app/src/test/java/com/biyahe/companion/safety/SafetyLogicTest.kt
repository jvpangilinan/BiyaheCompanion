package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedWatcherTest {

    /** Feeds one speed per second; returns the seconds (0-based) at which a warning fired. */
    private fun run(w: SpeedWatcher, speeds: List<Double>, limit: Int? = 60): List<Int> =
        speeds.mapIndexedNotNull { i, s -> if (w.update(s, limit, 1_000L + i * 1_000L)) i else null }

    @Test fun oneSpikeDoesNotWarn() {
        assertEquals(emptyList<Int>(), run(SpeedWatcher(), listOf(50.0, 75.0, 50.0, 50.0)))
    }

    @Test fun warnsAfterThreeSecondsOver() {
        assertEquals(listOf(3), run(SpeedWatcher(), listOf(70.0, 70.0, 70.0, 70.0, 70.0)))
    }

    @Test fun repeatsEveryTwentySecondsWhileStillFast() {
        val w = SpeedWatcher()
        assertEquals(listOf(3, 23), run(w, List(30) { 70.0 }))
        assertEquals(1, w.warnings) // one speeding episode
        assertEquals(29_000L, w.overspeedMs)
    }

    @Test fun hoveringAtTheLimitDoesNotStartNewEpisodes() {
        val w = SpeedWatcher()
        run(w, listOf(65.0, 65.0, 65.0, 65.0, 59.0, 65.0, 59.0, 65.0))
        assertEquals(1, w.warnings)
    }

    @Test fun offMeansNoWarnings() {
        assertEquals(emptyList<Int>(), run(SpeedWatcher(), List(10) { 120.0 }, limit = null))
    }
}

class CrashDetectorTest {

    @Test fun impactThenStopIsACrash() {
        val d = CrashDetector()
        assertFalse(d.addFix(80.0, 0))
        assertFalse(d.addFix(80.0, 1_000))
        d.addAccel(40.0, 1_500) // about 4 g
        assertTrue(d.addFix(3.0, 3_000))
    }

    @Test fun suddenStopFromHighwaySpeedIsACrash() {
        val d = CrashDetector()
        d.addFix(80.0, 0)
        assertTrue(d.addFix(0.0, 1_000)) // 80 to 0 in one second, no normal brake does that
    }

    @Test fun normalBrakingIsNotACrash() {
        val d = CrashDetector()
        var t = 0L
        var crash = false
        for (v in listOf(60.0, 50.0, 40.0, 30.0, 20.0, 10.0, 0.0)) { crash = crash || d.addFix(v, t); t += 1_000 }
        assertFalse(crash)
    }

    @Test fun potholeJoltWhileStillMovingIsNotACrash() {
        val d = CrashDetector()
        d.addFix(40.0, 0)
        d.addAccel(35.0, 500)
        assertFalse(d.addFix(38.0, 1_000))
        assertFalse(d.addFix(41.0, 2_000))
    }

    @Test fun droppedPhoneWhileParkedIsNotACrash() {
        val d = CrashDetector()
        d.addFix(0.0, 0)
        d.addAccel(60.0, 500)
        assertFalse(d.addFix(0.0, 1_000))
    }

    @Test fun gpsGapIsNotASuddenStop() {
        val d = CrashDetector()
        d.addFix(80.0, 0)
        assertFalse(d.addFix(0.0, 30_000)) // tunnel: next fix much later
    }
}

class DemoCrashDetectorTest {

    @Test fun walkThenSharpStopThenStillTriggers() {
        val d = DemoCrashDetector()
        var t = 0L
        var hit = false
        fun feed(x: Float, y: Float, z: Float) { hit = hit || d.add(Vec3(x, y, z, t)); t += 20 }
        repeat(50) { feed(0f, 0f, 9.81f) }                                   // still
        repeat(100) { i -> feed(0f, if (i % 25 < 12) 3f else -3f, 9.81f) }    // walking sway
        feed(0f, 25f, 9.81f)                                                  // sharp stop
        repeat(100) { feed(0f, 0f, 9.81f) }                                   // hold still
        assertTrue(hit)
    }

    @Test fun shakingWithoutWalkingFirstDoesNotTrigger() {
        val d = DemoCrashDetector()
        var t = 0L
        var hit = false
        repeat(100) { hit = hit || d.add(Vec3(0f, 0f, 9.81f, t)); t += 20 }
        hit = hit || d.add(Vec3(0f, 25f, 9.81f, t)); t += 20
        repeat(100) { hit = hit || d.add(Vec3(0f, 0f, 9.81f, t)); t += 20 }
        assertFalse(hit)
    }
}

class SleepMathTest {
    @Test fun acrossMidnight() = assertEquals(7.0, SleepMath.hoursBetween(23 * 60, 6 * 60), 0.001)
    @Test fun halfHours() = assertEquals(5.5, SleepMath.hoursBetween(0 * 60 + 30, 6 * 60), 0.001)
    @Test fun formatsTwelveHourClock() {
        assertEquals("11:00 PM", SleepMath.formatTime(23 * 60))
        assertEquals("12:15 AM", SleepMath.formatTime(15))
    }
}
