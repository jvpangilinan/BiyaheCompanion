package com.biyahe.companion.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Measures how loud the ride is. PRIVACY: audio is analysed in memory and thrown
 * away immediately; only one loudness number every 250 ms is kept. Nothing is recorded.
 *
 * The value is an APPROXIMATE dB (phone microphones are not calibrated), good for
 * comparing rides with each other, not for legal noise measurements.
 */
class NoiseMeter(context: Context) {

    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Emits approximate dB roughly 4 times a second while collected; the microphone
     * is released as soon as collection stops. Completes without emitting if the
     * permission is missing or the microphone is busy (e.g. during a phone call).
     */
    @SuppressLint("MissingPermission")
    fun levels(): Flow<Double> = flow {
        if (!hasPermission()) return@flow

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return@flow

        val window = SAMPLE_RATE / 4 // 250 ms of audio
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, window * 2 * 2)
            )
        } catch (_: Exception) {
            return@flow
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@flow
        }

        val buffer = ShortArray(window)
        try {
            record.startRecording()
            var failed = false
            while (!failed && currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < buffer.size) {
                    val n = record.read(buffer, filled, buffer.size - filled)
                    if (n <= 0) { failed = true; break }
                    filled += n
                }
                if (failed) break
                emit(toDb(buffer))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun toDb(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        val rms = sqrt(sum / samples.size).coerceAtLeast(1.0)
        val dbFs = 20.0 * log10(rms / 32768.0) // 0 = full scale, quiet room is about -60
        return (dbFs + FULL_SCALE_DB).coerceIn(0.0, 120.0)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        /** Rough offset so speech (about -30 dBFS) lands near 60 dB. */
        const val FULL_SCALE_DB = 90.0
    }
}
