package com.biyahe.companion.safety

import com.biyahe.companion.sensors.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class DriftStatus { OK, CAUTION, DRIFT_LEFT, DRIFT_RIGHT }

/** What the drift demo screen draws. x = metres right of the centre line, y = metres walked forward. */
data class DriftSnapshot(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val steps: Int = 0,
    /** Degrees away from the straight-ahead direction; + = turned right. */
    val deviationDeg: Double = 0.0,
    /** Turning speed right now (deg/s) from the gyroscope; + = turning right. */
    val turnRateDps: Double = 0.0,
    val compassDeg: Float? = null,
    val status: DriftStatus = DriftStatus.OK,
    val maxDriftM: Double = 0.0,
    val driftWarnings: Int = 0,
    val trail: List<Pair<Double, Double>> = listOf(0.0 to 0.0),
    val finished: Boolean = false
)

/**
 * "Walk a straight line" demo of drift detection (pedestrian dead reckoning).
 *
 *   DIRECTION: the gyroscope's turn rate around the vertical axis (found with gravity from the
 *              accelerometer) is added up into a heading. The gyroscope reacts instantly but slowly
 *              wanders, so the compass (magnetometer) gently pulls it back: a complementary filter.
 *   MOVEMENT:  each step (an up-and-down bounce in the accelerometer) moves you [stepLengthM]
 *              forward in the direction you are facing.
 *
 * The sideways distance from the centre line is the drift. Past [laneHalfM] you have left the
 * lane: the warning a drowsy driver drifting out of lane would get.
 *
 * Pure Kotlin, so it is unit-tested.
 */
class DriftTracker(
    private val stepLengthM: Double = 0.7,
    val laneHalfM: Double = 0.6,
    val cautionM: Double = 0.35,
    val turnCautionDeg: Double = 25.0,
    val goalM: Double = 6.0,
    private val compassWeight: Double = 0.02
) {
    private var heading: Double? = null      // fused heading, degrees clockwise from north
    private var reference: Double? = null    // "straight ahead", set on start
    private var compass: Float? = null
    private var compassAligned = false

    private var gx = 0.0; private var gy = 0.0; private var gz = 9.81
    private var gravityReady = false
    private var lastGyroAt = 0L
    private var turnRate = 0.0

    private var smoothMag = 9.81
    private var aboveStep = false
    private var lastStepAt = Long.MIN_VALUE / 2

    private var x = 0.0
    private var y = 0.0
    private var steps = 0
    private var maxDrift = 0.0
    private var warnings = 0
    private var inDrift = false
    private val trail = ArrayList<Pair<Double, Double>>().apply { add(0.0 to 0.0) }

    /** Makes the current facing direction "straight ahead" and puts you back at the start. */
    fun start() {
        // Without any reading yet, start at 0; the first compass reading re-aligns both
        // (see onCompass), so it never shows up as a fake turn.
        if (heading == null) heading = 0.0
        reference = heading
        x = 0.0; y = 0.0; steps = 0; maxDrift = 0.0; warnings = 0; inDrift = false
        trail.clear(); trail.add(0.0 to 0.0)
    }

    fun onCompass(deg: Float) {
        compass = deg
        val h = heading
        if (!compassAligned || h == null) {
            // First reading: move heading AND "straight ahead" together onto the compass frame.
            val shift = if (h == null) 0.0 else wrap180(deg - h)
            heading = deg.toDouble()
            reference = reference?.let { wrap360(it + shift) }
            compassAligned = true
            return
        }
        // Afterwards: the gyroscope leads, the compass gently corrects its slow drift.
        heading = wrap360(h + compassWeight * wrap180(deg - h))
    }

    fun onGyro(v: Vec3) {
        val dt = if (lastGyroAt == 0L) 0.0 else ((v.timestampMs - lastGyroAt) / 1000.0).coerceIn(0.0, 0.1)
        lastGyroAt = v.timestampMs
        if (!gravityReady) return
        val gNorm = sqrt(gx * gx + gy * gy + gz * gz).takeIf { it > 1e-3 } ?: return
        // Rotation around "up". Counter-clockwise seen from above (turning left) is positive,
        // and compass headings grow clockwise, so the sign flips.
        val upRate = (v.x * gx + v.y * gy + v.z * gz) / gNorm
        turnRate = -Math.toDegrees(upRate)
        val h = heading ?: return
        heading = wrap360(h + turnRate * dt)
    }

    /** @return true when a step was just counted. */
    fun onAccel(v: Vec3): Boolean {
        if (!gravityReady) {
            gx = v.x.toDouble(); gy = v.y.toDouble(); gz = v.z.toDouble(); gravityReady = true
        } else {
            gx = 0.9 * gx + 0.1 * v.x; gy = 0.9 * gy + 0.1 * v.y; gz = 0.9 * gz + 0.1 * v.z
        }
        smoothMag += 0.3 * (v.magnitude - smoothMag)
        // A step: the bounce rises above 10.8 m/s², then falls back below 9.8, at most ~3 steps/s.
        if (!aboveStep && smoothMag > 10.8) aboveStep = true
        if (aboveStep && smoothMag < 9.8) {
            aboveStep = false
            if (v.timestampMs - lastStepAt >= 300L) {
                lastStepAt = v.timestampMs
                step()
                return true
            }
        }
        return false
    }

    /** Moves one step forward along the current facing direction (also used by tests). */
    fun step() {
        if (y >= goalM) return
        val dev = Math.toRadians(deviation())
        x += stepLengthM * sin(dev)
        y += stepLengthM * cos(dev)
        steps++
        trail.add(x to y)
        maxDrift = maxOf(maxDrift, abs(x))
        val drifting = abs(x) >= laneHalfM
        if (drifting && !inDrift) warnings++
        if (drifting) inDrift = true else if (abs(x) < cautionM) inDrift = false
    }

    /** True once, when you just left the lane (time to warn). */
    fun justDrifted(previous: DriftStatus): Boolean {
        val now = status()
        return (now == DriftStatus.DRIFT_LEFT || now == DriftStatus.DRIFT_RIGHT) && now != previous
    }

    fun status(): DriftStatus = when {
        x <= -laneHalfM -> DriftStatus.DRIFT_LEFT
        x >= laneHalfM -> DriftStatus.DRIFT_RIGHT
        abs(x) >= cautionM || abs(deviation()) >= turnCautionDeg -> DriftStatus.CAUTION
        else -> DriftStatus.OK
    }

    fun snapshot() = DriftSnapshot(
        x = x, y = y, steps = steps,
        deviationDeg = deviation(),
        turnRateDps = turnRate,
        compassDeg = compass,
        status = status(),
        maxDriftM = maxDrift,
        driftWarnings = warnings,
        trail = trail.toList(),
        finished = y >= goalM
    )

    /** For tests: pretend the heading is [deg] right now. */
    internal fun setHeadingForTest(deg: Double) { heading = deg }

    private fun deviation(): Double {
        val h = heading ?: return 0.0
        val r = reference ?: return 0.0
        return wrap180(h - r)
    }

    private fun wrap180(d: Double): Double = ((d + 540.0) % 360.0) - 180.0
    private fun wrap360(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
}
