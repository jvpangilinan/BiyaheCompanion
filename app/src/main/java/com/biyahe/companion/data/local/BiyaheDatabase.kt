package com.biyahe.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biyahe.companion.data.local.dao.HazardDao
import com.biyahe.companion.data.local.dao.SavedRouteDao
import com.biyahe.companion.data.local.dao.TripDao
import com.biyahe.companion.data.local.dao.UserDao
import com.biyahe.companion.data.local.entity.AppSettings
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.local.entity.SavedRoute
import com.biyahe.companion.data.local.entity.Trip
import com.biyahe.companion.data.local.entity.UserProfile

/**
 * The offline SQLite store: user profile, settings, saved routes,
 * recent trips (and hazards waiting to be uploaded).
 */
@Database(
    entities = [UserProfile::class, AppSettings::class, SavedRoute::class, Trip::class, Hazard::class],
    version = 5,
    exportSchema = false
)
abstract class BiyaheDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun tripDao(): TripDao
    abstract fun hazardDao(): HazardDao
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        /** Speed limit warning: two new trip columns, existing trips get 0. */
        /** Driving / riding and the rider's star rating. Existing trips: DRIVING, not rated. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN tripMode TEXT NOT NULL DEFAULT 'DRIVING'")
                db.execSQL("ALTER TABLE trips ADD COLUMN riderRating INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Lane weaving: one new trip column, existing trips get 0. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN weavingCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN speedWarnings INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN overspeedSec INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** [fileName] comes from AccountDb: one file per rider account, plus the demo file. */
        fun build(context: Context, fileName: String): BiyaheDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BiyaheDatabase::class.java,
                fileName
            )
                // 2 -> 3 -> 4 keep every trip (speed columns, then weaving). Older versions are still wiped.
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
    }
}
