package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import kotlin.math.sqrt

// Pure Kotlin (no Android classes), so every rule here is covered by unit tests.

/**
 * Speed limit warning. Feed it the speed once a second.
 *
 *  - Warns only after the speed has stayed above the limit for [overForMs] (one bad GPS reading
 *    is not enough), then repeats every [repeatMs] while you are still too fast.
 *  - You are "back under" only below limit - [hysteresisKmh], so hovering at the limit does not
 *    start a new warning every second.
 *  - Counts warnings (one per speeding episode) and the total time over the limit.
 */
class SpeedWatcher(
    private val overForMs: Long = 3_000L,
    private val repeatMs: Long = 20_000L,
    private val hysteresisKmh: Double = 3.0
) {
    var warnings = 0
        private set
    var overspeedMs = 0L
        private set

    private var overSince: Long? = null
    private var lastWarnAt = 0L
    private var episodeWarned = false
    private var lastSampleAt = 0L
    private var wasOver = false

    fun reset() {
        warnings = 0; overspeedMs = 0L; overSince = null; lastWarnAt = 0L
        episodeWarned = false; lastSampleAt = 0L; wasOver = false
    }

    /** @return true when a warning should be given right now. [limitKmh] null = feature off. */
    fun update(speedKmh: Double, limitKmh: Int?, nowMs: Long): Boolean {
        val dt = if (lastSampleAt == 0L) 0L else (nowMs - lastSampleAt).coerceIn(0L, 5_000L)
        lastSampleAt = nowMs
        if (limitKmh == null) {
            overSince = null; wasOver = false; episodeWarned = false
            return false
        }
        if (speedKmh > limitKmh) {
            if (wasOver) overspeedMs += dt
            wasOver = true
            val since = overSince ?: nowMs.also { overSince = it }
            val firstWarning = !episodeWarned && nowMs - since >= overForMs
            val repeat = episodeWarned && nowMs - lastWarnAt >= repeatMs
            if (firstWarning || repeat) {
                if (firstWarning) warnings++
                episodeWarned = true
                lastWarnAt = nowMs
                return true
            }
        } else {
            wasOver = false
            if (speedKmh <= limitKmh - hysteresisKmh) {
                overSince = null
                episodeWarned = false
            }
        }
        return false
    }
}

/**
 * Real crash detection while a trip records. Two rules, both need a sudden stop:
 *
 *  1. Impact: a jolt of [impactG] g or more while you were going at least [fromKmh],
 *     and the GPS shows you stopped (at most [toKmh]) within [windowMs].
 *  2. Sudden stop: between two GPS fixes at most [dropWithinMs] apart, the speed fell by
 *     [dropKmh] or more down to [toKmh] (about 2 g of braking, beyond any normal stop).
 *
 * Only fresh GPS fixes are used, so losing signal in a tunnel never looks like a stop.
 */
class CrashDetector(
    private val impactG: Double = 3.0,
    private val fromKmh: Double = 25.0,
    private val toKmh: Double = 5.0,
    private val windowMs: Long = 10_000L,
    private val dropKmh: Double = 35.0,
    private val dropWithinMs: Long = 2_000L
) {
    private val fixes = ArrayDeque<Pair<Long, Double>>() // time, km/h
    private var impactAt: Long? = null

    @Synchronized
    fun reset() {
        fixes.clear(); impactAt = null
    }

    /** Raw accelerometer magnitude (m/s², gravity included). */
    @Synchronized
    fun addAccel(magnitude: Double, timeMs: Long) {
        if (magnitude < impactG * G) return
        val wasFast = fixes.any { timeMs - it.first <= 5_000L && it.second >= fromKmh }
        if (wasFast) impactAt = timeMs
    }

    /** One GPS fix with a measured speed. @return true when a crash is detected. */
    @Synchronized
    fun addFix(speedKmh: Double, timeMs: Long): Boolean {
        val prev = fixes.lastOrNull()
        fixes.addLast(timeMs to speedKmh)
        while (fixes.isNotEmpty() && timeMs - fixes.first().first > 15_000L) fixes.removeFirst()

        val impact = impactAt
        if (impact != null) {
            if (timeMs - impact > windowMs) impactAt = null
            else if (speedKmh <= toKmh) {
                reset()
                return true
            }
        }
        if (prev != null) {
            val gap = timeMs - prev.first
            if (gap in 1..dropWithinMs && prev.second - speedKmh >= dropKmh && speedKmh <= toKmh) {
                reset()
                return true
            }
        }
        return false
    }

    private companion object {
        const val G = 9.81
    }
}

/** How hard the stage crash demo is to set off. */
enum class CrashDemoLevel(val number: Int, val title: String, val howTo: String) {
    EASY(1, "Easy", "Take one step and stop."),
    MEDIUM(2, "Medium", "Walk a couple of steps, stop with a small jolt."),
    NORMAL(3, "Normal", "Walk a few steps, stop sharply with a firm jolt, hold still.");

    companion object {
        fun fromNumber(n: Int) = entries.firstOrNull { it.number == n } ?: MEDIUM
    }
}

/**
 * STAGE DEMO ONLY: the same idea as the real detector (moving, a jolt, then still) scaled down to
 * walking, using the accelerometer alone because GPS does not work indoors.
 *
 *   EASY    one step (a small bump) followed by half a second of stillness
 *   MEDIUM  some walking, a moderate jolt (about 1 g), then stillness
 *   NORMAL  walking, a firm jolt (about 1.5 g), then a steady hold
 *
 * The first [graceMs] after arming are ignored, so tapping the Arm button never counts.
 */
class DemoCrashDetector(
    private val spikeMs2: Double = 15.0,
    private val movingRms: Double = 1.0,
    private val needWalkingFirst: Boolean = true,
    private val stillRms: Double = 0.6,
    private val stillForMs: Long = 800L,
    private val windowMs: Long = 4_000L,
    private val graceMs: Long = 1_500L
) {
    constructor(level: CrashDemoLevel) : this(
        spikeMs2 = when (level) { CrashDemoLevel.EASY -> 3.0; CrashDemoLevel.MEDIUM -> 9.0; CrashDemoLevel.NORMAL -> 15.0 },
        movingRms = when (level) { CrashDemoLevel.EASY -> 0.3; CrashDemoLevel.MEDIUM -> 0.6; CrashDemoLevel.NORMAL -> 1.0 },
        needWalkingFirst = level != CrashDemoLevel.EASY,
        stillRms = when (level) { CrashDemoLevel.EASY -> 1.0; CrashDemoLevel.MEDIUM -> 0.8; CrashDemoLevel.NORMAL -> 0.6 },
        stillForMs = when (level) { CrashDemoLevel.EASY -> 500L; CrashDemoLevel.MEDIUM -> 600L; CrashDemoLevel.NORMAL -> 800L }
    )

    private var gx = 0.0; private var gy = 0.0; private var gz = 0.0
    private var ready = false
    private var ema = 0.0
    private var firstAt: Long? = null
    private var lastMovingAt = Long.MIN_VALUE / 2
    private var spikeAt: Long? = null
    private var stillSince: Long? = null

    fun reset() {
        ready = false; ema = 0.0; firstAt = null; lastMovingAt = Long.MIN_VALUE / 2; spikeAt = null; stillSince = null
    }

    /** @return true when the demo "crash" is detected. */
    fun add(v: Vec3): Boolean {
        val t = v.timestampMs
        if (!ready) {
            gx = v.x.toDouble(); gy = v.y.toDouble(); gz = v.z.toDouble(); ready = true
            firstAt = t
            return false
        }
        gx = 0.8 * gx + 0.2 * v.x; gy = 0.8 * gy + 0.2 * v.y; gz = 0.8 * gz + 0.2 * v.z
        val lx = v.x - gx; val ly = v.y - gy; val lz = v.z - gz
        val jolt = sqrt(lx * lx + ly * ly + lz * lz)
        ema += 0.1 * (jolt * jolt - ema)
        val rms = sqrt(ema)
        if (t - (firstAt ?: t) < graceMs) return false   // settling after the Arm tap

        val spike = spikeAt
        val walkedFirst = !needWalkingFirst || t - lastMovingAt <= 3_000L
        if (jolt >= spikeMs2 && walkedFirst) {
            spikeAt = t; stillSince = null
            return false
        }
        if (rms >= movingRms) lastMovingAt = t

        if (spike != null) {
            if (t - spike > windowMs) {
                spikeAt = null; stillSince = null
            } else if (rms < stillRms) {
                val since = stillSince ?: t.also { stillSince = it }
                if (t - since >= stillForMs) {
                    reset()
                    return true
                }
            } else {
                stillSince = null
            }
        }
        return false
    }
}

object SleepMath {
    /** Under this many hours counts as short sleep (drowsy-driving risk). */
    const val SHORT_SLEEP_H = 6.0

    /** Hours from bedtime to wake time (minutes after midnight); crosses midnight as needed. */
    fun hoursBetween(bedtimeMin: Int, wakeMin: Int): Double =
        ((wakeMin - bedtimeMin + 1440) % 1440) / 60.0

    /** "11:00 PM" from minutes after midnight. */
    fun formatTime(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        val h24 = m / 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return "%d:%02d %s".format(h12, m % 60, if (h24 < 12) "AM" else "PM")
    }

    fun formatHours(h: Double): String =
        if (h == Math.floor(h)) "${h.toInt()} h" else "%.1f h".format(h)
}
