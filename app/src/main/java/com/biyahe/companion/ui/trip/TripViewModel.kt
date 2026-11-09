package com.biyahe.companion.ui.trip

import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.safety.GuardLogEntry
import com.biyahe.companion.safety.SleepCheck
import com.biyahe.companion.data.model.RoutePoint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.scoring.WeatherAssessment
import com.biyahe.companion.scoring.WeatherWatcher
import com.biyahe.companion.tracking.LatLon
import com.biyahe.companion.tracking.RecorderState
import com.biyahe.companion.trip.EndOutcome
import com.biyahe.companion.trip.StartOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the Trip tab: Start/End buttons, live numbers, route map, rain warning. */
class TripViewModel(private val container: AppContainer) : ViewModel() {

    private val controller = container.tripController
    private val recorder = container.tripRecorder

    val live: StateFlow<RecorderState> = recorder.state

    /** Today's sleep check; the screen warns when it says short sleep. */
    val sleepCheck: StateFlow<SleepCheck?> = container.safetyStore.todayCheck

    /** This trip's spoken safety warnings (tunnel, rain, hazard ahead, breaks, loud cabin, mount). */
    val safetyLog: StateFlow<List<GuardLogEntry>> = container.safetyGuards.log
    val rainMode: StateFlow<Boolean> = container.safetyGuards.rainActive

    /** Eye watch (front camera) for this trip. */
    val eyeWatch: StateFlow<com.biyahe.companion.safety.EyeWatchState> = container.eyeWatch.state

    /** Eye watch is switched on in the Safety tab, so ask about it before a driving trip. */
    fun eyeWatchWanted(): Boolean = container.safetyStore.settings.value.eyeWatchOn

    /** After setup: the calibrated baseline turns eye watch on for the trip; null = drive without it. */
    fun setEyeWatch(baseline: Float?) {
        if (baseline != null) container.eyeWatch.armForTrip(baseline) else container.eyeWatch.stop()
    }

    /** Live rain warning while riding (re-checked every second, cheap: about 2 readings a minute). */
    val liveWeather: StateFlow<WeatherAssessment> = recorder.state
        .map { WeatherWatcher.assess(recorder.pressureSnapshot()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherWatcher.assess(emptyList()))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _ending = MutableStateFlow(false)
    val ending: StateFlow<Boolean> = _ending.asStateFlow()

    private val _saved = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Fires the new trip's id after End Trip saved it; the screen then opens the Trip Summary. */
    val savedTripId: SharedFlow<Long> = _saved.asSharedFlow()

    /** Route so far for the live map. Call again when [RecorderState.pointCount] changes. */
    fun routePoints(): List<LatLon> = recorder.routeSnapshot().map { LatLon(it.lat, it.lon) }

    /** Live route with roughness, so the map colours the road as you ride. */
    fun roughRoutePoints(): List<RoutePoint> = recorder.roughRouteSnapshot()

    fun startTrip(mode: TripMode = TripMode.DRIVING) {
        _message.value = when (controller.start(mode)) {
            StartOutcome.STARTED, StartOutcome.ALREADY_RUNNING -> null
            StartOutcome.NEED_LOCATION_PERMISSION ->
                "Location permission is needed to record the route. Allow it in the app's Settings."
            StartOutcome.GPS_OFF ->
                "Location is switched off. Turn it on in the quick settings, then tap Start Trip again."
        }
    }

    fun endTrip() {
        if (_ending.value) return
        _ending.value = true
        // appScope, not viewModelScope: the save must finish even if the user leaves this screen.
        container.appScope.launch {
            when (val outcome = controller.end()) {
                is EndOutcome.Saved -> {
                    _message.value = null
                    _saved.emit(outcome.result.tripId)
                }
                EndOutcome.TooShort -> _message.value = "That trip was under 15 seconds, so it was not saved."
                EndOutcome.NothingToEnd -> _message.value = "No trip is running."
            }
            _ending.value = false
        }
    }
}
