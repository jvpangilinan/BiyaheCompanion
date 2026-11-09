package com.biyahe.companion.trip

import com.biyahe.companion.tracking.LatLon
import com.biyahe.companion.tracking.Polyline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A trip plus the id it was stored under. */
data class StoredTrip(val id: Long, val trip: CompletedTrip)

/**
 * Where finished trips are written AND read from. Every screen (Home, History, Summary)
 * talks to this interface only, so none of them depends on the exact Room table shape.
 *
 *   InMemoryTripSink  -> used now (starts with 2 demo trips; everything vanishes when the app closes)
 *   RoomTripSink      -> see RoomTripSink.kt.template: SQLite via the Section 2 repository
 *   Section 8         -> also pushes to Firebase
 */
interface TripSink {
    /** @return the new trip's id. */
    suspend fun save(trip: CompletedTrip): Long

    /** Walking after the ride is only known some minutes later. */
    suspend fun updateStepsAfter(tripId: Long, stepsAfter: Int)

    /** All trips, newest first. Emits again whenever anything changes. */
    fun observeTrips(): Flow<List<StoredTrip>>

    /** One trip (null if it does not exist). Emits again when it changes, e.g. when steps-after arrive. */
    fun observeTrip(id: Long): Flow<StoredTrip?>
}

class InMemoryTripSink(seedDemo: Boolean = true) : TripSink {
    private val _trips = MutableStateFlow<Map<Long, CompletedTrip>>(emptyMap())
    val trips: StateFlow<Map<Long, CompletedTrip>> = _trips.asStateFlow()
    private var nextId = 1L

    init {
        if (seedDemo) demoTrips().forEach { t -> _trips.update { it + (nextId++ to t) } }
    }

    override suspend fun save(trip: CompletedTrip): Long {
        val id = synchronized(this) { nextId++ }
        _trips.update { it + (id to trip) }
        return id
    }

    override suspend fun updateStepsAfter(tripId: Long, stepsAfter: Int) {
        _trips.update { map ->
            val t = map[tripId] ?: return@update map
            map + (tripId to t.copy(stepsAfter = stepsAfter))
        }
    }

    override fun observeTrips(): Flow<List<StoredTrip>> = _trips.map { map ->
        map.map { (id, t) -> StoredTrip(id, t) }.sortedByDescending { it.trip.startedAtMs }
    }

    override fun observeTrip(id: Long): Flow<StoredTrip?> = _trips.map { map -> map[id]?.let { StoredTrip(id, it) } }

    private fun demoTrips(): List<CompletedTrip> {
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val route = Polyline.encode(
            listOf(
                LatLon(14.4166, 121.0399), LatLon(14.4190, 121.0418), LatLon(14.4207, 121.0431),
                LatLon(14.4250, 121.0455), LatLon(14.4302, 121.0490)
            )
        )
        return listOf(
            CompletedTrip(
                startedAtMs = now - 3 * day, endedAtMs = now - 3 * day + 28 * 60_000L, durationMs = 28 * 60_000L,
                distanceKm = 7.4, avgSpeedKmh = 15.9, maxSpeedKmh = 41.0,
                comfortScore = 58, comfortLabel = "Rough", bumpinessIndex = 1.9, bumpLevel = "Medium",
                bumpCount = 31, sharpTurnCount = 14, avgNoiseDb = 79.0, noiseLevel = "High",
                rainRisk = "None", stepsBefore = 410, stepsAfter = 355, routePolyline = route
            ),
            CompletedTrip(
                startedAtMs = now - 1 * day, endedAtMs = now - 1 * day + 22 * 60_000L, durationMs = 22 * 60_000L,
                distanceKm = 6.8, avgSpeedKmh = 18.5, maxSpeedKmh = 38.0,
                comfortScore = 86, comfortLabel = "Smooth", bumpinessIndex = 0.8, bumpLevel = "Low",
                bumpCount = 4, sharpTurnCount = 3, avgNoiseDb = 70.0, noiseLevel = "Medium",
                rainRisk = "Possible", stepsBefore = 520, stepsAfter = 480, routePolyline = route
            )
        )
    }
}
