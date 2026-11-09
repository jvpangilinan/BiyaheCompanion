package com.biyahe.companion.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import android.util.Base64
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.repository.HazardRepository
import com.biyahe.companion.data.repository.SettingsRepository
import com.biyahe.companion.data.repository.TripRepository
import com.biyahe.companion.ui.hazards.decodeSampled
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the Hazards tab shows about the cloud. */
data class SyncState(
    val running: Boolean = false,
    val message: String = "",
    val lastSyncMs: Long = 0L
)

/**
 * Pushes trips and hazard reports (with photos) to Firebase.
 *
 *   SQLite  = recent trips + anything not uploaded yet
 *   Firestore = the full history   (users/{uid}/trips and users/{uid}/hazards; photos are stored inside the hazard document)
 *
 * Each row has a cloudSynced flag. Only unsynced rows are uploaded, and a row is only marked
 * synced after Firebase accepted it, so a failed upload is simply retried next time.
 * Document ids come from the start time / photo time, so uploading the same row twice is harmless.
 * Everything is stored under an anonymous Firebase user, so each phone only sees its own data.
 */
class CloudSync(
    private val context: Context,
    private val settings: SettingsRepository,
    private val trips: TripRepository,
    private val hazards: HazardRepository,
    private val scope: CoroutineScope,
    /** In demo mode nothing ever reaches Firestore, so sample data cannot mix with the real history. */
    private val demoMode: Boolean = false,
    /** True when a rider (not an admin, not demo) is logged in. */
    private val canSync: () -> Boolean = { true },
    /** Signs Firebase in as the current account if needed; returns its uid (null = not possible now). */
    private val ensureSession: suspend () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget; safe to call as often as you like (a sync already running is not doubled). */
    fun requestSync() {
        scope.launch { sync() }
    }

    suspend fun sync() {
        if (!mutex.tryLock()) return
        try {
            doSync()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun doSync() {
        if (demoMode) {
            say("Demo mode: sample data stays on this phone and is never uploaded.")
            return
        }
        if (!canSync()) {
            say("Log in as a rider to sync your trips.")
            return
        }
        if (!settings.currentSettings().cloudSyncEnabled) {
            say("Cloud sync is turned off.")
            return
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            say("Firebase is not set up yet (google-services.json is missing).")
            return
        }
        if (!isOnline()) {
            say("Offline. Reports are saved on this phone and will upload later.")
            return
        }

        _state.update { it.copy(running = true, message = "Syncing...") }
        var tripsUp = 0
        var hazardsUp = 0
        var failed = 0

        try {
            val uid = signedInUid()
            if (uid == null) {
                say("Couldn't reach your account. Uploads will retry when you're online.")
                return
            }
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid)

            for (t in trips.unsyncedTrips()) {
                val ok = timed {
                    userDoc.collection("trips").document("trip_${t.startTime}").set(tripToMap(t)).awaitTask()
                    true
                } == true
                if (ok) {
                    trips.markSynced(t.id)
                    tripsUp++
                } else failed++
            }

            for (h in hazards.unsyncedHazards()) {
                // The photo travels inside the hazard document as a small compressed JPEG (Base64 text),
                // so everything lives in Firestore and the free Spark plan is enough.
                val photo = encodePhoto(h.photoPath)
                // A photo exists on the phone but could not be prepared: keep the report as "waiting".
                if (photo == null && h.photoPath.isNotBlank() && File(h.photoPath).exists()) {
                    failed++
                    continue
                }
                val ok = timed {
                    userDoc.collection("hazards").document("hazard_${h.timestamp}").set(hazardToMap(h, photo)).awaitTask()
                    true
                } == true
                if (ok) {
                    hazards.markSynced(h.id, null)
                    hazardsUp++
                } else failed++
            }

            // SQLite keeps only the newest synced trips; Firebase has the rest.
            trips.keepOnlyRecentSynced(KEEP_RECENT_TRIPS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed++
        }

        val done = tripsUp + hazardsUp
        val msg = when {
            failed > 0 -> "Uploaded $done, $failed failed. Will retry."
            done == 0 -> "Everything is already synced."
            else -> "Uploaded $tripsUp trip(s) and $hazardsUp hazard report(s)."
        }
        _state.update { SyncState(running = false, message = msg, lastSyncMs = if (failed == 0) System.currentTimeMillis() else it.lastSyncMs) }
    }

    /**
     * Brings this account's trips and hazard reports down from the cloud (only ones not already on
     * the phone), then uploads anything new. This is what makes an account "keep" its data: log in
     * on another phone, or after reinstalling, and the history comes back.
     */
    fun restoreThenSync() {
        scope.launch {
            runCatching { restore() }
            sync()
        }
    }

    private suspend fun restore() {
        if (demoMode || !canSync() || FirebaseApp.getApps(context).isEmpty() || !isOnline()) return
        val uid = ensureSession() ?: return
        val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid)

        val haveTrips = trips.allTrips.first().map { it.startTime }.toSet()
        val cloudTrips = timed { userDoc.collection("trips").get().awaitTask().documents } ?: return
        for (d in cloudTrips) {
            val start = d.getLong("startTime") ?: continue
            if (start in haveTrips) continue
            trips.saveTrip(
                Trip(
                    startTime = start,
                    endTime = d.getLong("endTime") ?: start,
                    durationSec = d.getLong("durationSec") ?: 0L,
                    distanceMeters = d.getDouble("distanceMeters") ?: 0.0,
                    avgSpeedKmh = d.getDouble("avgSpeedKmh") ?: 0.0,
                    maxSpeedKmh = d.getDouble("maxSpeedKmh") ?: 0.0,
                    comfortScore = (d.getLong("comfortScore") ?: 0L).toInt(),
                    comfortLabel = d.getString("comfortLabel") ?: "",
                    bumpinessIndex = d.getDouble("bumpinessIndex") ?: 0.0,
                    noiseAvgDb = d.getDouble("noiseAvgDb") ?: 0.0,
                    noiseMaxDb = d.getDouble("noiseMaxDb") ?: 0.0,
                    rainWarning = d.getBoolean("rainWarning") ?: false,
                    stepsBefore = (d.getLong("stepsBefore") ?: 0L).toInt(),
                    stepsAfter = (d.getLong("stepsAfter") ?: 0L).toInt(),
                    bumpCount = (d.getLong("bumpCount") ?: 0L).toInt(),
                    sharpTurnCount = (d.getLong("sharpTurnCount") ?: 0L).toInt(),
                    speedWarnings = (d.getLong("speedWarnings") ?: 0L).toInt(),
                    overspeedSec = d.getLong("overspeedSec") ?: 0L,
                    weavingCount = (d.getLong("weavingCount") ?: 0L).toInt(),
                    tripMode = d.getString("tripMode") ?: "DRIVING",
                    riderRating = (d.getLong("riderRating") ?: 0L).toInt(),
                    routeEncoded = d.getString("routeEncoded") ?: "",
                    cloudSynced = true
                )
            )
        }

        val haveHazards = hazards.allHazards.first().map { it.timestamp }.toSet()
        val cloudHazards = timed { userDoc.collection("hazards").get().awaitTask().documents } ?: return
        for (d in cloudHazards) {
            val ts = d.getLong("timestamp") ?: continue
            if (ts in haveHazards) continue
            val photo = d.getString("photoBase64")?.let { b64 ->
                runCatching {
                    val f = File(File(context.filesDir, "photos").apply { mkdirs() }, "restored_$ts.jpg")
                    f.writeBytes(Base64.decode(b64, Base64.DEFAULT)); f.absolutePath
                }.getOrNull()
            } ?: ""
            hazards.saveHazard(
                Hazard(
                    type = d.getString("type") ?: "OTHER",
                    note = d.getString("note") ?: "",
                    photoPath = photo,
                    latitude = d.getDouble("latitude") ?: 0.0,
                    longitude = d.getDouble("longitude") ?: 0.0,
                    timestamp = ts,
                    cloudSynced = true
                )
            )
        }
    }

    /**
     * Removes one hazard report from the cloud (same document id the upload used). Best effort:
     * returns false when offline, and the caller then tells the user the cloud copy stays for now.
     */
    suspend fun deleteHazardInCloud(h: Hazard): Boolean {
        if (demoMode || !canSync() || !h.cloudSynced) return true
        if (FirebaseApp.getApps(context).isEmpty() || !isOnline()) return false
        val uid = ensureSession() ?: return false
        return timed {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("hazards").document("hazard_${h.timestamp}").delete().awaitTask()
            true
        } == true
    }

    /**
     * "Delete my trips and reports": the rider's cloud copies first (all or nothing), then the phone's.
     * Returns null on success, or why it could not be done (nothing is deleted then).
     */
    suspend fun deleteAllMyData(): String? {
        if (!demoMode && canSync()) {
            if (FirebaseApp.getApps(context).isEmpty() || !isOnline()) return "You're offline. Connect to the internet so the cloud copy is deleted too."
            val uid = ensureSession() ?: return "Couldn't reach your account. Try again when you're online."
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid)
            val ok = timed {
                for (name in listOf("trips", "hazards")) {
                    val docs = userDoc.collection(name).get().awaitTask().documents
                    docs.chunked(400).forEach { chunk ->
                        val batch = FirebaseFirestore.getInstance().batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().awaitTask()
                    }
                }
                true
            } == true
            if (!ok) return "The cloud copy could not be deleted. Nothing was removed; try again."
        }
        hazards.allHazards.first().forEach { h -> if (h.photoPath.isNotBlank()) runCatching { File(h.photoPath).delete() } }
        hazards.deleteAll()
        trips.deleteAll()
        say("All your trips and reports were deleted.")
        return null
    }

    /** "Add sample trips to my account": sample trips and reports, saved like real ones, then uploaded. */
    suspend fun addSamplesToMyAccount(): String {
        val now = System.currentTimeMillis()
        val seed = (now / 1000 % 10_000).toInt()
        val haveStarts = trips.allTrips.first().map { it.startTime }.toSet()
        var added = 0
        com.biyahe.companion.data.DemoData.sampleTrips(seed, now).filter { it.startTime !in haveStarts }.forEach { trips.saveTrip(it); added++ }
        val reports = com.biyahe.companion.data.DemoData.sampleHazards(seed, now)
        reports.forEach { hazards.saveHazard(it) }
        sync()
        return "Added $added sample trips and ${reports.size} sample reports" +
            if (demoMode) "." else ". They upload to your account like real ones."
    }

    private fun say(text: String) {
        _state.update { it.copy(running = false, message = text) }
    }

    private suspend fun signedInUid(): String? {
        // Uploads go to the logged-in account only (no more anonymous accounts since log-in exists).
        return ensureSession()
    }

    /**
     * Shrinks the photo (max 800 px, JPEG) and returns it as Base64 text, small enough for one Firestore
     * document (limit 1 MiB). Returns null if there is no photo file or it could not be read.
     */
    private suspend fun encodePhoto(path: String): String? = withContext(Dispatchers.IO) {
        if (path.isBlank() || !File(path).exists()) return@withContext null
        for ((maxSide, quality) in listOf(800 to 60, 600 to 45, 400 to 40)) {
            val bytes = runCatching {
                val bmp = decodeSampled(path, maxSide) ?: return@runCatching null
                val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                val small = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                } else bmp
                ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            }.getOrNull() ?: continue
            if (bytes.size <= MAX_PHOTO_BYTES) return@withContext Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        null
    }

    /** The Firestore shape of a trip (also used to write the admin's sample riders). */
    fun tripToMap(t: Trip): Map<String, Any?> = mapOf(
        "startTime" to t.startTime,
        "endTime" to t.endTime,
        "durationSec" to t.durationSec,
        "distanceMeters" to t.distanceMeters,
        "avgSpeedKmh" to t.avgSpeedKmh,
        "maxSpeedKmh" to t.maxSpeedKmh,
        "comfortScore" to t.comfortScore,
        "comfortLabel" to t.comfortLabel,
        "bumpinessIndex" to t.bumpinessIndex,
        "noiseAvgDb" to t.noiseAvgDb,
        "noiseMaxDb" to t.noiseMaxDb,
        "rainWarning" to t.rainWarning,
        "stepsBefore" to t.stepsBefore,
        "stepsAfter" to t.stepsAfter,
        "bumpCount" to t.bumpCount,
        "sharpTurnCount" to t.sharpTurnCount,
        "speedWarnings" to t.speedWarnings,
        "overspeedSec" to t.overspeedSec,
        "weavingCount" to t.weavingCount,
        "tripMode" to t.tripMode,
        "riderRating" to t.riderRating,
        "routeEncoded" to t.routeEncoded,
        "syncedAt" to FieldValue.serverTimestamp()
    )

    /** The Firestore shape of a hazard report. */
    fun hazardToMap(h: Hazard, photoBase64: String?): Map<String, Any?> = mapOf(
        "type" to h.type,
        "note" to h.note,
        "latitude" to h.latitude,
        "longitude" to h.longitude,
        "timestamp" to h.timestamp,
        "photoBase64" to photoBase64,
        "syncedAt" to FieldValue.serverTimestamp()
    )

    private fun isOnline(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: SecurityException) {
        true // no network-state permission: just try
    }

    /** Firestore waits forever when the connection drops mid-write, so every call gets a time limit. */
    private suspend fun <T> timed(block: suspend () -> T): T? =
        try {
            withTimeoutOrNull(OP_TIMEOUT_MS) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        const val KEEP_RECENT_TRIPS = 50
        private const val OP_TIMEOUT_MS = 30_000L
        private const val MAX_PHOTO_BYTES = 600_000 // keeps each hazard document well under Firestore's 1 MiB limit
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
