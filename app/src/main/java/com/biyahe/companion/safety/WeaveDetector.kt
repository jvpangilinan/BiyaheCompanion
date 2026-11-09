package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lane weaving detection for real trips. The phone cannot see lanes and GPS is too rough to
 * tell one lane from the next, so this looks at HOW the car turns (gyroscope) instead of where
 * it is:
 *
 *   curve / real turn   heading keeps changing one way and stays changed       -> ignored
 *   one lane change     a quick S: heading swings 4°+ and comes back in 1.5-8 s -> counted, normal
 *   weaving             many of those close together                          -> warning
 *
 * Safeguards:
 *  - only above [minSpeedKmh] (slow traffic in Manila weaves all the time)
 *  - the gyroscope's turning is compared with the GPS course every few seconds; if they disagree
 *    by more than [mismatchDeg], someone is handling the phone, so detection pauses
 *
 * It is advice, not a guarantee, and works best with the phone fixed in a mount.
 * Pure Kotlin, so it is unit-tested.
 */
class WeaveDetector(
    private val minSpeedKmh: Double = 40.0,
    private val swerveDeg: Double = 4.0,
    private val settleDeg: Double = 1.5,
    private val minSwerveMs: Long = 1_500L,
    private val maxSwerveMs: Long = 8_000L,
    private val baselineTauS: Double = 10.0,
    private val warnCount: Int = 5,
    private val warnWindowMs: Long = 120_000L,
    private val warnCooldownMs: Long = 300_000L,
    private val mismatchDeg: Double = 25.0,
    private val pauseMs: Long = 10_000L
) {
    var weaves = 0
        private set

    private var gx = 0.0; private var gy = 0.0; private var gz = 9.81
    private var gravityReady = false
    private var lastGyroAt = 0L
    private var gyroHeading = 0.0            // relative, degrees, clockwise; not wrapped
    private var baseline = 0.0
    private var speedKmh = 0.0
    private var lastFixAt = 0L
    private var excursionStart: Long? = null
    private var waitForSettle = false
    private var pausedUntil = 0L
    private var lastWarnAt = Long.MIN_VALUE / 2
    private val recent = ArrayDeque<Long>()

    private data class Fix(val t: Long, val lat: Double, val lon: Double, val gyro: Double, val bearing: Double?)
    private val fixes = ArrayDeque<Fix>()

    @Synchronized
    fun reset() {
        weaves = 0; gravityReady = false; lastGyroAt = 0L; gyroHeading = 0.0; baseline = 0.0
        speedKmh = 0.0; lastFixAt = 0L; excursionStart = null; waitForSettle = false
        pausedUntil = 0L; lastWarnAt = Long.MIN_VALUE / 2; recent.clear(); fixes.clear()
    }

    @Synchronized
    fun onAccel(v: Vec3) {
        if (!gravityReady) {
            gx = v.x.toDouble(); gy = v.y.toDouble(); gz = v.z.toDouble(); gravityReady = true
        } else {
            gx = 0.95 * gx + 0.05 * v.x; gy = 0.95 * gy + 0.05 * v.y; gz = 0.95 * gz + 0.05 * v.z
        }
    }

    @Synchronized
    fun onGyro(v: Vec3) {
        val t = v.timestampMs
        val dt = if (lastGyroAt == 0L) 0.0 else ((t - lastGyroAt) / 1000.0).coerceIn(0.0, 0.1)
        lastGyroAt = t
        if (!gravityReady) return
        val n = sqrt(gx * gx + gy * gy + gz * gz).takeIf { it > 1e-3 } ?: return
        val yawDps = -Math.toDegrees((v.x * gx + v.y * gy + v.z * gz) / n) // + = turning right
        gyroHeading += yawDps * dt
        baseline += (gyroHeading - baseline) * (dt / baselineTauS).coerceAtMost(1.0)

        val active = speedKmh >= minSpeedKmh && t - lastFixAt <= 3_000L && t >= pausedUntil
        if (!active) {
            excursionStart = null
            return
        }
        val dev = abs(gyroHeading - baseline)
        val start = excursionStart
        when {
            waitForSettle -> if (dev <= settleDeg) waitForSettle = false
            start == null -> if (dev >= swerveDeg) excursionStart = t
            dev <= settleDeg -> {
                if (t - start >= minSwerveMs) {   // came back: a lane change
                    weaves++
                    recent.addLast(t)
                }
                excursionStart = null
            }
            t - start > maxSwerveMs -> {          // never came back: a curve, not a swerve
                excursionStart = null
                waitForSettle = true
            }
        }
    }

    /** One GPS fix. @return true when the weaving warning should be given now. */
    @Synchronized
    fun onFix(lat: Double, lon: Double, speedKmh: Double, t: Long): Boolean {
        this.speedKmh = speedKmh
        lastFixAt = t

        val prev = fixes.lastOrNull()
        val bearing = prev?.let { if (distanceM(it.lat, it.lon, lat, lon) >= 8.0) bearingDeg(it.lat, it.lon, lat, lon) else null }
        fixes.addLast(Fix(t, lat, lon, gyroHeading, bearing))
        while (fixes.size > 2 && t - fixes.first().t > 8_000L) fixes.removeFirst()

        // Phone-handling check: gyroscope turning vs. GPS course over the last ~5 s.
        val then = fixes.firstOrNull { t - it.t in 4_000L..8_000L && it.bearing != null }
        if (bearing != null && then?.bearing != null && speedKmh >= minSpeedKmh) {
            val gps = wrap180(bearing - then.bearing)
            val gyro = gyroHeading - then.gyro
            if (abs(wrap180(gps - gyro)) > mismatchDeg) {
                pausedUntil = t + pauseMs
                excursionStart = null
                baseline = gyroHeading
            }
        }

        while (recent.isNotEmpty() && t - recent.first() > warnWindowMs) recent.removeFirst()
        if (recent.size >= warnCount && t - lastWarnAt >= warnCooldownMs) {
            lastWarnAt = t
            return true
        }
        return false
    }

    private fun wrap180(d: Double) = ((d + 540.0) % 360.0) - 180.0

    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1) * 6_371_000.0
        val dLon = Math.toRadians(lon2 - lon1) * 6_371_000.0 * cos(Math.toRadians(lat1))
        return sqrt(dLat * dLat + dLon * dLon)
    }
}
