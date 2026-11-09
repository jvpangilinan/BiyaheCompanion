package com.biyahe.companion.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrowsinessTest {

    private val step = 100L   // 10 frames a second, like the camera

    /** Feeds [ms] of frames with the same openness; returns every event that fired. */
    private fun DrowsinessDetector.feed(from: Long, ms: Long, openness: Float?): Pair<Long, List<EyeEvent>> {
        val events = mutableListOf<EyeEvent>()
        var t = from
        while (t < from + ms) { update(t, openness)?.let { events += it }; t += step }
        return t to events
    }

    @Test fun blinksNeverSoundTheAlarm() {
        val d = DrowsinessDetector(0.9f)
        var t = 0L
        val events = mutableListOf<EyeEvent>()
        repeat(15) {   // a 0.3 s blink every 4 s, for a minute
            d.feed(t, 3_700, 0.9f).also { t = it.first; events += it.second }
            d.feed(t, 300, 0.05f).also { t = it.first; events += it.second }
        }
        assertTrue(events.isEmpty())
    }

    @Test fun eyesClosedTooLongSoundsTheAlarmThenStopsWhenOpen() {
        val d = DrowsinessDetector(0.9f)
        var (t, ev) = d.feed(0, 5_000, 0.9f)
        assertTrue(ev.isEmpty())
        d.feed(t, 1_600, 0.05f).also { t = it.first; ev = it.second }
        assertEquals(listOf(EyeEvent.MICROSLEEP), ev)
        assertTrue(d.alarmOn)
        // Still closed: no second alarm.
        d.feed(t, 3_000, 0.05f).also { t = it.first; ev = it.second }
        assertTrue(ev.isEmpty())
        // Open for a second: awake.
        d.feed(t, 1_200, 0.9f).also { t = it.first; ev = it.second }
        assertEquals(listOf(EyeEvent.AWAKE), ev)
        assertFalse(d.alarmOn)
    }

    @Test fun strictFiresSoonerThanGentle() {
        val strict = DrowsinessDetector(0.9f, EyeSensitivity.STRICT)
        val gentle = DrowsinessDetector(0.9f, EyeSensitivity.GENTLE)
        assertEquals(listOf(EyeEvent.MICROSLEEP), strict.feed(0, 1_200, 0.05f).second)
        assertTrue(gentle.feed(0, 1_200, 0.05f).second.isEmpty())
    }

    @Test fun aShortHeadTurnDoesNotResetAClosure() {
        val d = DrowsinessDetector(0.9f)
        var (t, _) = d.feed(0, 800, 0.05f)
        d.feed(t, 300, null).also { t = it.first }        // face lost for 0.3 s
        assertEquals(listOf(EyeEvent.MICROSLEEP), d.feed(t, 800, 0.05f).second)
    }

    @Test fun droopingEyesForAMinuteGiveADrowsyWarningOnce() {
        val d = DrowsinessDetector(0.9f)
        // Half-closed: under 60% of normal, but not "closed".
        val (t, ev) = d.feed(0, 60_000, 0.45f)
        assertEquals(listOf(EyeEvent.DROWSY), ev)
        // Cooldown: not again within 2 minutes.
        assertTrue(d.feed(t, 60_000, 0.45f).second.isEmpty())
    }

    @Test fun manyLongBlinksAddUpToDrowsy() {
        val d = DrowsinessDetector(0.9f)
        var t = 0L
        val events = mutableListOf<EyeEvent>()
        repeat(30) {   // closed 0.4 s out of every 2 s = 20% of the time
            d.feed(t, 1_600, 0.9f).also { t = it.first; events += it.second }
            d.feed(t, 400, 0.05f).also { t = it.first; events += it.second }
        }
        assertEquals(listOf(EyeEvent.DROWSY), events)
    }

    @Test fun noFaceForAWhileIsReportedNotTreatedAsClosed() {
        val d = DrowsinessDetector(0.9f)
        var (t, _) = d.feed(0, 2_000, 0.9f)
        val (_, ev) = d.feed(t, 21_000, null)
        assertEquals(listOf(EyeEvent.FACE_LOST), ev)
        assertFalse(d.alarmOn)
    }

    @Test fun calibrationUsesTheMedianAndRejectsUnclearEyes() {
        val c = EyeCalibration(minSamples = 5)
        listOf(0.8f, 0.85f, 0.1f, 0.9f, 0.82f).forEach(c::add)   // one blink
        assertEquals(0.82f, c.result()!!, 0.001f)

        val dark = EyeCalibration(minSamples = 5)
        repeat(10) { dark.add(0.2f) }                                // sunglasses / too dark
        assertNull(dark.result())

        val few = EyeCalibration(minSamples = 5)
        few.add(0.9f); few.add(null)
        assertNull(few.result())
    }

    @Test fun opennessAveragesTheEyesThatAreVisible() {
        assertEquals(0.5f, eyeOpenness(0.4f, 0.6f)!!, 0.001f)
        assertEquals(0.7f, eyeOpenness(null, 0.7f)!!, 0.001f)
        assertNull(eyeOpenness(null, null))
    }
}
