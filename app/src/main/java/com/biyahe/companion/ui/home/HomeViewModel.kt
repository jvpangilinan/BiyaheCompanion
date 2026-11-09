package com.biyahe.companion.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.trip.StoredTrip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeStats(
    val tripCount: Int = 0,
    val averageScore: Int? = null,
    val totalKm: Double = 0.0,
    val totalSteps: Int = 0,
    val recent: List<StoredTrip> = emptyList()
)

class HomeViewModel(container: AppContainer) : ViewModel() {

    val isDemo: Boolean = container.isDemo

    val stats: StateFlow<HomeStats> = container.tripSink.observeTrips()
        .map { trips ->
            HomeStats(
                tripCount = trips.size,
                averageScore = if (trips.isEmpty()) null else trips.map { it.trip.comfortScore }.average().toInt(),
                totalKm = trips.sumOf { it.trip.distanceKm },
                totalSteps = trips.sumOf { it.trip.stepsBefore + it.trip.stepsAfter },
                recent = trips.take(3)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    val isRecording: StateFlow<Boolean> = container.tripRecorder.state
        .map { it.isRecording }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
