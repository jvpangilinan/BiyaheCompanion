package com.biyahe.companion.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.biyahe.companion.R
import com.biyahe.companion.data.DemoData
import com.biyahe.companion.sync.CloudSync
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Admin tool: five made-up riders written to Firestore, so the dashboard looks like a real
 * community. They are clearly separated from real riders:
 *
 *   ids "sample-1" ... "sample-5", names ending in "(sample)", and a field sample = true
 *
 * The Firestore rules let an admin write ONLY to users whose id starts with "sample-", and
 * [remove] deletes all of them (and any reports sent about them) in one tap.
 */
object SampleRiders {
    private val riders = listOf(
        "andrea" to "Andrea C.", "miguel" to "Miguel S.", "joy" to "Joy R.", "paolo" to "Paolo D.", "kim" to "Kim L."
    )
    private fun uid(i: Int) = "sample-${i + 1}"

    /** @return how many trips and reports were written. */
    suspend fun add(context: Context, cloud: CloudSync): Pair<Int, Int> = withTimeout(60_000L) {
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val photos = listOf(R.drawable.still_roadside, R.drawable.road_hero).mapNotNull { photoBase64(context, it) }
        var tripCount = 0
        var hazardCount = 0
        riders.forEachIndexed { i, (username, name) ->
            val userDoc = db.collection("users").document(uid(i))
            val batch = db.batch()
            batch.set(userDoc, mapOf(
                "username" to "sample.$username", "name" to "$name (sample)", "role" to "user",
                "sample" to true, "lastLogin" to FieldValue.serverTimestamp()
            ))
            DemoData.sampleTrips(seed = 100 + i, now = now, count = 5 + i % 3).forEach { t ->
                batch.set(userDoc.collection("trips").document("trip_${t.startTime}"), cloud.tripToMap(t) + ("sample" to true))
                tripCount++
            }
            DemoData.sampleHazards(seed = 100 + i, now = now).forEachIndexed { k, h ->
                val photo = if (k == 0) photos.getOrNull(i % photos.size.coerceAtLeast(1)) else null
                batch.set(userDoc.collection("hazards").document("hazard_${h.timestamp}"), cloud.hazardToMap(h, photo) + ("sample" to true))
                hazardCount++
            }
            batch.commit().await()
        }
        tripCount to hazardCount
    }

    /** Deletes every sample rider, their trips and reports, and the agency-report records about them. */
    suspend fun remove(): Int = withTimeout(60_000L) {
        val db = FirebaseFirestore.getInstance()
        var deleted = 0
        riders.indices.forEach { i ->
            val userDoc = db.collection("users").document(uid(i))
            val batch = db.batch()
            listOf("trips", "hazards").forEach { name ->
                userDoc.collection(name).get().await().documents.forEach { batch.delete(it.reference); deleted++ }
            }
            batch.delete(userDoc)
            batch.commit().await()
        }
        db.collection("reports").get().await().documents
            .filter { (it.getString("key") ?: "").startsWith("sample-") }
            .forEach { it.reference.delete().await() }
        deleted
    }

    /** A bundled picture as a small JPEG in Base64 (the same way real hazard photos are stored). */
    private fun photoBase64(context: Context, resId: Int): String? = runCatching {
        val bmp = BitmapFactory.decodeResource(context.resources, resId, BitmapFactory.Options().apply { inSampleSize = 2 })
        val scale = 600f / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 60, it) }
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
