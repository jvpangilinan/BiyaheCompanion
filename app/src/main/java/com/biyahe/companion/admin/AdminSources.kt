package com.biyahe.companion.admin

import com.biyahe.companion.data.repository.HazardRepository
import com.biyahe.companion.data.repository.TripRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/** Where the admin dashboard gets everyone's data. */
interface AdminSource {
    suspend fun load(): AdminSnapshot
    suspend fun recordReport(r: ReportRecord)
}

/**
 * REAL: every rider's data from Firestore.
 *
 *   users/{uid}                 profile (written at log-in)
 *   users/{uid}/trips/{id}      read with a collection-group query
 *   users/{uid}/hazards/{id}    read with a collection-group query
 *   reports/{key}               what the admins sent, and to whom
 *
 * Only works for admins: the security rules (notes/ADMIN_SETUP.md) refuse these reads to anyone
 * whose uid is not in admins/.
 */
class FirestoreAdminSource : AdminSource {
    private val db get() = FirebaseFirestore.getInstance()

    override suspend fun load(): AdminSnapshot = withTimeout(30_000L) {
        val profiles = db.collection("users").get().await().documents
        val trips = db.collectionGroup("trips").get().await().documents.map(::trip)
        val hazards = db.collectionGroup("hazards").get().await().documents.map(::hazard)
        val reports = db.collection("reports").get().await().documents.map {
            ReportRecord(it.getString("key") ?: it.id, it.getString("agency") ?: "", it.getLong("sentAtMs") ?: 0L, it.getString("sentBy") ?: "")
        }
        // Riders who synced before log-in existed have no profile document: list them anyway.
        val known = profiles
            .filter { it.getString("role") != "admin" }
            .map { AdminUser(it.id, it.getString("name") ?: "Rider", "@" + (it.getString("username") ?: it.id.take(6))) }
        val extra = (trips.map { it.uid } + hazards.map { it.uid }).distinct()
            .filter { uid -> known.none { it.uid == uid } }
            .map { AdminUser(it, "Rider ${it.take(6)}", "(synced before log-in existed)") }
        AdminSnapshot(known + extra, trips, hazards.sortedByDescending { it.timestamp }, reports)
    }

    override suspend fun recordReport(r: ReportRecord) {
        withTimeout(15_000L) {
            db.collection("reports").document(r.key.replace('/', '_')).set(
                mapOf("key" to r.key, "agency" to r.agency, "sentAtMs" to r.sentAtMs, "sentBy" to r.sentBy)
            ).await()
        }
    }

    private fun ownerOf(d: DocumentSnapshot) = d.reference.parent.parent?.id ?: "?"

    private fun trip(d: DocumentSnapshot) = AdminTrip(
        uid = ownerOf(d),
        startTime = d.getLong("startTime") ?: 0L,
        distanceKm = (d.getDouble("distanceMeters") ?: 0.0) / 1000.0,
        durationMin = (d.getLong("durationSec") ?: 0L) / 60,
        score = (d.getLong("comfortScore") ?: 0L).toInt(),
        label = d.getString("comfortLabel") ?: "",
        bumps = (d.getLong("bumpCount") ?: 0L).toInt(),
        speedWarnings = (d.getLong("speedWarnings") ?: 0L).toInt(),
        overspeedSec = d.getLong("overspeedSec") ?: 0L,
        weaving = (d.getLong("weavingCount") ?: 0L).toInt(),
        rating = (d.getLong("riderRating") ?: 0L).toInt(),
        mode = d.getString("tripMode") ?: "DRIVING"
    )

    private fun hazard(d: DocumentSnapshot) = AdminHazard(
        key = "${ownerOf(d)}/${d.id}",
        uid = ownerOf(d),
        type = d.getString("type") ?: "OTHER",
        note = d.getString("note") ?: "",
        lat = d.getDouble("latitude") ?: 0.0,
        lon = d.getDouble("longitude") ?: 0.0,
        timestamp = d.getLong("timestamp") ?: 0L,
        photoBase64 = d.getString("photoBase64")
    )
}

/**
 * DEMO: this phone's sample data as "Demo Rider", plus five generated riders so the dashboard
 * looks like a real community on stage. Nothing leaves the phone; sent reports live in memory.
 */
class DemoAdminSource(private val trips: TripRepository, private val hazards: HazardRepository) : AdminSource {

    override suspend fun load(): AdminSnapshot {
        val me = AdminUser("demo-user", "Rider", "@user")
        val myTrips = trips.allTrips.first().map {
            AdminTrip(me.uid, it.startTime, it.distanceMeters / 1000.0, it.durationSec / 60, it.comfortScore, it.comfortLabel,
                it.bumpCount, it.speedWarnings, it.overspeedSec, it.weavingCount, it.riderRating, it.tripMode)
        }
        val myHazards = hazards.allHazards.first().map {
            AdminHazard("${me.uid}/${it.id}", me.uid, it.type, it.note, it.latitude, it.longitude, it.timestamp,
                photoPath = it.photoPath.ifBlank { null })
        }
        val others = listOf(
            AdminUser("demo-r1", "Andrea C.", "@andrea"),
            AdminUser("demo-r2", "Miguel S.", "@miguel"),
            AdminUser("demo-r3", "Joy R.", "@joy"),
            AdminUser("demo-r4", "Paolo D.", "@paolo"),
            AdminUser("demo-r5", "Kim L.", "@kim")
        )
        val now = System.currentTimeMillis()
        val rnd = Random(2026)
        val otherTrips = others.flatMap { u ->
            val careful = rnd.nextDouble()
            (0 until 3 + rnd.nextInt(6)).map { i ->
                val score = (45 + rnd.nextInt(50))
                AdminTrip(
                    u.uid, now - (i + 1) * 86_400_000L - rnd.nextLong(3_600_000L * 10),
                    3.0 + rnd.nextDouble() * 15, 15L + rnd.nextInt(50), score,
                    when { score >= 80 -> "Smooth"; score >= 60 -> "Fair"; else -> "Rough" },
                    rnd.nextInt(30),
                    if (careful > 0.6) rnd.nextInt(5) else rnd.nextInt(2),
                    if (careful > 0.6) rnd.nextLong(300) else rnd.nextLong(40),
                    rnd.nextInt(if (careful > 0.7) 9 else 3),
                    // How it felt tracks the score, with some human noise (as real ratings do).
                    rating = (score / 20 + rnd.nextInt(3) - 1).coerceIn(1, 5),
                    mode = if (rnd.nextInt(3) == 0) "RIDING" else "DRIVING"
                )
            }
        }
        val spots = listOf(
            Triple("POTHOLE", "Wide pothole on the right lane", 14.6407 to 121.0402),
            Triple("FLOODING", "Knee-deep flood near the underpass", 14.6011 to 120.9887),
            Triple("ROAD_DAMAGE", "Sunken pavement after road works", 14.5547 to 121.0244),
            Triple("OBSTRUCTION", "Stalled truck blocking a lane", 14.5795 to 121.0560),
            Triple("POTHOLE", "Series of potholes at the bus stop", 14.5378 to 121.0014),
            Triple("FLOODING", "Street flooded after heavy rain", 14.6760 to 121.0437)
        )
        val otherHazards = spots.mapIndexed { i, (type, note, ll) ->
            val u = others[i % others.size]
            AdminHazard("${u.uid}/h$i", u.uid, type, note, ll.first, ll.second, now - (i + 1) * 43_200_000L)
        }
        return AdminSnapshot(
            listOf(me) + others,
            myTrips + otherTrips,
            (myHazards + otherHazards).sortedByDescending { it.timestamp },
            sent.toList()
        )
    }

    override suspend fun recordReport(r: ReportRecord) { sent += r }

    private companion object {
        val sent = mutableListOf<ReportRecord>()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
