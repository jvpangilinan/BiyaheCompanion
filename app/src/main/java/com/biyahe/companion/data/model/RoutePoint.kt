package com.biyahe.companion.data.model

import java.util.Locale

/**
 * One point of a stored route.
 *
 * [roughness] is the vibration (RMS jolt, m/s²) felt on the stretch that ENDS at this point, and
 * [bumps] the big bumps felt on it. Both come from the accelerometer while the trip records; they
 * are null / 0 for points that have no motion data (old trips).
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val roughness: Double? = null,
    val bumps: Int = 0
)

/**
 * Routes are stored in a single TEXT column so a whole trip stays one row in SQLite (and one field
 * in Firebase):
 *
 *   old format:  "lat,lng;lat,lng;..."
 *   new format:  "lat,lng,roughness,bumps;..."   (roughness left empty when unknown)
 *
 * Both formats decode, so trips saved before the roughness map still open.
 */
object RouteCodec {
    fun encode(points: List<RoutePoint>): String =
        points.joinToString(";") { p ->
            val base = "${p.latitude},${p.longitude}"
            if (p.roughness == null && p.bumps == 0) base
            else base + "," + (p.roughness?.let { String.format(Locale.US, "%.2f", it) } ?: "") + "," + p.bumps
        }

    fun decode(encoded: String): List<RoutePoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(";").mapNotNull { pair ->
            val parts = pair.split(",")
            val lat = parts.getOrNull(0)?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat == null || lng == null) return@mapNotNull null
            RoutePoint(
                latitude = lat,
                longitude = lng,
                roughness = parts.getOrNull(2)?.toDoubleOrNull(),
                bumps = parts.getOrNull(3)?.toIntOrNull() ?: 0
            )
        }
    }
}
