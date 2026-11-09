package com.biyahe.companion.ui.recorder

import androidx.lifecycle.ViewModel
import com.biyahe.companion.AppContainer
import com.biyahe.companion.tracking.RecordedTrip
import com.biyahe.companion.tracking.RecorderState
import kotlinx.coroutines.flow.StateFlow

/** Drives the "Trip recorder test" screen. Section 6 replaces this with the real TripViewModel. */
class RecorderCheckViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<RecorderState> = container.tripRecorder.state
    val finished: StateFlow<RecordedTrip?> = container.tripRecorder.finished

    fun hasLocationPermission(): Boolean = container.locationTracker.hasPermission()
    fun hasMicPermission(): Boolean = container.noiseMeter.hasPermission()
    fun isGpsEnabled(): Boolean = container.locationTracker.isGpsEnabled()
}
