package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The "more guards" rules. Pure Kotlin (no Android classes), so all of them are unit-tested.

/** What the light sensor says changed. */
enum class LightChange { ENTER_TUNNEL, ENTER_NIGHT, EXIT_DARK }

/**
 * Tunnel & night guard (ambient light sensor).
 *
 *  - Dark = under [darkLux] for [enterMs]. If it was bright (at least [brightLux]) in the last
 *    [suddenMs], the darkness came suddenly: a tunnel or covered parking. Otherwise: nightfall.
 *  - Light again = over [exitLux] for [exitMs] (two different limits so it doesn't flicker).
 */
class LightGuard(
    private val darkLux: Float = 10f,
    private val exitLux: Float = 30f,
    private val brightLux: Float = 80f,
    private val enterMs: Long = 1_000L,
    private val exitMs: Long = 2_000L,
    private val suddenMs: Long = 6_000L
) {
    var isDark = false
        private set

    private val history = ArrayDeque<Pair<Long, Float>>()
    private var darkSince: Long? = null
    private var lightSince: Long? = null

    fun reset() { isDark = false; history.clear(); darkSince = null; lightSince = null }

    fun update(lux: Float, t: Long): LightChange? {
        history.addLast(t to lux)
        while (history.isNotEmpty() && t - history.first().first > suddenMs + enterMs) history.removeFirst()

        if (!isDark) {
            if (lux < darkLux) {
                val since = darkSince ?: t.also { darkSince = it }
                if (t - since >= enterMs) {
                    isDark = true; lightSince = null
                    val wasBright = history.any { it.first < since && since - it.first <= suddenMs && it.second >= brightLux }
                    return if (wasBright) LightChange.ENTER_TUNNEL else LightChange.ENTER_NIGHT
                }
            } else darkSince = null
        } else {
            if (lux > exitLux) {
                val since = lightSince ?: t.also { lightSince = it }
                if (t - since >= exitMs) {
                    isDark = false; darkSince = null
                    return LightChange.EXIT_DARK
                }
            } else lightSince = null
        }
        return null
    }
}

/** Rain mode (barometer): roads are slippery, so the speed limit drops by [cutKmh] (never under 20). */
object RainMode {
    const val CUT_KMH = 10
    fun limit(baseKmh: Int?, rainLikely: Boolean, rainModeOn: Boolean): Int? =
        if (baseKmh != null && rainLikely && rainModeOn) (baseKmh - CUT_KMH).coerceAtLeast(20) else baseKmh
}

/** A reported hazard, as the hazard-ahead guard needs it. */
data class HazardPoint(val id: Long, val type: String, val lat: Double, val lon: Double)

/**
 * Hazard ahead (GPS + your camera reports + compass): the nearest report within [rangeM] that lies
 * in front of you, within [coneDeg] of the direction you are travelling. Each hazard is announced
 * once per trip (the caller keeps [warned]).
 */
object HazardAhead {
    fun find(
        lat: Double, lon: Double, headingDeg: Double, hazards: List<HazardPoint>, warned: Set<Long>,
        rangeM: Double = 300.0, coneDeg: Double = 35.0
    ): Pair<HazardPoint, Double>? = hazards
        .asSequence()
        .filter { it.id !in warned }
        .map { it to distanceM(lat, lon, it.lat, it.lon) }
        .filter { (h, d) -> d in 15.0..rangeM && abs(wrap180(bearingDeg(lat, lon, h.lat, h.lon) - headingDeg)) <= coneDeg }
        .minByOrNull { it.second }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1) * 6_371_000.0
        val dLon = Math.toRadians(lon2 - lon1) * 6_371_000.0 * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dLat * dLat + dLon * dLon)
    }

    private fun wrap180(d: Double) = ((d + 540.0) % 360.0) - 180.0
}

enum class BreakEvent { REMIND, BREAK_LOGGED }

/**
 * Break reminder (step counter + clock): after [everyMs] of driving, remind; again every
 * [repeatMs]. A real break = stopped for at least [stopMs] AND at least [minSteps] steps walked
 * during the stop (the step counter proves you got out and stretched). That restarts the clock.
 */
class BreakTimer(
    private val everyMs: Long = 2 * 60 * 60_000L,
    private val repeatMs: Long = 15 * 60_000L,
    private val stopMs: Long = 2 * 60_000L,
    private val minSteps: Int = 50
) {
    private var drivingSince: Long? = null
    private var lastRemindAt: Long? = null
    private var stoppedSince: Long? = null
    private var logged = false

    fun reset() { drivingSince = null; lastRemindAt = null; stoppedSince = null; logged = false }

    /** [stepsSinceStop] = steps walked since the car stopped (0 while moving). */
    fun update(t: Long, speedKmh: Double, stepsSinceStop: Int): BreakEvent? {
        val start = drivingSince ?: t.also { drivingSince = it }
        if (speedKmh < 3.0) {
            val since = stoppedSince ?: t.also { stoppedSince = it; logged = false }
            if (!logged && t - since >= stopMs && stepsSinceStop >= minSteps) {
                logged = true
                drivingSince = t; lastRemindAt = null
                return BreakEvent.BREAK_LOGGED
            }
            return null
        }
        stoppedSince = null
        val due = t - start >= everyMs && (lastRemindAt == null || t - lastRemindAt!! >= repeatMs)
        if (due) {
            lastRemindAt = t
            return BreakEvent.REMIND
        }
        return null
    }

    /** Minutes of driving since the last break (for the screen). */
    fun drivingMinutes(t: Long): Long = drivingSince?.let { (t - it) / 60_000 } ?: 0
}

/**
 * Loud cabin (microphone): [thresholdDb] or more for [forMs] means you may not hear horns or
 * sirens. Warns at most every [cooldownMs].
 */
class LoudGuard(
    private val thresholdDb: Double = 85.0,
    private val forMs: Long = 30_000L,
    private val cooldownMs: Long = 10 * 60_000L
) {
    private var loudSince: Long? = null
    private var lastWarnAt: Long? = null

    fun reset() { loudSince = null; lastWarnAt = null }

    fun update(db: Double?, t: Long): Boolean {
        if (db == null || db < thresholdDb) { loudSince = null; return false }
        val since = loudSince ?: t.also { loudSince = it }
        if (t - since >= forMs && (lastWarnAt == null || t - lastWarnAt!! >= cooldownMs)) {
            lastWarnAt = t
            return true
        }
        return false
    }
}

/**
 * Mount check (accelerometer): learns which way gravity points in the phone once it has been
 * steady for [settleMs]. If it then tips more than [tiltDeg] for [holdMs], the phone has slid or
 * fallen out of its holder. Warns at most every [cooldownMs], then learns the new position.
 */
class MountGuard(
    private val tiltDeg: Double = 40.0,      // hard braking tilts "gravity" ~30°; a fallen phone is far more
    private val settleMs: Long = 3_000L,
    private val holdMs: Long = 2_000L,
    private val cooldownMs: Long = 60_000L
) {
    private var gx = 0.0; private var gy = 0.0; private var gz = 0.0
    private var ready = false
    private var base: Triple<Double, Double, Double>? = null
    private var steadySince: Long? = null
    private var tiltSince: Long? = null
    private var lastWarnAt: Long? = null

    fun reset() {
        ready = false; base = null; steadySince = null; tiltSince = null; lastWarnAt = null
    }

    /** Angle (degrees) between gravity now and the learned mount position; 0 before it is learned. */
    var tiltNow = 0.0
        private set

    fun update(v: Vec3): Boolean {
        val t = v.timestampMs
        if (!ready) { gx = v.x.toDouble(); gy = v.y.toDouble(); gz = v.z.toDouble(); ready = true }
        else { gx = 0.85 * gx + 0.15 * v.x; gy = 0.85 * gy + 0.15 * v.y; gz = 0.85 * gz + 0.15 * v.z }

        val b = base
        if (b == null) {
            val since = steadySince ?: t.also { steadySince = it }
            if (t - since >= settleMs) base = Triple(gx, gy, gz)
            return false
        }
        tiltNow = angle(b, Triple(gx, gy, gz))
        if (tiltNow >= tiltDeg) {
            val since = tiltSince ?: t.also { tiltSince = it }
            if (t - since >= holdMs && (lastWarnAt == null || t - lastWarnAt!! >= cooldownMs)) {
                lastWarnAt = t
                base = null; steadySince = null; tiltSince = null   // learn the new position
                return true
            }
        } else tiltSince = null
        return false
    }

    private fun angle(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val dot = a.first * b.first + a.second * b.second + a.third * b.third
        val na = sqrt(a.first * a.first + a.second * a.second + a.third * a.third)
        val nb = sqrt(b.first * b.first + b.second * b.second + b.third * b.third)
        if (na < 1e-3 || nb < 1e-3) return 0.0
        return Math.toDegrees(acos((dot / (na * nb)).coerceIn(-1.0, 1.0)))
    }
}
