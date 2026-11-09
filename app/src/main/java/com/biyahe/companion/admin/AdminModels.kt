package com.biyahe.companion.admin

import com.biyahe.companion.data.model.HazardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Admin data, agencies and report wording. Pure Kotlin, so the rules are unit-tested.

data class AdminTrip(
    val uid: String,
    val startTime: Long,
    val distanceKm: Double,
    val durationMin: Long,
    val score: Int,
    val label: String,
    val bumps: Int,
    val speedWarnings: Int,
    val overspeedSec: Long,
    val weaving: Int,
    val rating: Int = 0,
    val mode: String = "DRIVING"
)

data class AdminHazard(
    /** Unique across all users: "uid/docId". */
    val key: String,
    val uid: String,
    val type: String,
    val note: String,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    /** Photo as Base64 JPEG (cloud) ... */
    val photoBase64: String? = null,
    /** ... or as a file on this phone (demo). */
    val photoPath: String? = null
) {
    val typeLabel: String get() = HazardType.fromName(type).label
}

data class AdminUser(val uid: String, val name: String, val email: String)

data class ReportRecord(val key: String, val agency: String, val sentAtMs: Long, val sentBy: String)

data class AdminSnapshot(
    val users: List<AdminUser> = emptyList(),
    val trips: List<AdminTrip> = emptyList(),
    val hazards: List<AdminHazard> = emptyList(),
    val reports: List<ReportRecord> = emptyList()
) {
    fun tripsOf(uid: String) = trips.filter { it.uid == uid }
    fun hazardsOf(uid: String) = hazards.filter { it.uid == uid }
    fun reportFor(key: String) = reports.lastOrNull { it.key == key }
    fun userName(uid: String) = users.firstOrNull { it.uid == uid }?.name ?: "Rider ${uid.take(6)}"
    val pendingHazards: List<AdminHazard> get() = hazards.filter { reportFor(it.key) == null }
}

/** Government offices a report can be routed to. No contact details are stored: the admin picks the recipient. */
enum class Agency(val short: String, val full: String, val handles: String) {
    DPWH("DPWH", "Department of Public Works and Highways", "national roads: potholes and damaged pavement"),
    LGU_ENGINEERING("City/Municipal Engineering", "City or Municipal Engineering Office", "local roads and streets"),
    MMDA("MMDA", "Metro Manila Development Authority", "major Metro Manila roads: flooding, obstructions, traffic"),
    LGU_DRRMO("Local DRRMO", "City or Municipal Disaster Risk Reduction and Management Office", "flooding and other dangers"),
    LTO("LTO", "Land Transportation Office", "speeding and reckless driving trends"),
    PNP_HPG("PNP-HPG", "Philippine National Police Highway Patrol Group", "road safety enforcement");
}

object Agencies {
    /** Rough Metro Manila box: MMDA only covers the NCR cities. */
    fun inMetroManila(lat: Double, lon: Double) = lat in 14.35..14.80 && lon in 120.90..121.15

    /** Recommended offices for a hazard, best first. */
    fun forHazard(h: AdminHazard): List<Agency> {
        val ncr = inMetroManila(h.lat, h.lon)
        return when (HazardType.fromName(h.type)) {
            HazardType.POTHOLE, HazardType.ROAD_DAMAGE -> listOf(Agency.DPWH, Agency.LGU_ENGINEERING)
            HazardType.FLOODING -> if (ncr) listOf(Agency.MMDA, Agency.LGU_DRRMO) else listOf(Agency.LGU_DRRMO)
            HazardType.OBSTRUCTION -> if (ncr) listOf(Agency.MMDA, Agency.LGU_ENGINEERING) else listOf(Agency.LGU_ENGINEERING)
            HazardType.OTHER -> listOf(Agency.LGU_ENGINEERING, Agency.LGU_DRRMO)
        }
    }

    /** Speeding and lane-weaving trends go to the traffic authorities. */
    val forSafetyTrends = listOf(Agency.LTO, Agency.PNP_HPG)
}

/**
 * Report wording. Riders are never named in what goes to an agency (Data Privacy Act, RA 10173):
 * only what, where and when.
 */
object ReportText {
    private fun fmt(ms: Long) = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(ms))

    fun hazardSubject(h: AdminHazard) = "Road hazard report: ${h.typeLabel} near ${coords(h)}"

    fun hazard(h: AdminHazard, agency: Agency, nowMs: Long): String = buildString {
        appendLine("ROAD HAZARD REPORT")
        appendLine("To: ${agency.full} (${agency.short})")
        appendLine()
        appendLine("Type: ${h.typeLabel}")
        appendLine("Seen: ${fmt(h.timestamp)}")
        appendLine("Location: ${coords(h)}")
        appendLine("Map: https://maps.google.com/?q=${coords(h).replace(" ", "")}")
        if (h.note.isNotBlank()) appendLine("Description: ${h.note}")
        appendLine("Photo: ${if (h.photoBase64 != null || h.photoPath != null) "attached" else "none"}")
        appendLine("Reference: BC-${h.key.substringAfter('/').takeLast(8).uppercase()}")
        appendLine()
        appendLine("This report was collected by Biyahe Companion, a commuter safety app (student project, SENSORVERSE 2026). " +
            "It was photographed and GPS-tagged by a rider and reviewed by an administrator on ${fmt(nowMs)}. " +
            "The rider's identity is withheld for privacy.")
    }

    fun safetySubject() = "Road safety summary from Biyahe Companion"

    fun safetySummary(s: AdminSnapshot, agency: Agency, nowMs: Long): String = buildString {
        val trips = s.trips
        val warnings = trips.sumOf { it.speedWarnings }
        val overMin = trips.sumOf { it.overspeedSec } / 60
        val weaving = trips.sumOf { it.weaving }
        val km = trips.sumOf { it.distanceKm }
        appendLine("ROAD SAFETY SUMMARY")
        appendLine("To: ${agency.full} (${agency.short})")
        appendLine("Prepared: ${fmt(nowMs)}")
        appendLine()
        appendLine("Riders: ${s.users.size}   Trips: ${trips.size}   Distance: ${"%.0f".format(Locale.US, km)} km")
        appendLine("Speed limit warnings: $warnings (about $overMin min over the limit in total)")
        appendLine("Lane changes / swerves at driving speed: $weaving")
        if (km > 0) appendLine("Warnings per 100 km: ${"%.1f".format(Locale.US, warnings * 100 / km)}")
        appendLine()
        appendLine("Figures come from phone sensors (GPS speed, gyroscope) in Biyahe Companion, a student project " +
            "(SENSORVERSE 2026). Individual riders are not identified.")
    }

    private fun coords(h: AdminHazard) = "%.5f, %.5f".format(Locale.US, h.lat, h.lon)
}
