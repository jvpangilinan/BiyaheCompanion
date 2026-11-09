package com.biyahe.companion.data.repository

import com.biyahe.companion.data.local.dao.SavedRouteDao
import com.biyahe.companion.data.local.entity.SavedRoute
import com.biyahe.companion.data.local.entity.Trip
import kotlinx.coroutines.flow.Flow

class SavedRouteRepository(private val dao: SavedRouteDao) {

    val routes: Flow<List<SavedRoute>> = dao.observeAll()

    suspend fun saveFromTrip(trip: Trip, name: String): Long =
        dao.insert(
            SavedRoute(
                name = name.trim().ifEmpty { "My route" },
                routeEncoded = trip.routeEncoded,
                distanceMeters = trip.distanceMeters
            )
        )

    suspend fun delete(id: Long) = dao.deleteById(id)
}
