package com.biyahe.companion.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.biyahe.companion.data.model.HazardType

/** Single-row table (id is always 1): the commuter's profile. */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Commuter",
    val createdAt: Long = System.currentTimeMillis()
)

/** Single-row table (id is always 1): app settings. */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val fingerprintLockEnabled: Boolean = true,
    val autoBrightness: Boolean = true,        // ambient light sensor
    val pauseScreenInPocket: Boolean = true,   // proximity sensor
    val rainWarningEnabled: Boolean = true,    // barometer
    val cloudSyncEnabled: Boolean = true
)

/** A route the commuter saved so they can compare future trips. */
@Entity(tableName = "saved_routes")
data class SavedRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val routeEncoded: String,
    val distanceMeters: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/** One recorded commute. */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSec: Long,
    val distanceMeters: Double,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val comfortScore: Int,            // 0..100
    val comfortLabel: String,         // Smooth / Fair / Rough
    val bumpinessIndex: Double,       // from accelerometer + gyroscope
    val noiseAvgDb: Double,           // from microphone
    val noiseMaxDb: Double,
    val rainWarning: Boolean = false, // barometer drop detected
    val stepsBefore: Int = 0,         // walking before the ride
    val stepsAfter: Int = 0,          // walking after the ride
    val bumpCount: Int = 0,           // big bumps felt (accelerometer)
    val sharpTurnCount: Int = 0,      // sharp turns / swerves (gyroscope)
    @ColumnInfo(defaultValue = "0")
    val speedWarnings: Int = 0,       // speed limit warnings given (GPS)       [added in DB version 3]
    @ColumnInfo(defaultValue = "0")
    val overspeedSec: Long = 0,       // seconds spent over the speed limit     [added in DB version 3]
    @ColumnInfo(defaultValue = "0")
    val weavingCount: Int = 0,        // lane changes / swerves (gyroscope)      [added in DB version 4]
    @ColumnInfo(defaultValue = "DRIVING")
    val tripMode: String = "DRIVING", // DRIVING or RIDING                       [added in DB version 5]
    @ColumnInfo(defaultValue = "0")
    val riderRating: Int = 0,         // 1-5 stars from the rider, 0 = not rated  [added in DB version 5]
    val routeEncoded: String = "",
    val cloudSynced: Boolean = false
) {
    val totalSteps: Int get() = stepsBefore + stepsAfter
}

/** A photographed road problem, tagged with GPS location. */
@Entity(
    tableName = "hazards",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("tripId")]
)
data class Hazard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val type: String = HazardType.OTHER.name,
    val note: String = "",
    val photoPath: String = "",       // local file
    val photoUrl: String? = null,     // Firebase Storage URL once uploaded
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val cloudSynced: Boolean = false
)
