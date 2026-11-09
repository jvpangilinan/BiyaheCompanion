package com.biyahe.companion.safety

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.biyahe.companion.MainActivity
import com.biyahe.companion.R
import com.biyahe.companion.sensors.Vec3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface CrashState {
    object Idle : CrashState
    /** Alarm ringing; the person can still tap "I'm OK". */
    data class Countdown(val secondsLeft: Int, val cause: String) : CrashState
    /** Nobody cancelled: contacts were alerted. [report] says what happened for each one. */
    data class Alerted(val cause: String, val report: List<String>) : CrashState
}

/**
 * What happens after a crash is detected:
 *
 *   1. Loud alarm + vibration + notification, and a 30 second countdown with a big "I'M OK".
 *   2. If nobody cancels: a text with the location goes to every emergency contact.
 *   3. The crash screen stays up with big Call buttons (contacts and 911).
 *
 * The app never calls 911 by itself: Android does not allow ordinary apps to, and a dropped
 * phone must never send an ambulance. In demo mode no text is ever sent; the screen only shows
 * what would have been sent.
 */
class CrashMonitor(
    private val context: Context,
    private val store: SafetyStore,
    private val alerts: SafetyAlerts,
    private val scope: CoroutineScope,
    private val isDemo: Boolean,
    private val accelerometer: () -> Flow<Vec3>,
    private val lastLocation: () -> Pair<Double, Double>?
) {
    private val _state = MutableStateFlow<CrashState>(CrashState.Idle)
    val state: StateFlow<CrashState> = _state.asStateFlow()

    private val _demoArmed = MutableStateFlow(false)
    /** Demo mode: true while waiting for the walk-and-stop gesture. */
    val demoArmed: StateFlow<Boolean> = _demoArmed.asStateFlow()

    private var countdownJob: Job? = null
    private var demoJob: Job? = null

    fun trigger(cause: String, countdownS: Int = COUNTDOWN_S) {
        if (_state.value != CrashState.Idle) return
        disarmDemo()
        countdownJob = scope.launch {
            alerts.startAlarm()
            showNotification()
            for (s in countdownS downTo 1) {
                _state.value = CrashState.Countdown(s, cause)
                delay(1_000)
            }
            alerts.stopAlarm()
            val report = alertContacts(cause)
            _state.value = CrashState.Alerted(cause, report)
            alerts.speak("No answer. Your emergency contacts have been alerted.", Voice.CRITICAL)
        }
    }

    /** "I'M OK": stop everything, nothing is sent. */
    fun imOk() {
        countdownJob?.cancel()
        countdownJob = null
        alerts.stopAlarm()
        cancelNotification()
        _state.value = CrashState.Idle
    }

    /** Close the crash screen after contacts were alerted. */
    fun dismiss() = imOk()

    /** Demo mode only: listen for "walk a few steps, stop sharply, hold still". */
    fun armDemo() {
        if (!isDemo || demoJob?.isActive == true) return
        val level = CrashDemoLevel.fromNumber(store.settings.value.crashDemoLevel)
        val detector = DemoCrashDetector(level)
        _demoArmed.value = true
        demoJob = scope.launch {
            accelerometer().collect { v ->
                if (detector.add(v)) trigger("Sudden stop (stage demo, level ${level.number} ${level.title})")
            }
        }
    }

    fun disarmDemo() {
        demoJob?.cancel()
        demoJob = null
        _demoArmed.value = false
    }

    private fun alertContacts(cause: String): List<String> {
        val contacts = store.settings.value.contacts
        if (contacts.isEmpty()) return listOf("No emergency contacts set. Add them in the Safety tab.")
        val message = buildMessage(cause)

        if (isDemo) {
            return contacts.map { "DEMO: would text ${it.name} (${it.phone})" } + "Message: \"$message\""
        }
        val canText = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canText) return listOf("Could not text your contacts: SMS permission is off (Safety tab).")

        val sms: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        return contacts.map { c ->
            runCatching {
                sms.sendMultipartTextMessage(c.phone, null, sms.divideMessage(message), null, null)
                "Texted ${c.name} (${c.phone})"
            }.getOrElse { "Could not text ${c.name}" }
        }
    }

    private fun buildMessage(cause: String): String {
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        val where = lastLocation()?.let { (lat, lon) ->
            "Location: https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lon)
        } ?: "Location: not available"
        return "BIYAHE COMPANION ALERT: a possible crash was detected at $time ($cause) and was not cancelled. $where"
    }

    private fun showNotification() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Crash alerts", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Crash detected")
                .setContentText("Tap to open and press I'M OK, or your contacts will be alerted.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID, n)
        }
    }

    private fun cancelNotification() {
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        }
    }

    companion object {
        const val COUNTDOWN_S = 30
        /** Philippine national emergency hotline (EO 56, 2018). */
        const val EMERGENCY_NUMBER = "911"
        private const val CHANNEL = "crash_alerts"
        private const val NOTIFICATION_ID = 4242
    }
}
