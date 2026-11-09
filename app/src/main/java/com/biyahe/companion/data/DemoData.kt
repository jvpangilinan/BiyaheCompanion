package com.biyahe.companion.data

import android.content.Context
import com.biyahe.companion.R
import com.biyahe.companion.data.local.BiyaheDatabase
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.model.HazardType
import com.biyahe.companion.data.model.RouteCodec
import com.biyahe.companion.data.model.RoutePoint
import com.biyahe.companion.data.repository.HazardRepository
import com.biyahe.companion.data.repository.TripRepository
import com.biyahe.companion.scoring.ComfortScoreCalculator
import kotlin.random.Random
import java.io.File

/**
 * Sample data for DEMO MODE only (see DemoMode). It is written into the separate demo database
 * (biyahe_demo.db), never into the real one, and demo mode never uploads to Firestore.
 * Seeds once, when the demo database has no trips.
 */
object DemoData {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    // ---- Routes (Metro Manila) -------------------------------------------------------------------

    /** EDSA southbound: Cubao -> Ortigas -> Guadalupe -> Ayala. */
    private val edsa = listOf(
        RoutePoint(14.6196, 121.0509), RoutePoint(14.6140, 121.0535), RoutePoint(14.6076, 121.0560),
        RoutePoint(14.5975, 121.0575), RoutePoint(14.5873, 121.0567), RoutePoint(14.5790, 121.0560),
        RoutePoint(14.5720, 121.0510), RoutePoint(14.5663, 121.0455), RoutePoint(14.5600, 121.0350),
        RoutePoint(14.5547, 121.0244)
    )

    /** Taft Avenue northbound: Baclaran -> Gil Puyat -> Vito Cruz -> Pedro Gil -> Lawton. */
    private val taft = listOf(
        RoutePoint(14.5340, 120.9983), RoutePoint(14.5410, 120.9986), RoutePoint(14.5477, 120.9985),
        RoutePoint(14.5543, 120.9970), RoutePoint(14.5636, 120.9946), RoutePoint(14.5703, 120.9916),
        RoutePoint(14.5765, 120.9881), RoutePoint(14.5826, 120.9846), RoutePoint(14.5929, 120.9816)
    )

    /** Quezon Avenue westbound: Quezon Memorial Circle -> Timog -> Banawe -> Welcome Rotonda. */
    private val quezonAve = listOf(
        RoutePoint(14.6514, 121.0493), RoutePoint(14.6470, 121.0440), RoutePoint(14.6420, 121.0386),
        RoutePoint(14.6360, 121.0300), RoutePoint(14.6320, 121.0225), RoutePoint(14.6290, 121.0150),
        RoutePoint(14.6240, 121.0060), RoutePoint(14.6185, 120.9990)
    )

    /** Ortigas Avenue eastbound: Ortigas Center -> Santolan -> Cainta junction. */
    private val ortigasAve = listOf(
        RoutePoint(14.5872, 121.0614), RoutePoint(14.5878, 121.0700), RoutePoint(14.5880, 121.0800),
        RoutePoint(14.5880, 121.0885), RoutePoint(14.5878, 121.0970), RoutePoint(14.5872, 121.1060),
        RoutePoint(14.5866, 121.1150)
    )

    /** Bump this whenever the sample data changes; the demo database is then wiped and refilled. */
    private const val SEED_VERSION = 5

    /**
     * Fills the DEMO database. If it holds an older sample set, it is cleared first. Only ever
     * called with the demo database (BiyaheApp checks), so real data can never be wiped here.
     */
    suspend fun seedDemo(context: Context, db: BiyaheDatabase, trips: TripRepository, hazards: HazardRepository) {
        val prefs = context.getSharedPreferences("biyahe_mode", Context.MODE_PRIVATE)
        if (prefs.getInt("demo_seed_version", 0) != SEED_VERSION) {
            db.clearAllTables()
            prefs.edit().putInt("demo_seed_version", SEED_VERSION).apply()
        }
        if (trips.count() > 0) return
        val now = System.currentTimeMillis()

        // Scores are worked out by the real ComfortScoreCalculator from these inputs, so the
        // "Why this score" card always adds up: 3 Smooth (82, 90, 84), 3 Fair (66, 71, 75), 2 Rough (46, 52).
        val edsaMorning = trips.saveTrip(trip(1, now - 1 * DAY - 2 * HOUR, 41, 9800.0, 14.3, 44.0, 0.9, 68.0, 82.0, 5, 3, edsa, rain = false, steps = 420 to 610))
        trips.saveTrip(trip(2, now - 3 * HOUR, 13, 9800.0, 45.2, 78.0, 0.6, 63.0, 74.0, 1, 1, edsa, rain = false, steps = 300 to 380)
            .copy(speedWarnings = 2, overspeedSec = 95)) // Fast Run, and over EDSA's 60 km/h twice
        val taftRough = trips.saveTrip(trip(3, now - 2 * DAY - 5 * HOUR, 52, 7000.0, 8.1, 33.0, 2.3, 80.0, 96.0, 30, 12, taft, rain = true, steps = 380 to 540))
        trips.saveTrip(trip(4, now - 4 * DAY - 1 * HOUR, 38, 7000.0, 11.1, 38.0, 1.5, 74.0, 90.0, 12, 6, taft.reversed(), rain = false, steps = 250 to 470))
        val quezon = trips.saveTrip(trip(5, now - 5 * DAY - 3 * HOUR, 29, 6600.0, 13.7, 52.0, 1.3, 71.0, 88.0, 8, 5, quezonAve, rain = true, steps = 510 to 330))
        trips.saveTrip(trip(6, now - 6 * DAY - 6 * HOUR, 24, 6600.0, 16.5, 55.0, 0.8, 66.0, 84.0, 3, 3, quezonAve.reversed(), rain = false, steps = 290 to 410)
            .copy(speedWarnings = 1, overspeedSec = 22))
        val ortigas = trips.saveTrip(trip(7, now - 8 * DAY - 2 * HOUR, 47, 5900.0, 7.5, 29.0, 2.1, 78.0, 93.0, 24, 10, ortigasAve, rain = false, steps = 640 to 220))
        trips.saveTrip(trip(8, now - 9 * DAY - 4 * HOUR, 33, 9800.0, 17.8, 49.0, 1.2, 70.0, 86.0, 7, 4, edsa.reversed(), rain = false, steps = 360 to 500))

        // Two reports get photos copied from the app's own images, so the Hazards tab is not all grey boxes.
        val photoA = copyDemoPhoto(context, R.drawable.still_roadside, "demo_hazard_1.jpg")
        val photoB = copyDemoPhoto(context, R.drawable.road_hero, "demo_hazard_2.jpg")

        hazards.saveHazard(Hazard(tripId = taftRough, type = HazardType.POTHOLE.name, note = "Deep pothole in the outer lane near Vito Cruz station",
            photoPath = photoA, latitude = 14.5636, longitude = 120.9946, timestamp = now - 2 * DAY - 5 * HOUR + 22 * MINUTE))
        hazards.saveHazard(Hazard(tripId = quezon, type = HazardType.FLOODING.name, note = "Ankle-deep flood after the afternoon rain",
            photoPath = photoB, latitude = 14.6320, longitude = 121.0225, timestamp = now - 5 * DAY - 3 * HOUR + 15 * MINUTE))
        hazards.saveHazard(Hazard(tripId = edsaMorning, type = HazardType.ROAD_DAMAGE.name, note = "Cracked concrete slabs before the Ortigas flyover",
            latitude = 14.5873, longitude = 121.0567, timestamp = now - 1 * DAY - 2 * HOUR + 18 * MINUTE))
        hazards.saveHazard(Hazard(tripId = ortigas, type = HazardType.OBSTRUCTION.name, note = "Road works block one lane, jeepneys merge suddenly",
            latitude = 14.5880, longitude = 121.0885, timestamp = now - 8 * DAY - 2 * HOUR + 25 * MINUTE))
        hazards.saveHazard(Hazard(tripId = taftRough, type = HazardType.POTHOLE.name, note = "Series of small potholes by the Libertad stop",
            latitude = 14.5477, longitude = 120.9985, timestamp = now - 2 * DAY - 5 * HOUR + 9 * MINUTE))
    }

    /**
     * A believable set of trips for one sample rider: the four Metro Manila routes, mixed comfort,
     * a little speeding now and then, star ratings close to the score. Same [seed] = same trips.
     * Used by "Add sample trips to my account" and by the admin's sample riders.
     */
    fun sampleTrips(seed: Int, now: Long, count: Int = 6): List<Trip> {
        val rnd = Random(seed)
        val routes = listOf(edsa, taft, quezonAve, ortigasAve)
        return (0 until count).map { i ->
            val base = routes[(seed + i) % routes.size]
            val route = if (rnd.nextBoolean()) base else base.reversed()
            val bump = 0.6 + rnd.nextDouble() * 1.9
            val minutes = 15 + rnd.nextInt(45)
            val km = 4.0 + rnd.nextDouble() * 8
            val avg = km / (minutes / 60.0)
            val noise = 62.0 + rnd.nextDouble() * 18
            val warnings = if (rnd.nextInt(3) == 0) 1 + rnd.nextInt(3) else 0
            trip(
                seed * 10 + i, now - (i + 1) * DAY - rnd.nextLong(10 * HOUR), minutes, km * 1000, avg, avg * 1.8,
                bump, noise, noise + 12, (bump * 6).toInt() + rnd.nextInt(8), rnd.nextInt(10), route,
                rain = rnd.nextInt(5) == 0, steps = (200 + rnd.nextInt(400)) to (200 + rnd.nextInt(400)), weaving = rnd.nextInt(6)
            ).copy(
                speedWarnings = warnings,
                overspeedSec = warnings * (20L + rnd.nextInt(60)),
                tripMode = if (rnd.nextInt(3) == 0) "RIDING" else "DRIVING"
            )
        }
    }

    /** Two or three hazard reports for one sample rider (no photo file; the admin samples add photos in the cloud). */
    fun sampleHazards(seed: Int, now: Long): List<Hazard> {
        val spots = listOf(
            Triple(HazardType.POTHOLE, "Wide pothole on the right lane", 14.6407 to 121.0402),
            Triple(HazardType.FLOODING, "Knee-deep flood near the underpass", 14.6011 to 120.9887),
            Triple(HazardType.ROAD_DAMAGE, "Sunken pavement after road works", 14.5547 to 121.0244),
            Triple(HazardType.OBSTRUCTION, "Stalled truck blocking a lane", 14.5795 to 121.0560),
            Triple(HazardType.POTHOLE, "Series of potholes at the bus stop", 14.5378 to 121.0014),
            Triple(HazardType.FLOODING, "Street flooded after heavy rain", 14.6760 to 121.0437)
        )
        val rnd = Random(seed)
        return (0 until 2 + rnd.nextInt(2)).map { i ->
            val (type, note, ll) = spots[(seed + i * 2) % spots.size]
            Hazard(type = type.name, note = note, latitude = ll.first, longitude = ll.second,
                timestamp = now - (i + 1) * 30 * HOUR - rnd.nextLong(5 * HOUR))
        }
    }

    private fun trip(
        seed: Int, start: Long, minutes: Int, meters: Double, avgKmh: Double, maxKmh: Double,
        bumpiness: Double, noiseAvg: Double, noiseMax: Double,
        bumps: Int, turns: Int, route: List<RoutePoint>, rain: Boolean, steps: Pair<Int, Int>,
        weaving: Int = turns / 2
    ): Trip {
        val score = ComfortScoreCalculator.breakdown(bumpiness, bumps, turns, noiseAvg, minutes * MINUTE).score
        return Trip(
        startTime = start,
        endTime = start + minutes * MINUTE,
        durationSec = minutes * 60L,
        distanceMeters = meters,
        avgSpeedKmh = avgKmh,
        maxSpeedKmh = maxKmh,
        comfortScore = score,
        comfortLabel = when {
            score >= 80 -> "Smooth"
            score >= 60 -> "Fair"
            else -> "Rough"
        },
        bumpinessIndex = bumpiness,
        noiseAvgDb = noiseAvg,
        noiseMaxDb = noiseMax,
        rainWarning = rain,
        stepsBefore = steps.first,
        stepsAfter = steps.second,
        bumpCount = bumps,
        sharpTurnCount = turns,
        weavingCount = weaving,
        // Rider's own 1-5 rating, close to the score (for the "score vs feeling" chart); every 3rd trip a passenger ride.
        riderRating = (score / 20 + listOf(0, 1, -1, 0)[seed % 4]).coerceIn(1, 5),
        tripMode = if (seed % 3 == 0) "RIDING" else "DRIVING",
        routeEncoded = RouteCodec.encode(roughen(route, bumpiness, bumps, Random(seed)))
    )
    }

    /**
     * Makes a believable roughness map: adds in-between points, gives each stretch a vibration
     * level around the trip's Bumpiness Index (with one rougher patch), and places the big bumps
     * mostly on the roughest stretches.
     */
    private fun roughen(base: List<RoutePoint>, bumpiness: Double, bumps: Int, rnd: Random): List<RoutePoint> {
        val dense = ArrayList<RoutePoint>()
        for (i in 0 until base.size - 1) {
            val a = base[i]; val b = base[i + 1]
            for (k in 0 until 5) {
                val t = k / 5.0
                dense += RoutePoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
        }
        dense += base.last()

        val patchStart = rnd.nextInt(dense.size / 2)
        val patch = patchStart..(patchStart + dense.size / 5)
        val rough = dense.indices.map { i ->
            val wobble = 0.6 + rnd.nextDouble() * 0.7
            (bumpiness * wobble * if (i in patch) 1.6 else 1.0).coerceIn(0.2, 3.5)
        }
        val bumpAt = IntArray(dense.size)
        val weights = rough.map { it * it }
        val total = weights.sum()
        repeat(bumps) {
            var pick = rnd.nextDouble() * total
            var idx = 0
            while (idx < weights.lastIndex && pick > weights[idx]) { pick -= weights[idx]; idx++ }
            bumpAt[idx]++
        }
        return dense.mapIndexed { i, p ->
            p.copy(roughness = if (i == 0) null else rough[i], bumps = bumpAt[i])
        }
    }

    /** Copies a bundled image into app storage so it works like a real hazard photo file. */
    private fun copyDemoPhoto(context: Context, resId: Int, name: String): String = runCatching {
        val file = File(File(context.filesDir, "demo_photos").apply { mkdirs() }, name)
        if (!file.exists()) {
            context.resources.openRawResource(resId).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        file.absolutePath
    }.getOrDefault("")
}
