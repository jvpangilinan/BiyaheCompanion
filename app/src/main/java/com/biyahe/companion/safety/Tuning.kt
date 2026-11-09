package com.biyahe.companion.safety

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Detection sensitivity and sound, adjustable per phone / car / mount.
 *
 * Only HOW THE PHONE DETECTS things is tunable. The Comfort Score formula itself (weights and
 * ranges) is fixed, so a score of 80 means the same for every rider and the results stay comparable.
 */
data class Tuning(
    // Comfort (accelerometer, gyroscope)
    /** Jolts at least this big count as a big bump (m/s²). */
    val bumpThreshold: Double = 2.5,
    /** Normal shaking of this car / mount, removed before scoring (RMS m/s²). Set by Calibrate. */
    val vibrationFloor: Double = 0.0,
    /** Turning faster than this counts as a sharp turn (degrees per second). */
    val turnDps: Double = 57.0,
    // Speed limit (GPS)
    val speedGraceSec: Int = 3,
    val speedRepeatSec: Int = 20,
    // Crash (accelerometer + GPS)
    val crashImpactG: Double = 3.0,
    val crashFromKmh: Double = 25.0,
    // Lane weaving (gyroscope)
    val weaveDeg: Double = 4.0,
    val weaveCount: Int = 5,
    // More guards
    val tunnelLux: Float = 10f,
    val loudDb: Double = 85.0,
    val loudSec: Int = 30,
    val mountTiltDeg: Double = 40.0,
    // Sound
    val voiceVolume: Float = 1f,
    val voiceRate: Float = 1f,
    val beepOn: Boolean = true
) {
    companion object {
        val DEFAULT = Tuning()
    }
}

/** The simple choice shown for each section. */
enum class Level(val label: String) { GENTLE("Gentle"), NORMAL("Normal"), SENSITIVE("Sensitive") }

/**
 * Presets per section. GENTLE = fewer alerts (rough roads, loose mounts), SENSITIVE = more alerts.
 * Each returns the tuning with only that section changed.
 */
object TuningPresets {
    fun comfort(t: Tuning, l: Level) = when (l) {
        Level.GENTLE -> t.copy(bumpThreshold = 3.5, turnDps = 75.0)
        Level.NORMAL -> t.copy(bumpThreshold = 2.5, turnDps = 57.0)
        Level.SENSITIVE -> t.copy(bumpThreshold = 1.8, turnDps = 45.0)
    }
    fun speed(t: Tuning, l: Level) = when (l) {
        Level.GENTLE -> t.copy(speedGraceSec = 6, speedRepeatSec = 40)
        Level.NORMAL -> t.copy(speedGraceSec = 3, speedRepeatSec = 20)
        Level.SENSITIVE -> t.copy(speedGraceSec = 1, speedRepeatSec = 10)
    }
    fun crash(t: Tuning, l: Level) = when (l) {
        Level.GENTLE -> t.copy(crashImpactG = 4.0, crashFromKmh = 35.0)
        Level.NORMAL -> t.copy(crashImpactG = 3.0, crashFromKmh = 25.0)
        Level.SENSITIVE -> t.copy(crashImpactG = 2.5, crashFromKmh = 20.0)
    }
    fun weaving(t: Tuning, l: Level) = when (l) {
        Level.GENTLE -> t.copy(weaveDeg = 6.0, weaveCount = 7)
        Level.NORMAL -> t.copy(weaveDeg = 4.0, weaveCount = 5)
        Level.SENSITIVE -> t.copy(weaveDeg = 3.0, weaveCount = 4)
    }
    fun guards(t: Tuning, l: Level) = when (l) {
        Level.GENTLE -> t.copy(tunnelLux = 5f, loudDb = 90.0, loudSec = 60, mountTiltDeg = 55.0)
        Level.NORMAL -> t.copy(tunnelLux = 10f, loudDb = 85.0, loudSec = 30, mountTiltDeg = 40.0)
        Level.SENSITIVE -> t.copy(tunnelLux = 20f, loudDb = 80.0, loudSec = 20, mountTiltDeg = 30.0)
    }

    /** Which preset a section currently matches (null = custom fine-tuning). */
    fun levelOf(t: Tuning, apply: (Tuning, Level) -> Tuning): Level? = Level.entries.firstOrNull { apply(t, it) == t }
}

/** Saved on the phone (per phone: it describes this phone's sensors and mount). */
class TuningStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("biyahe_tuning", Context.MODE_PRIVATE)
    private val _tuning = MutableStateFlow(load())
    val tuning: StateFlow<Tuning> = _tuning.asStateFlow()

    fun update(transform: (Tuning) -> Tuning) {
        val t = transform(_tuning.value)
        prefs.edit().putString("t", toJson(t)).apply()
        _tuning.value = t
    }

    fun reset() = update { Tuning.DEFAULT }

    private fun load(): Tuning = runCatching { fromJson(JSONObject(prefs.getString("t", null)!!)) }.getOrDefault(Tuning.DEFAULT)

    private fun toJson(t: Tuning) = JSONObject()
        .put("bump", t.bumpThreshold).put("floor", t.vibrationFloor).put("turn", t.turnDps)
        .put("sg", t.speedGraceSec).put("sr", t.speedRepeatSec)
        .put("cg", t.crashImpactG).put("ck", t.crashFromKmh)
        .put("wd", t.weaveDeg).put("wc", t.weaveCount)
        .put("lux", t.tunnelLux.toDouble()).put("db", t.loudDb).put("ds", t.loudSec).put("tilt", t.mountTiltDeg)
        .put("vv", t.voiceVolume.toDouble()).put("vr", t.voiceRate.toDouble()).put("beep", t.beepOn)
        .toString()

    private fun fromJson(o: JSONObject): Tuning {
        val d = Tuning.DEFAULT
        return Tuning(
            o.optDouble("bump", d.bumpThreshold), o.optDouble("floor", d.vibrationFloor), o.optDouble("turn", d.turnDps),
            o.optInt("sg", d.speedGraceSec), o.optInt("sr", d.speedRepeatSec),
            o.optDouble("cg", d.crashImpactG), o.optDouble("ck", d.crashFromKmh),
            o.optDouble("wd", d.weaveDeg), o.optInt("wc", d.weaveCount),
            o.optDouble("lux", d.tunnelLux.toDouble()).toFloat(), o.optDouble("db", d.loudDb), o.optInt("ds", d.loudSec),
            o.optDouble("tilt", d.mountTiltDeg),
            o.optDouble("vv", d.voiceVolume.toDouble()).toFloat(), o.optDouble("vr", d.voiceRate.toDouble()).toFloat(),
            o.optBoolean("beep", d.beepOn)
        )
    }
}

/**
 * Calibrate: the car stopped with the engine on, phone in its mount, for ~10 s. The steady shaking
 * measured becomes the vibration floor, and the bump threshold is set well above it.
 */
object Calibration {
    /** [rmsJolt] = RMS of the gravity-free jolt measured while idling (m/s²). */
    fun apply(t: Tuning, rmsJolt: Double): Tuning = t.copy(
        vibrationFloor = rmsJolt.coerceIn(0.0, 1.5),
        bumpThreshold = (rmsJolt * 4.0).coerceIn(2.0, 5.0)
    )
}
