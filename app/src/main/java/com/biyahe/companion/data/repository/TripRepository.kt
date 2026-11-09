package com.biyahe.companion.data.repository

import com.biyahe.companion.data.local.dao.TripDao
import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.model.TripStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TripRepository(private val dao: TripDao) {

    val allTrips: Flow<List<Trip>> = dao.observeAll()
    fun recentTrips(limit: Int = 5): Flow<List<Trip>> = dao.observeRecent(limit)
    fun observeTrip(id: Long): Flow<Trip?> = dao.observeById(id)

    val stats: Flow<TripStats> = combine(
        dao.observeCount(),
        dao.observeAverageScore(),
        dao.observeTotalDistance(),
        dao.observeTotalSteps()
    ) { count, avg, distance, steps ->
        TripStats(count, avg, distance, steps)
    }

    /** Saves a finished trip and returns its new id. */
    suspend fun saveTrip(trip: Trip): Long = dao.insert(trip)

    suspend fun getTrip(id: Long): Trip? = dao.getById(id)
    suspend fun updateTrip(trip: Trip) = dao.update(trip)
    suspend fun deleteTrip(id: Long) = dao.deleteById(id)
    suspend fun count(): Int = dao.count()
    suspend fun deleteAll() = dao.deleteAll()

    // Cloud sync helpers (used in Section 8)
    suspend fun unsyncedTrips(): List<Trip> = dao.getUnsynced()
    suspend fun markSynced(id: Long) = dao.markSynced(id)
    suspend fun keepOnlyRecentSynced(keep: Int = 50) = dao.pruneSyncedOlderThanNewest(keep)
}
