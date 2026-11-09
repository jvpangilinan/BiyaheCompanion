package com.biyahe.companion.safety

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class EmergencyContact(val name: String, val phone: String)

data class SafetySettings(
    val speedLimitOn: Boolean = true,
    val speedLimitKmh: Int = 60,
    val bedtimeMin: Int = 23 * 60,       // 11:00 PM
    val wakeMin: Int = 6 * 60,           // 6:00 AM
    val morningCheckOn: Boolean = true,
    val crashDetectionOn: Boolean = true,
    val weavingWarningOn: Boolean = true,
    // More guards
    val tunnelGuardOn: Boolean = true,
    val rainModeOn: Boolean = true,
    val hazardAheadOn: Boolean = true,
    val breakReminderOn: Boolean = true,
    val breakEveryMin: Int = 120,
    val loudCabinOn: Boolean = true,
    val mountCheckOn: Boolean = true,
    /** Eye watch (front camera): drowsy-driving warnings. Asked about before each driving trip. */
    val eyeWatchOn: Boolean = true,
    /** Eye watch strictness: 1 Gentle, 2 Normal, 3 Strict (see EyeSensitivity). */
    val eyeSensitivity: Int = 2,
    // Study / work mode (eye watch at a desk): what to say, typed by the user.
    val focusWakeText: String = FocusDefaults.WAKE,
    val focusLockInText: String = FocusDefaults.LOCK_IN,
    val focusBreakText: String = FocusDefaults.BREAK,
    /** Break reminder every N minutes of a session; 0 = off. */
    val focusBreakMin: Int = 50,
    /** Loud alarm when the eyes close (off = voice + vibration only, e.g. in a library). */
    val focusAlarmOn: Boolean = true,
    /** Stage crash demo difficulty: 1 Easy, 2 Medium, 3 Normal. */
    val crashDemoLevel: Int = 2,
    /** The "Read me first" safety rules were accepted. */
    val rulesAccepted: Boolean = false,
    val contacts: List<EmergencyContact> = emptyList()
) {
    val plannedSleepHours: Double get() = SleepMath.hoursBetween(bedtimeMin, wakeMin)
    /** The limit to enforce right now, or null when the warning is off. */
    val activeLimitKmh: Int? get() = if (speedLimitOn) speedLimitKmh else null
}

/** What study / work mode says until the user types their own. */
object FocusDefaults {
    const val WAKE = "Wake up! Your eyes are closing. Sit up straight and take a deep breath."
    const val LOCK_IN = "Lock in. Eyes back on your work."
    const val BREAK = "Nice work. You've earned a break: stand up, stretch, and drink some water."
}

/** Today's answer to "Did you sleep well?". */
data class SleepCheck(val dateKey: String, val hours: Double, val rested: Boolean) {
    /** Short sleep or not rested: the Trip screen shows a drowsy-driving warning. */
    val tired: Boolean get() = !rested || hours < SleepMath.SHORT_SLEEP_H
}

/**
 * Safety settings, emergency contacts and today's sleep check.
 *
 * Kept in SharedPreferences (not Room) on purpose: they belong to the person, not to the trip
 * data, so they are the SAME in real and demo mode. Emergency contacts added once also work
 * during a stage demo.
 */
class SafetyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("biyahe_safety", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<SafetySettings> = _settings.asStateFlow()

    private val _todayCheck = MutableStateFlow(loadCheck())
    /** Null until today's question has been answered. */
    val todayCheck: StateFlow<SleepCheck?> = _todayCheck.asStateFlow()

    fun update(transform: (SafetySettings) -> SafetySettings) {
        val next = transform(_settings.value)
        save(next)
        _settings.value = next
    }

    fun saveSleepCheck(hours: Double, rested: Boolean) {
        val check = SleepCheck(today(), hours, rested)
        prefs.edit()
            .putString(K_CHECK_DATE, check.dateKey)
            .putFloat(K_CHECK_HOURS, hours.toFloat())
            .putBoolean(K_CHECK_RESTED, rested)
            .apply()
        _todayCheck.value = check
    }

    /** Ask once a day, in the morning (3 AM to noon), if the check is on and not answered yet. */
    fun shouldAskNow(now: Calendar = Calendar.getInstance()): Boolean {
        refreshDay()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return _settings.value.morningCheckOn && _todayCheck.value == null && hour in 3..11
    }

    /** Drops yesterday's answer after midnight. */
    fun refreshDay() {
        if (_todayCheck.value?.dateKey != null && _todayCheck.value?.dateKey != today()) _todayCheck.value = null
    }

    private fun load(): SafetySettings {
        val d = SafetySettings()
        return SafetySettings(
            speedLimitOn = prefs.getBoolean(K_SPEED_ON, d.speedLimitOn),
            speedLimitKmh = prefs.getInt(K_SPEED, d.speedLimitKmh),
            bedtimeMin = prefs.getInt(K_BED, d.bedtimeMin),
            wakeMin = prefs.getInt(K_WAKE, d.wakeMin),
            morningCheckOn = prefs.getBoolean(K_MORNING, d.morningCheckOn),
            crashDetectionOn = prefs.getBoolean(K_CRASH, d.crashDetectionOn),
            weavingWarningOn = prefs.getBoolean(K_WEAVE, d.weavingWarningOn),
            crashDemoLevel = prefs.getInt(K_DEMO_LEVEL, d.crashDemoLevel),
            tunnelGuardOn = prefs.getBoolean("g_tunnel", d.tunnelGuardOn),
            rainModeOn = prefs.getBoolean("g_rain", d.rainModeOn),
            hazardAheadOn = prefs.getBoolean("g_hazard", d.hazardAheadOn),
            breakReminderOn = prefs.getBoolean("g_break", d.breakReminderOn),
            breakEveryMin = prefs.getInt("g_break_min", d.breakEveryMin),
            loudCabinOn = prefs.getBoolean("g_loud", d.loudCabinOn),
            mountCheckOn = prefs.getBoolean("g_mount", d.mountCheckOn),
            eyeWatchOn = prefs.getBoolean("g_eye", d.eyeWatchOn),
            eyeSensitivity = prefs.getInt("g_eye_sens", d.eyeSensitivity),
            focusWakeText = prefs.getString("f_wake", null) ?: d.focusWakeText,
            focusLockInText = prefs.getString("f_lockin", null) ?: d.focusLockInText,
            focusBreakText = prefs.getString("f_break", null) ?: d.focusBreakText,
            focusBreakMin = prefs.getInt("f_break_min", d.focusBreakMin),
            focusAlarmOn = prefs.getBoolean("f_alarm", d.focusAlarmOn),
            rulesAccepted = prefs.getBoolean(K_RULES, d.rulesAccepted),
            contacts = decodeContacts(prefs.getString(K_CONTACTS, null))
        )
    }

    private fun save(s: SafetySettings) {
        prefs.edit()
            .putBoolean(K_SPEED_ON, s.speedLimitOn)
            .putInt(K_SPEED, s.speedLimitKmh)
            .putInt(K_BED, s.bedtimeMin)
            .putInt(K_WAKE, s.wakeMin)
            .putBoolean(K_MORNING, s.morningCheckOn)
            .putBoolean(K_CRASH, s.crashDetectionOn)
            .putBoolean(K_WEAVE, s.weavingWarningOn)
            .putInt(K_DEMO_LEVEL, s.crashDemoLevel)
            .putBoolean("g_tunnel", s.tunnelGuardOn)
            .putBoolean("g_rain", s.rainModeOn)
            .putBoolean("g_hazard", s.hazardAheadOn)
            .putBoolean("g_break", s.breakReminderOn)
            .putInt("g_break_min", s.breakEveryMin)
            .putBoolean("g_loud", s.loudCabinOn)
            .putBoolean("g_mount", s.mountCheckOn)
            .putBoolean("g_eye", s.eyeWatchOn)
            .putInt("g_eye_sens", s.eyeSensitivity)
            .putString("f_wake", s.focusWakeText)
            .putString("f_lockin", s.focusLockInText)
            .putString("f_break", s.focusBreakText)
            .putInt("f_break_min", s.focusBreakMin)
            .putBoolean("f_alarm", s.focusAlarmOn)
            .putBoolean(K_RULES, s.rulesAccepted)
            .putString(K_CONTACTS, encodeContacts(s.contacts))
            .apply()
    }

    private fun loadCheck(): SleepCheck? {
        val date = prefs.getString(K_CHECK_DATE, null) ?: return null
        if (date != today()) return null
        return SleepCheck(date, prefs.getFloat(K_CHECK_HOURS, 0f).toDouble(), prefs.getBoolean(K_CHECK_RESTED, true))
    }

    private fun encodeContacts(list: List<EmergencyContact>): String =
        JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("phone", it.phone)) } }.toString()

    private fun decodeContacts(raw: String?): List<EmergencyContact> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EmergencyContact(o.getString("name"), o.getString("phone"))
        }
    }.getOrDefault(emptyList())

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private companion object {
        const val K_SPEED_ON = "speed_on"
        const val K_SPEED = "speed_kmh"
        const val K_BED = "bedtime_min"
        const val K_WAKE = "wake_min"
        const val K_MORNING = "morning_check"
        const val K_CRASH = "crash_on"
        const val K_WEAVE = "weave_on"
        const val K_DEMO_LEVEL = "crash_demo_level"
        const val K_RULES = "rules_accepted"
        const val K_CONTACTS = "contacts"
        const val K_CHECK_DATE = "check_date"
        const val K_CHECK_HOURS = "check_hours"
        const val K_CHECK_RESTED = "check_rested"
    }
}
