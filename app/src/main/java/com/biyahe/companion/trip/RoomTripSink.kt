// ---------------------------------------------------------------------------
// RoomTripSink: saves AND reads trips through the Section 2 TripRepository.
// Written against the real Trip entity (data/local/entity/Entities.kt).
//
// Two translations happen here:
//  * Units/names: CompletedTrip uses km, ms and "...Index"; Room's Trip uses
//    meters, seconds and its own column names.
//  * Routes: the recorder uses Polyline text, Room stores "lat,lng;lat,lng;..."
//    (RouteCodec). Routes are converted both ways so the map still draws.
// ---------------------------------------------------------------------------
package com.biyahe.companion.trip

import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.model.RouteCodec
import com.biyahe.companion.data.model.RoutePoint
import com.biyahe.companion.data.repository.TripRepository
import com.biyahe.companion.sensors.BumpLevel
import com.biyahe.companion.tracking.LatLon
import com.biyahe.companion.tracking.NoiseLevel
import com.biyahe.companion.tracking.Polyline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTripSink(private val repository: TripRepository) : TripSink {

    override suspend fun save(trip: CompletedTrip): Long =
        repository.saveTrip(toEntity(trip))

    override suspend fun updateStepsAfter(tripId: Long, stepsAfter: Int) {
        val current = repository.getTrip(tripId) ?: return
        repository.updateTrip(current.copy(stepsAfter = stepsAfter, cloudSynced = false)) // re-upload with the new steps
    }

    override fun observeTrips(): Flow<List<StoredTrip>> =
        repository.allTrips.map { list -> list.map { StoredTrip(it.id, toCompleted(it)) } }

    override fun observeTrip(id: Long): Flow<StoredTrip?> =
        repository.observeTrip(id).map { t -> t?.let { StoredTrip(it.id, toCompleted(it)) } }

    private fun toEntity(t: CompletedTrip) = Trip(
        startTime = t.startedAtMs,
        endTime = t.endedAtMs,
        durationSec = t.durationMs / 1000,
        distanceMeters = t.distanceKm * 1000.0,
        avgSpeedKmh = t.avgSpeedKmh,
        maxSpeedKmh = t.maxSpeedKmh,
        comfortScore = t.comfortScore,
        comfortLabel = t.comfortLabel,
        bumpinessIndex = t.bumpinessIndex,
        noiseAvgDb = t.avgNoiseDb,
        noiseMaxDb = t.avgNoiseDb,              // CompletedTrip has no peak noise yet
        rainWarning = t.rainRisk != "None",
        stepsBefore = t.stepsBefore,
        stepsAfter = t.stepsAfter,
        bumpCount = t.bumpCount,
        sharpTurnCount = t.sharpTurnCount,
        speedWarnings = t.speedWarnings,
        overspeedSec = t.overspeedSec,
        weavingCount = t.weavingCount,
        tripMode = t.tripMode,
        riderRating = t.riderRating,
        // Prefer the route with roughness; fall back to the plain polyline.
        routeEncoded = RouteCodec.encode(
            t.roughRoute.ifEmpty { Polyline.decode(t.routePolyline).map { RoutePoint(it.lat, it.lon) } }
        )
    )

    private fun toCompleted(e: Trip): CompletedTrip {
        val route = RouteCodec.decode(e.routeEncoded)
        return CompletedTrip(
        startedAtMs = e.startTime,
        endedAtMs = e.endTime,
        durationMs = e.durationSec * 1000,
        distanceKm = e.distanceMeters / 1000.0,
        avgSpeedKmh = e.avgSpeedKmh,
        maxSpeedKmh = e.maxSpeedKmh,
        comfortScore = e.comfortScore,
        comfortLabel = e.comfortLabel,
        bumpinessIndex = e.bumpinessIndex,
        bumpLevel = BumpLevel.from(e.bumpinessIndex).label,
        bumpCount = e.bumpCount,
        sharpTurnCount = e.sharpTurnCount,
        avgNoiseDb = e.noiseAvgDb,
        noiseLevel = NoiseLevel.from(e.noiseAvgDb).label,
        rainRisk = if (e.rainWarning) "Possible" else "None",
        stepsBefore = e.stepsBefore,
        stepsAfter = e.stepsAfter,
        routePolyline = Polyline.encode(route.map { LatLon(it.latitude, it.longitude) }),
        roughRoute = route,
        speedWarnings = e.speedWarnings,
        overspeedSec = e.overspeedSec,
        weavingCount = e.weavingCount,
        tripMode = e.tripMode,
        riderRating = e.riderRating
    )
    }
}
