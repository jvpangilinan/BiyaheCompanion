package com.biyahe.companion.ui.sensorcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.data.local.entity.AppSettings
import com.biyahe.companion.sensors.ScreenCareState
import com.biyahe.companion.sensors.SensorKind
import com.biyahe.companion.sensors.Vec3
import com.biyahe.companion.tracking.TrackPoint
import com.biyahe.companion.sensors.compassLabel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class SensorRow(
    val kind: SensorKind,
    val available: Boolean,
    val reading: String? = null
)

/** Drives the "Sensor check" screen: availability of all 11 components + live values for the 7 hardware sensors. */
@OptIn(FlowPreview::class)
class SensorCheckViewModel(private val container: AppContainer) : ViewModel() {

    private val hub = container.sensorHub

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val screenCare: StateFlow<ScreenCareState> = container.screenCareController.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), ScreenCareState())

    private val initialRows = SensorKind.entries.map { SensorRow(it, hub.isAvailable(it)) }

    val rows: StateFlow<List<SensorRow>> = combine(liveReadings()) { pairs ->
        val readings = pairs.toMap()
        SensorKind.entries.map { kind ->
            SensorRow(kind, hub.isAvailable(kind), readings[kind])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), initialRows)

    // ---- one sensor at a time, for the individual test screens (cold flows: the sensor is only
    // ---- switched on while a test screen collects it)
    fun accelRaw(): Flow<Vec3> = hub.accelerometer()
    fun gyroRaw(): Flow<Vec3> = hub.gyroscope()
    fun magnetRaw(): Flow<Vec3> = hub.magnetometer()
    fun headingFlow(): Flow<Float> = hub.heading()
    fun luxFlow(): Flow<Float> = hub.ambientLux()
    fun nearFlow(): Flow<Boolean> = hub.proximityNear()
    fun pressureFlow(): Flow<Float> = hub.pressureHpa()
    fun stepsFlow(): Flow<Float> = hub.stepCounter()
    fun gpsFlow(): Flow<TrackPoint> = container.locationTracker.points()
    fun gpsEnabled(): Boolean = container.locationTracker.isGpsEnabled()
    fun noiseFlow(): Flow<Double> = container.noiseMeter.levels()
    fun authAvailability() = container.authenticator.availability()
    val authenticator get() = container.authenticator

    private fun liveReadings(): List<Flow<Pair<SensorKind, String>>> = listOf(
        reading(SensorKind.ACCELEROMETER, hub.accelerometer().map { "x ${f(it.x)}  y ${f(it.y)}  z ${f(it.z)} m/s²" }),
        reading(SensorKind.GYROSCOPE, hub.gyroscope().map { "x ${f(it.x)}  y ${f(it.y)}  z ${f(it.z)} rad/s" }),
        reading(SensorKind.MAGNETOMETER, hub.heading().map { "${compassLabel(it)}  ${it.toInt()}°" }),
        reading(SensorKind.BAROMETER, hub.pressureHpa().map { "${f(it)} hPa" }),
        reading(SensorKind.AMBIENT_LIGHT, hub.ambientLux().map { "${it.toInt()} lux" }),
        reading(SensorKind.PROXIMITY, hub.proximityNear().map { if (it) "Near (covered)" else "Far (clear)" }),
        reading(SensorKind.STEP_COUNTER, hub.stepCounter().map { "${it.toLong()} steps since boot" })
    )

    /** Emits "waiting..." immediately, then at most 4 updates per second. */
    private fun reading(kind: SensorKind, values: Flow<String>): Flow<Pair<SensorKind, String>> =
        values.sample(250).map { kind to it }.onStart { emit(kind to "waiting for data...") }

    private fun f(v: Float): String = String.format(Locale.US, "%.1f", v)
}
