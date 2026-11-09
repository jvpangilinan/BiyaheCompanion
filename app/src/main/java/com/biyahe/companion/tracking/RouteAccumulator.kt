package com.biyahe.companion.tracking

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }
}

/** Google "encoded polyline" (precision 5): a whole route in one short string. */
object Polyline {
    fun encode(points: List<LatLon>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0
        for (p in points) {
            val lat = (p.lat * 1e5).roundToInt()
            val lon = (p.lon * 1e5).roundToInt()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    fun decode(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        var i = 0
        var lat = 0
        var lon = 0
        while (i < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && i < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            if (i >= encoded.length) break
            shift = 0
            result = 0
            do {
                b = encoded[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && i < encoded.length)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out.add(LatLon(lat / 1e5, lon / 1e5))
        }
        return out
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}

/**
 * Turns raw GPS fixes into a clean route + distance. Pure Kotlin (no Android classes).
 *
 *  - Fixes less accurate than [maxAccuracyM] are ignored.
 *  - A fix that implies an impossible speed (> [maxSpeedMps]) is a GPS jump and is ignored.
 *  - Movement smaller than [minStepM] is treated as jitter while standing still
 *    (stops the distance creeping up in traffic).
 */
class RouteAccumulator(
    private val maxAccuracyM: Float = 35f,
    private val minStepM: Double = 4.0,
    private val maxSpeedMps: Double = 70.0
) {
    private val points = ArrayList<TrackPoint>()
    private var distanceM = 0.0
    private var maxSeenMps = 0.0
    private var currentMps = 0.0
    private var currentAt = 0L

    /** @return true if the fix became part of the route. */
    @Synchronized
    fun add(p: TrackPoint): Boolean {
        if (p.accuracyM > maxAccuracyM) return false
        val last = points.lastOrNull()
        if (last == null) {
            points.add(p)
            setSpeed(p, if (p.speedMps >= 0f) p.speedMps.toDouble() else 0.0)
            return true
        }
        val dtSec = (p.timeMs - last.timeMs) / 1000.0
        if (dtSec <= 0.0) return false
        val d = GeoMath.haversineM(last.lat, last.lon, p.lat, p.lon)
        val computed = d / dtSec
        if (computed > maxSpeedMps) return false

        val speed = if (p.speedMps >= 0f) p.speedMps.toDouble() else computed
        setSpeed(p, speed)
        if (d < minStepM) return false

        points.add(p)
        distanceM += d
        if (speed > maxSeenMps && speed <= maxSpeedMps) maxSeenMps = speed
        return true
    }

    private fun setSpeed(p: TrackPoint, mps: Double) {
        currentMps = if (mps < 0.5) 0.0 else mps // below ~2 km/h is just GPS noise
        currentAt = p.timeMs
    }

    @Synchronized fun distanceM(): Double = distanceM
    @Synchronized fun maxSpeedMps(): Double = maxSeenMps
    @Synchronized fun pointCount(): Int = points.size
    @Synchronized fun lastPoint(): TrackPoint? = points.lastOrNull()
    @Synchronized fun snapshot(): List<TrackPoint> = ArrayList(points)

    /** Speed from the newest fix, or 0 if that fix is older than [staleMs]. */
    @Synchronized
    fun currentSpeedMps(nowMs: Long, staleMs: Long = 5_000L): Double =
        if (nowMs - currentAt > staleMs) 0.0 else currentMps

    @Synchronized
    fun reset() {
        points.clear()
        distanceM = 0.0; maxSeenMps = 0.0; currentMps = 0.0; currentAt = 0L
    }
}
