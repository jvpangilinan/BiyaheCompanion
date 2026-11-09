package com.biyahe.companion.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * GPS fixes as a cold Flow (same style as SensorHub): the GPS chip only runs while
 * something collects the Flow. Uses the platform LocationManager, so it needs no
 * Google Play Services and no extra Gradle dependency.
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** false if the user switched Location off in the quick settings. */
    fun isGpsEnabled(): Boolean = try {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: Exception) {
        false
    }

    /** One fix per second while collected. Completes without emitting if permission is missing. */
    fun points(): Flow<TrackPoint> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toTrackPoint())
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            // Required on Android 10 and older; never called on newer versions.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        } catch (_: IllegalArgumentException) { // phone has no GPS provider
            close()
            return@callbackFlow
        }
        awaitClose { manager.removeUpdates(listener) }
    }.buffer(64, BufferOverflow.DROP_OLDEST)

    private fun Location.toTrackPoint() = TrackPoint(
        lat = latitude,
        lon = longitude,
        timeMs = if (time > 0L) time else System.currentTimeMillis(),
        speedMps = if (hasSpeed()) speed else -1f,
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE
    )

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
