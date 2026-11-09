package com.biyahe.companion.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.biyahe.companion.BiyaheApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the sensors recording when the screen is off or
 * the app is in the background. All the real work lives in TripRecorder; this
 * class only owns the notification, the wake lock and the coroutine scope.
 *
 * Start with [TripService.start], stop with [TripService.stop].
 * If Android kills the app mid-trip the recording is lost (START_NOT_STICKY).
 */
class TripService : Service() {

    private val container get() = (application as BiyaheApp).container
    private var scope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notifier: TripNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = TripNotifier(this)
        notifier.ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishRecording()
        } else {
            beginRecording()
        }
        return START_NOT_STICKY
    }

    private fun beginRecording() {
        val recorder = container.tripRecorder
        if (!enterForeground(recorder.state.value)) {
            stopSelf()
            return
        }
        if (recorder.isRecording()) return // already running; just refreshed the foreground state

        acquireWakeLock()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = serviceScope
        recorder.start(serviceScope)
        serviceScope.launch {
            while (isActive) {
                delay(NOTIFICATION_REFRESH_MS)
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(TripNotifier.NOTIFICATION_ID, notifier.build(recorder.state.value))
            }
        }
    }

    /** Must run within 5 seconds of startForegroundService(). Returns false if Android refused. */
    private fun enterForeground(state: com.biyahe.companion.tracking.RecorderState): Boolean {
        val notification = notifier.build(state)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(TripNotifier.NOTIFICATION_ID, notification, foregroundTypes())
            } else {
                startForeground(TripNotifier.NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) { // e.g. location permission missing on Android 14+
            false
        }
    }

    /** Only claim the sensors we are actually allowed to use (Android 14 checks this). */
    private fun foregroundTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && micGranted) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return types
    }

    private fun finishRecording() {
        container.tripRecorder.stop() // result stays in tripRecorder.finished for Section 6
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Biyahe:TripRecording")
            .apply { acquire(MAX_TRIP_MS) } // auto-releases after 6 hours as a safety net
    }

    private fun cleanup() {
        scope?.cancel()
        scope = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // If Android shuts the service down, keep whatever was recorded instead of losing it.
        if (container.tripRecorder.isRecording()) container.tripRecorder.stop()
        cleanup()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.biyahe.companion.action.START_TRIP_RECORDING"
        const val ACTION_STOP = "com.biyahe.companion.action.STOP_TRIP_RECORDING"
        private const val NOTIFICATION_REFRESH_MS = 3_000L
        private const val MAX_TRIP_MS = 6 * 60 * 60 * 1000L

        /** Call from the UI (app visible). Needs location permission granted first. */
        fun start(context: Context) {
            val intent = Intent(context, TripService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_STOP))
        }
    }
}
