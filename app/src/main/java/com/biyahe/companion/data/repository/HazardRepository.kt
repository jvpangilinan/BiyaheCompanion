package com.biyahe.companion.data.repository

import com.biyahe.companion.data.local.dao.HazardDao
import com.biyahe.companion.data.local.entity.Hazard
import kotlinx.coroutines.flow.Flow

class HazardRepository(private val dao: HazardDao) {

    val allHazards: Flow<List<Hazard>> = dao.observeAll()
    fun hazardsForTrip(tripId: Long): Flow<List<Hazard>> = dao.observeForTrip(tripId)

    suspend fun saveHazard(hazard: Hazard): Long = dao.insert(hazard)
    suspend fun deleteHazard(id: Long) = dao.deleteById(id)
    suspend fun getHazard(id: Long): Hazard? = dao.getById(id)
    suspend fun updateHazard(hazard: Hazard) = dao.update(hazard)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun count(): Int = dao.count()

    // Cloud sync helpers (used in Section 8)
    suspend fun unsyncedHazards(): List<Hazard> = dao.getUnsynced()
    suspend fun markSynced(id: Long, photoUrl: String?) = dao.markSynced(id, photoUrl)
}
