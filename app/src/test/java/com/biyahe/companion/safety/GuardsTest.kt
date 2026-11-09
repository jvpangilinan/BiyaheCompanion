package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightGuardTest {
    private fun run(g: LightGuard, vararg luxPerHalfSecond: Float): List<LightChange> {
        val out = mutableListOf<LightChange>()
        luxPerHalfSecond.forEachIndexed { i, lux -> g.update(lux, i * 500L)?.let { out += it } }
        return out
    }

    @Test fun suddenDarknessIsATunnel() {
        assertEquals(listOf(LightChange.ENTER_TUNNEL), run(LightGuard(), 400f, 400f, 400f, 3f, 3f, 3f, 3f))
    }

    @Test fun slowDuskIsNight() {
        val g = LightGuard()
        val lux = (0 until 40).map { (60f - it * 1.5f).coerceAtLeast(2f) }.toFloatArray()
        assertEquals(listOf(LightChange.ENTER_NIGHT), run(g, *lux))
    }

    @Test fun leavingTheTunnelNeedsTwoSecondsOfLight() {
        val g = LightGuard()
        assertEquals(
            listOf(LightChange.ENTER_TUNNEL, LightChange.EXIT_DARK),
            run(g, 400f, 400f, 3f, 3f, 3f, 3f, 200f, 3f, 200f, 200f, 200f, 200f, 200f)
        )
    }

    @Test fun aPassingShadowIsIgnored() {
        assertTrue(run(LightGuard(), 400f, 3f, 400f, 400f, 3f, 400f).isEmpty())
    }
}

class RainModeTest {
    @Test fun lowersTheLimitWhenRainIsLikely() = assertEquals(50, RainMode.limit(60, rainLikely = true, rainModeOn = true))
    @Test fun leavesItAloneWhenDry() = assertEquals(60, RainMode.limit(60, rainLikely = false, rainModeOn = true))
    @Test fun neverBelowTwenty() = assertEquals(20, RainMode.limit(25, rainLikely = true, rainModeOn = true))
    @Test fun offMeansOff() = assertEquals(60, RainMode.limit(60, rainLikely = true, rainModeOn = false))
}

class HazardAheadTest {
    private val pothole = HazardPoint(1, "POTHOLE", 14.5020, 121.0000) // ~220 m north of the start
    private val start = 14.5000 to 121.0000

    @Test fun findsAHazardStraightAhead() {
        val hit = HazardAhead.find(start.first, start.second, 0.0, listOf(pothole), emptySet())
        assertNotNull(hit)
        assertEquals(222.0, hit!!.second, 5.0)
    }

    @Test fun ignoresAHazardBehindYou() =
        assertNull(HazardAhead.find(start.first, start.second, 180.0, listOf(pothole), emptySet()))

    @Test fun ignoresAHazardTooFarAway() =
        assertNull(HazardAhead.find(14.49, 121.0, 0.0, listOf(pothole), emptySet()))

    @Test fun announcesEachHazardOnce() =
        assertNull(HazardAhead.find(start.first, start.second, 0.0, listOf(pothole), setOf(1L)))
}

class BreakTimerTest {
    private val min = 60_000L

    @Test fun remindsAfterTwoHoursThenEveryFifteenMinutes() {
        val b = BreakTimer()
        val events = (0..150).mapNotNull { m -> b.update(m * min, 60.0, 0)?.let { m to it } }
        assertEquals(listOf(120 to BreakEvent.REMIND, 135 to BreakEvent.REMIND, 150 to BreakEvent.REMIND), events)
    }

    @Test fun aWalkingStopCountsAsABreak() {
        val b = BreakTimer()
        b.update(0, 60.0, 0)
        b.update(100 * min, 0.0, 0)                      // stop
        assertEquals(BreakEvent.BREAK_LOGGED, b.update(103 * min, 0.0, 120))
        assertNull(b.update(130 * min, 60.0, 0))          // clock restarted: no reminder yet
    }

    @Test fun sittingInTrafficIsNotABreak() {
        val b = BreakTimer()
        b.update(0, 60.0, 0)
        assertNull(b.update(100 * min, 0.0, 0))
        assertNull(b.update(105 * min, 0.0, 5))           // stopped, but no walking
        assertEquals(BreakEvent.REMIND, b.update(121 * min, 60.0, 0))
    }
}

class LoudGuardTest {
    @Test fun warnsAfterThirtyLoudSeconds() {
        val g = LoudGuard()
        val fired = (0..40).filter { g.update(90.0, it * 1_000L) }
        assertEquals(listOf(30), fired)
    }

    @Test fun shortHornBlastsAreIgnored() {
        val g = LoudGuard()
        assertFalse((0..60).any { g.update(if (it % 10 < 3) 95.0 else 70.0, it * 1_000L) })
    }
}

class MountGuardTest {
    private fun feed(g: MountGuard, x: Float, y: Float, z: Float, fromMs: Long, toMs: Long): Boolean {
        var hit = false
        var t = fromMs
        while (t < toMs) { hit = g.update(Vec3(x, y, z, t)) || hit; t += 100 }
        return hit
    }

    @Test fun phoneSlidingOverIsCaught() {
        val g = MountGuard()
        assertFalse(feed(g, 0f, 9.81f, 0f, 0, 4_000))        // upright in the holder
        assertTrue(feed(g, 0f, 0f, 9.81f, 4_000, 7_000))     // fell flat
    }

    @Test fun roadVibrationIsNotAFall() {
        val g = MountGuard()
        feed(g, 0f, 9.81f, 0f, 0, 4_000)
        var hit = false
        var t = 4_000L
        repeat(60) { i -> hit = g.update(Vec3(if (i % 2 == 0) 1.5f else -1.5f, 9.81f, 0f, t)) || hit; t += 100 }
        assertFalse(hit)
    }
}

class MountGuardBrakingTest {
    @Test fun hardBrakingIsNotAFall() {
        val g = MountGuard()
        var t = 0L
        repeat(40) { g.update(Vec3(0f, 9.81f, 0f, t)); t += 100 }          // mounted upright
        var hit = false
        repeat(30) { hit = g.update(Vec3(0f, 9.81f, 6.9f, t)) || hit; t += 100 } // 0.7 g braking for 3 s (~35°)
        assertFalse(hit)
    }
}
