package com.biyahe.companion.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The single doorway to the phone's hardware sensors. Every sensor is a cold
 * Flow: the sensor is only switched on while something collects the Flow, and
 * switched off again when collection stops (saves battery).
 *
 * If the phone lacks a sensor, its Flow simply completes without emitting.
 */
class SensorHub(context: Context) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val packageManager: PackageManager = appContext.packageManager

    // ---------- availability ----------

    fun isAvailable(kind: SensorKind): Boolean = when (kind) {
        SensorKind.ACCELEROMETER -> has(Sensor.TYPE_ACCELEROMETER)
        SensorKind.GYROSCOPE -> has(Sensor.TYPE_GYROSCOPE)
        SensorKind.MAGNETOMETER -> has(Sensor.TYPE_MAGNETIC_FIELD)
        SensorKind.BAROMETER -> has(Sensor.TYPE_PRESSURE)
        SensorKind.AMBIENT_LIGHT -> has(Sensor.TYPE_LIGHT)
        SensorKind.PROXIMITY -> has(Sensor.TYPE_PROXIMITY)
        SensorKind.STEP_COUNTER -> has(Sensor.TYPE_STEP_COUNTER)
        SensorKind.GPS -> packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        SensorKind.MICROPHONE -> packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        SensorKind.CAMERA -> packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        SensorKind.FINGERPRINT -> packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    private fun has(type: Int) = sensorManager.getDefaultSensor(type) != null

    // ---------- motion (trip recording) ----------

    /** Raw accelerometer INCLUDING gravity (~9.8 m/s² when still). */
    fun accelerometer(delay: Int = SensorManager.SENSOR_DELAY_GAME): Flow<Vec3> =
        sensorFlow(Sensor.TYPE_ACCELEROMETER, delay) { e, _ -> e.toVec3() }

    /** Rotation speed in rad/s. */
    fun gyroscope(delay: Int = SensorManager.SENSOR_DELAY_GAME): Flow<Vec3> =
        sensorFlow(Sensor.TYPE_GYROSCOPE, delay) { e, _ -> e.toVec3() }

    /** Raw magnetic field in microtesla. */
    fun magnetometer(delay: Int = SensorManager.SENSOR_DELAY_UI): Flow<Vec3> =
        sensorFlow(Sensor.TYPE_MAGNETIC_FIELD, delay) { e, _ -> e.toVec3() }

    /**
     * Compass heading in degrees (0 = magnetic north, 90 = east), built from
     * the magnetometer + accelerometer and smoothed so the needle doesn't jitter.
     * Most accurate when the phone is held roughly flat or upright.
     */
    fun heading(): Flow<Float> = callbackFlow {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accel == null || magnet == null) {
            close()
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveMagnet = false
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var sinSmooth = 0f
        var cosSmooth = 0f
        var smoothReady = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity, haveGravity)
                        haveGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic, haveMagnet)
                        haveMagnet = true
                    }
                }
                if (haveGravity && haveMagnet &&
                    SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)
                ) {
                    SensorManager.getOrientation(rotation, orientation)
                    val rad = orientation[0]
                    if (!smoothReady) {
                        sinSmooth = sin(rad)
                        cosSmooth = cos(rad)
                        smoothReady = true
                    } else {
                        sinSmooth += HEADING_ALPHA * (sin(rad) - sinSmooth)
                        cosSmooth += HEADING_ALPHA * (cos(rad) - cosSmooth)
                    }
                    val degrees = Math.toDegrees(atan2(sinSmooth, cosSmooth).toDouble()).toFloat()
                    trySend((degrees + 360f) % 360f)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val ok = sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI) &&
            sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        if (!ok) {
            sensorManager.unregisterListener(listener)
            close()
            return@callbackFlow
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }.buffer(64, BufferOverflow.DROP_OLDEST)

    // ---------- environment ----------

    /** Air pressure in hectopascals (hPa). Used for rain/storm warnings (Section 6). */
    fun pressureHpa(delay: Int = SensorManager.SENSOR_DELAY_NORMAL): Flow<Float> =
        sensorFlow(Sensor.TYPE_PRESSURE, delay) { e, _ -> e.values[0] }

    /** Ambient light in lux. */
    fun ambientLux(delay: Int = SensorManager.SENSOR_DELAY_NORMAL): Flow<Float> =
        sensorFlow(Sensor.TYPE_LIGHT, delay) { e, _ -> e.values[0] }

    /** true while something (a pocket, a hand, your ear) is covering the proximity sensor. */
    fun proximityNear(delay: Int = SensorManager.SENSOR_DELAY_NORMAL): Flow<Boolean> =
        sensorFlow(Sensor.TYPE_PROXIMITY, delay) { e, sensor -> e.values[0] < sensor.maximumRange }

    // ---------- walking ----------

    /** Total steps since the phone last rebooted (cumulative). See [StepLog]. */
    fun stepCounter(): Flow<Float> =
        sensorFlow(Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL) { e, _ -> e.values[0] }

    // ---------- plumbing ----------

    private fun <T> sensorFlow(type: Int, delay: Int, map: (SensorEvent, Sensor) -> T): Flow<T> =
        callbackFlow {
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor == null) {
                close()
                return@callbackFlow
            }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    trySend(map(event, sensor))
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = try {
                sensorManager.registerListener(listener, sensor, delay)
            } catch (_: SecurityException) {
                false // e.g. Activity Recognition permission not granted yet
            }
            if (!registered) {
                close()
                return@callbackFlow
            }
            awaitClose { sensorManager.unregisterListener(listener) }
        }.buffer(256, BufferOverflow.DROP_OLDEST)

    private fun SensorEvent.toVec3() = Vec3(values[0], values[1], values[2], System.currentTimeMillis())

    private fun lowPass(input: FloatArray, output: FloatArray, initialised: Boolean) {
        for (i in 0..2) {
            output[i] = if (initialised) output[i] + LOW_PASS_ALPHA * (input[i] - output[i]) else input[i]
        }
    }

    private companion object {
        const val LOW_PASS_ALPHA = 0.1f
        const val HEADING_ALPHA = 0.15f
    }
}
