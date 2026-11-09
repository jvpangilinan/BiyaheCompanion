package com.biyahe.companion.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.trip.StoredTrip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryState(
    val trips: List<StoredTrip> = emptyList(),
    val totalSteps: Int = 0,
    val totalKm: Double = 0.0,
    val loaded: Boolean = false
)

class HistoryViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<HistoryState> = container.tripSink.observeTrips()
        .map { trips ->
            HistoryState(
                trips = trips,
                totalSteps = trips.sumOf { it.trip.stepsBefore + it.trip.stepsAfter },
                totalKm = trips.sumOf { it.trip.distanceKm },
                loaded = true
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}
