package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDemoLevelTest {

    /** Feeds samples at 50 Hz and remembers whether the detector fired. */
    private class Feed(level: CrashDemoLevel) {
        val d = DemoCrashDetector(level)
        var t = 0L
        var hit = false
        fun add(y: Float) { hit = d.add(Vec3(0f, y, 9.81f, t)) || hit; t += 20 }
        fun still(ms: Long) = repeat((ms / 20).toInt()) { add(0f) }
        fun oneStep() = repeat(8) { i -> add(if (i < 4) 3.5f else -3.5f) }          // ~0.16 s bounce
        fun walk(ms: Long) = repeat((ms / 20).toInt()) { i -> add(if (i % 25 < 12) 3f else -3f) }
    }

    @Test fun easyTriggersOnOneStepThenStop() {
        val f = Feed(CrashDemoLevel.EASY)
        f.still(2_000); f.oneStep(); f.still(1_000)
        assertTrue(f.hit)
    }

    @Test fun mediumNeedsMoreThanOneStep() {
        val f = Feed(CrashDemoLevel.MEDIUM)
        f.still(2_000); f.oneStep(); f.still(1_000)
        assertFalse(f.hit)
        f.walk(2_000); f.add(11f); f.still(2_000)   // walk, ~1 g jolt, stop and hold
        assertTrue(f.hit)
    }

    @Test fun normalIgnoresAModerateJolt() {
        val f = Feed(CrashDemoLevel.NORMAL)
        f.still(2_000); f.walk(2_000); f.add(11f); f.still(1_500)
        assertFalse(f.hit)
        f.walk(2_000); f.add(25f); f.still(2_000)   // firm jolt, then hold
        assertTrue(f.hit)
    }

    @Test fun tappingArmIsIgnored() {
        val f = Feed(CrashDemoLevel.EASY)
        f.oneStep(); f.still(1_000)                 // inside the 1.5 s grace period
        assertFalse(f.hit)
    }
}
