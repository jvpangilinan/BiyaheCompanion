package com.biyahe.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.biyahe.companion.tracking.RecorderState
import java.util.Locale

/** Builds the "recording your trip" notification that keeps the service alive and visible. */
class TripNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Trip recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while Biyahe Companion is recording a trip"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun build(state: RecorderState): Notification {
        val km = String.format(Locale.US, "%.2f km", state.distanceM / 1000.0)
        val totalSec = state.elapsedMs / 1000
        val time = String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
        val noise = state.noiseDb?.let { " · ${it.toInt()} dB" } ?: ""

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Recording your trip")
            .setContentText("$km · $time$noise")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "trip_recording"
        const val NOTIFICATION_ID = 4201
    }
}
