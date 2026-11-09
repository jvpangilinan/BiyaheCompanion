package com.biyahe.companion.safety

import com.biyahe.companion.tracking.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class EyeStatus(val label: String) {
    OFF("Off"), WATCHING("Eyes OK"), NO_FACE("Can't see you"), DROWSY("Drowsy"), ALARM("WAKE UP")
}

/** What eye watch is being used for. */
enum class EyeUse { DRIVING, TEST, FOCUS }

data class EyeWatchState(
    /** Running: for this trip (set up before it started), a live test, or a study / work session. */
    val active: Boolean = false,
    val use: EyeUse = EyeUse.DRIVING,
    val status: EyeStatus = EyeStatus.OFF,
    val openness: Float? = null,
    val baseline: Float? = null,
    val perclos: Double = 0.0,
    val alarm: Boolean = false,
    /** Study / work session: when it started, and what was said so far. */
    val sessionStartMs: Long = 0L,
    val wakeCount: Int = 0,
    val lockInCount: Int = 0,
    val breakCount: Int = 0,
    /** The words on the WAKE UP screen. */
    val alarmText: String = "Open your eyes and focus on the road.\nPull over somewhere safe and rest."
) {
    val testing: Boolean get() = active && use == EyeUse.TEST
    val focus: Boolean get() = active && use == EyeUse.FOCUS
    /** Runs off the trip: its own screen owns the camera. */
    val standalone: Boolean get() = testing || focus
}

/**
 * Eye watch (front camera): drowsiness warnings from how open your eyes are.
 *
 * DRIVING: set up before a driving trip (phone in a holder facing the driver, a few seconds of
 * calibration), runs while the trip records. Warnings only sound while moving ([MIN_SPEED_KMH]).
 *   Eyes closed ~1.5 s           loud alarm + "Wake up!", until the eyes open again (or a tap)
 *   Eyes closed/drooping often    beep + "Your eyes are getting heavy..." (escalates the 2nd time)
 *   No face for 20 s              "I can't see your eyes. Check the phone holder once you've stopped."
 *
 * FOCUS (study / work): the same detector at a desk, saying what the user typed:
 *   Eyes closed ~1.5 s           their "wake up" line (+ the loud alarm, if they want it)
 *   Eyes drooping often / looking away for 20 s   their "lock in" line
 *   Every N minutes               their "take a break" line
 *
 * TEST: the Safety tab's live test, driving messages without the speed check.
 */
class EyeWatch(
    private val store: SafetyStore,
    private val alerts: SafetyAlerts,
    private val guards: SafetyGuards,
    private val recorder: TripRecorder,
    private val crash: CrashMonitor,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(EyeWatchState())
    val state: StateFlow<EyeWatchState> = _state.asStateFlow()

    private var detector: DrowsinessDetector? = null
    private var lastDrowsyAt = 0L
    private var drowsyCount = 0
    private var breakJob: Job? = null
    private var alarmSounding = false

    /** Called once by AppContainer: driving eye watch ends with the trip. */
    fun start() {
        scope.launch {
            recorder.state.map { it.isRecording }.distinctUntilChanged().collect { recording ->
                if (!recording && _state.value.use == EyeUse.DRIVING) stop()
            }
        }
    }

    /** After the setup screen: watch this trip with the calibrated [baseline]. */
    fun armForTrip(baseline: Float) = begin(baseline, EyeUse.DRIVING)

    /** Safety tab live test: no speed check, ends when the test screen closes. */
    fun startTest(baseline: Float) = begin(baseline, EyeUse.TEST)

    /** Study / work session, with the user's own messages and break reminder. */
    fun startFocus(baseline: Float) {
        begin(baseline, EyeUse.FOCUS)
        val everyMin = store.settings.value.focusBreakMin
        if (everyMin > 0) {
            breakJob = scope.launch {
                var n = 0
                while (isActive) {
                    delay(everyMin * 60_000L)
                    n++
                    withContext(Dispatchers.Main) {
                        if (_state.value.focus) {
                            alerts.drowsyWarning(store.settings.value.focusBreakText.ifBlank { FocusDefaults.BREAK })
                            _state.update { it.copy(breakCount = n) }
                        }
                    }
                }
            }
        }
    }

    private fun begin(baseline: Float, use: EyeUse) {
        stop()
        val sens = EyeSensitivity.fromLevel(store.settings.value.eyeSensitivity)
        detector = DrowsinessDetector(
            baseline, sens,
            faceLostCooldownMs = if (use == EyeUse.FOCUS) 2 * 60_000L else 5 * 60_000L
        )
        lastDrowsyAt = 0L; drowsyCount = 0
        _state.value = EyeWatchState(
            active = true, use = use, status = EyeStatus.WATCHING, baseline = baseline,
            sessionStartMs = System.currentTimeMillis(),
            alarmText = if (use == EyeUse.FOCUS) store.settings.value.focusWakeText.ifBlank { FocusDefaults.WAKE }
            else EyeWatchState().alarmText
        )
    }

    /** Trip ended, setup skipped, test or session closed. */
    fun stop() {
        breakJob?.cancel(); breakJob = null
        stopAlarmSound()
        detector = null
        _state.value = EyeWatchState()
    }

    /** The user tapped the big WAKE UP screen. */
    fun dismissAlarm() {
        detector?.dismissAlarm()
        stopAlarmSound()
        _state.update { it.copy(alarm = false, status = EyeStatus.WATCHING) }
    }

    /** One camera frame (main thread). */
    fun onFrame(t: Long, openness: Float?) {
        val d = detector ?: return
        val s = _state.value
        val focus = s.use == EyeUse.FOCUS
        val settings = store.settings.value
        val live = s.use != EyeUse.DRIVING || recorder.state.value.speedKmh >= MIN_SPEED_KMH
        var next = s

        when (d.update(t, openness)) {
            EyeEvent.MICROSLEEP -> if (live) {
                if (focus) {
                    val text = settings.focusWakeText.ifBlank { FocusDefaults.WAKE }
                    if (settings.focusAlarmOn) soundAlarm(text) else alerts.drowsyWarning(text)
                    next = next.copy(wakeCount = s.wakeCount + 1)
                } else {
                    soundAlarm("Wake up! Your eyes are closed. Open your eyes and focus on the road.")
                    guards.logQuietly("Eyes closed", "Your eyes were closed while driving. Pull over somewhere safe and rest.")
                }
            } else d.dismissAlarm()
            EyeEvent.AWAKE -> {
                val wasRinging = alarmSounding
                stopAlarmSound()
                if (!focus && wasRinging) alerts.speak("Stay focused on the road. If you're sleepy, pull over and rest.", Voice.HIGH)
            }
            EyeEvent.DROWSY -> if (live) {
                val prevDrowsyAt = lastDrowsyAt
                lastDrowsyAt = t
                if (focus) {
                    alerts.drowsyWarning(settings.focusLockInText.ifBlank { FocusDefaults.LOCK_IN })
                    next = next.copy(lockInCount = s.lockInCount + 1)
                } else {
                    drowsyCount = if (prevDrowsyAt != 0L && t - prevDrowsyAt < 10 * 60_000L) drowsyCount + 1 else 1
                    val text = if (drowsyCount >= 2)
                        "You're getting sleepy. Please stop somewhere safe and rest before you drive on."
                    else "Your eyes are getting heavy. Stay focused on the road, and take a break soon."
                    alerts.drowsyWarning(text)
                    guards.logQuietly("Drowsy", text)
                }
            }
            EyeEvent.FACE_LOST -> if (live) {
                if (focus) {
                    alerts.drowsyWarning(settings.focusLockInText.ifBlank { FocusDefaults.LOCK_IN })
                    next = next.copy(lockInCount = s.lockInCount + 1)
                } else {
                    val text = "I can't see your eyes. Check that the phone faces you, once you've stopped."
                    alerts.notice(text)
                    guards.logQuietly("Eye watch", text)
                }
            }
            null -> Unit
        }

        val status = when {
            d.alarmOn -> EyeStatus.ALARM
            !d.faceVisible(t) -> EyeStatus.NO_FACE
            lastDrowsyAt != 0L && t - lastDrowsyAt < 30_000L -> EyeStatus.DROWSY
            else -> EyeStatus.WATCHING
        }
        // Voice-only wake up (alarm sound off) still counts as "alarm" for the detector; only show
        // the WAKE UP screen when the alarm actually rings.
        _state.value = next.copy(status = status, openness = openness, perclos = d.perclos, alarm = d.alarmOn && alarmSounding)
        if (!d.alarmOn && alarmSounding) stopAlarmSound()
    }

    private fun soundAlarm(text: String) {
        // A crash countdown already rings the alarm; don't stack another on top.
        if (crash.state.value != CrashState.Idle) return
        alarmSounding = true
        alerts.eyesClosedAlarm(text)
    }

    private fun stopAlarmSound() {
        if (!alarmSounding) return
        alarmSounding = false
        // Never silence a crash countdown that happens to be ringing.
        if (crash.state.value == CrashState.Idle) alerts.stopAlarm()
    }

    companion object {
        /** Below this the car is stopped (traffic light, parked): resting your eyes is fine. */
        const val MIN_SPEED_KMH = 5.0
    }
}
