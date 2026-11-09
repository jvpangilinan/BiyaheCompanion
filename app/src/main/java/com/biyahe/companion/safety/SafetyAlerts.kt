package com.biyahe.companion.safety

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Everything the app says or plays out loud. Warnings are sound + vibration + voice, never
 * something the driver has to read, so nobody needs to look at the phone while driving.
 */
class SafetyAlerts(context: Context, private val tuning: () -> Tuning = { Tuning.DEFAULT }) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Pair<String, Voice>? = null
    private val lastSaid = mutableMapOf<String, Long>()
    private val levels = mutableMapOf<String?, Int>()
    private var utteranceSeq = 0
    private var currentLevel = -1
    private var waiting = 0
    private var alarm: Ringtone? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Beep, double buzz, then "You're going too fast". */
    fun speedWarning(limitKmh: Int) = main.post {
        beep()
        vibrate(longArrayOf(0, 300, 150, 300), repeat = false)
        speak("You're going too fast. The speed limit is $limitKmh. Please slow down.", Voice.HIGH)
    }

    /** Drift demo: beep, long buzz, "Drifting left/right". */
    fun driftWarning(left: Boolean) = main.post {
        beep()
        vibrate(longArrayOf(0, 600), repeat = false)
        speak("Drifting ${if (left) "left" else "right"}! Steer back to your lane.", Voice.HIGH)
    }

    /** Frequent lane weaving: a gentle "are you tired?" rather than an alarm. */
    fun weavingWarning() = main.post {
        beep()
        vibrate(longArrayOf(0, 400, 200, 400), repeat = false)
        speak("You're weaving between lanes a lot. Are you tired? Consider taking a break.", Voice.HIGH)
    }

    /** Eye watch: eyes getting heavy. Beep, triple buzz, then the message. */
    fun drowsyWarning(text: String) = main.post {
        beep()
        vibrate(longArrayOf(0, 400, 150, 400, 150, 400), repeat = false)
        speak(text, Voice.HIGH)
    }

    /** Eye watch: eyes closed too long. The loud looping alarm, then [text] spoken over it. */
    fun eyesClosedAlarm(text: String) {
        startAlarm()
        speak(text, Voice.CRITICAL)
    }

    /** The "more guards": a short buzz, then the message spoken. */
    fun notice(text: String) = main.post {
        vibrate(longArrayOf(0, 250), repeat = false)
        speak(text)
    }

    /**
     * The voice queue, so warnings never talk over each other:
     *  - one message at a time; a more important one interrupts a less important one
     *  - while something is talking, at most ONE normal message waits; more are dropped
     *  - the same warning is not repeated within 10 seconds
     *  - Chanelly's tour lines (TOUR) always replace the previous line
     */
    fun speak(text: String, priority: Voice = Voice.NORMAL) = main.post {
        val now = System.currentTimeMillis()
        if (priority != Voice.TOUR) {
            val last = lastSaid[text]
            if (last != null && now - last < 10_000L) return@post
            lastSaid[text] = now
        }
        val engine = tts
        if (engine == null) {
            pendingSpeech = text to priority
            tts = TextToSpeech(ctx) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.US
                    tts?.setOnUtteranceProgressListener(progress)
                    pendingSpeech?.let { (t, p) -> say(t, p, flush = true) }
                    pendingSpeech = null
                }
            }
            return@post
        }
        if (!ttsReady) { pendingSpeech = text to priority; return@post }

        val busy = engine.isSpeaking
        when {
            !busy || priority == Voice.TOUR || priority.level > currentLevel -> say(text, priority, flush = true)
            waiting < 1 -> say(text, priority, flush = false)
            else -> Unit // already one waiting: drop this less important message
        }
    }

    private fun say(text: String, priority: Voice, flush: Boolean) {
        val id = "u${++utteranceSeq}"
        levels[id] = priority.level
        if (flush) { waiting = 0; currentLevel = priority.level } else waiting++
        val t = tuning()
        tts?.setSpeechRate(t.voiceRate)
        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, t.voiceVolume) }
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id)
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post {
                val lvl = levels[utteranceId] ?: return@post
                if (lvl != currentLevel && waiting > 0) waiting--
                currentLevel = lvl
            }
        }
        override fun onDone(utteranceId: String?) {
            main.post { levels.remove(utteranceId); if (tts?.isSpeaking != true) { currentLevel = -1; waiting = 0 } }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onDone(utteranceId)
    }

    /** Loud looping alarm + continuous vibration, for the crash countdown. */
    fun startAlarm() = main.post {
        if (alarm?.isPlaying == true) return@post
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        alarm = runCatching { RingtoneManager.getRingtone(ctx, uri) }.getOrNull()?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrate(longArrayOf(0, 800, 400), repeat = true)
    }

    fun stopAlarm() = main.post {
        runCatching { alarm?.stop() }
        alarm = null
        runCatching { vibrator?.cancel() }
    }

    private fun beep() {
        if (!tuning().beepOn) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            main.postDelayed({ tone.release() }, 1_000)
        }
    }

    private fun vibrate(pattern: LongArray, repeat: Boolean) {
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, if (repeat) 0 else -1))
        }
    }
}

/** How important a spoken message is (see SafetyAlerts.speak). */
enum class Voice(val level: Int) { TOUR(0), NORMAL(1), HIGH(2), CRITICAL(3) }
