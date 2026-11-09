package com.biyahe.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biyahe.companion.data.local.entity.AppSettings
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.local.entity.SavedRoute
import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfileIfMissing(profile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIfMissing(settings: AppSettings)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfile?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?
}

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Trip>>

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE cloudSynced = 0 ORDER BY startTime")
    suspend fun getUnsynced(): List<Trip>

    @Query("UPDATE trips SET cloudSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Keeps only the newest [keep] trips that are already safe in the cloud. */
    @Query(
        "DELETE FROM trips WHERE cloudSynced = 1 AND id NOT IN " +
            "(SELECT id FROM trips ORDER BY startTime DESC LIMIT :keep)"
    )
    suspend fun pruneSyncedOlderThanNewest(keep: Int)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM trips")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(AVG(comfortScore), 0) FROM trips")
    fun observeAverageScore(): Flow<Double>

    @Query("SELECT COALESCE(SUM(distanceMeters), 0) FROM trips")
    fun observeTotalDistance(): Flow<Double>

    @Query("SELECT COALESCE(SUM(stepsBefore + stepsAfter), 0) FROM trips")
    fun observeTotalSteps(): Flow<Int>
}

@Dao
interface HazardDao {
    @Insert
    suspend fun insert(hazard: Hazard): Long

    @Update
    suspend fun update(hazard: Hazard)

    @Query("SELECT * FROM hazards ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Hazard>>

    @Query("SELECT * FROM hazards WHERE tripId = :tripId ORDER BY timestamp")
    fun observeForTrip(tripId: Long): Flow<List<Hazard>>

    @Query("SELECT * FROM hazards WHERE cloudSynced = 0 ORDER BY timestamp")
    suspend fun getUnsynced(): List<Hazard>

    @Query("UPDATE hazards SET cloudSynced = 1, photoUrl = :photoUrl WHERE id = :id")
    suspend fun markSynced(id: Long, photoUrl: String?)

    @Query("DELETE FROM hazards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM hazards WHERE id = :id")
    suspend fun getById(id: Long): Hazard?

    @Query("DELETE FROM hazards")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM hazards")
    suspend fun count(): Int
}

@Dao
interface SavedRouteDao {
    @Insert
    suspend fun insert(route: SavedRoute): Long

    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedRoute>>

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
