package com.biyahe.companion.ui.summary

import kotlinx.coroutines.launch
import com.biyahe.companion.data.model.RoutePoint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biyahe.companion.AppContainer
import com.biyahe.companion.trip.StoredTrip
import com.biyahe.companion.tracking.LatLon
import com.biyahe.companion.tracking.Polyline
import com.biyahe.companion.ui.biyaheApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SummaryState(
    val loaded: Boolean = false,
    val stored: StoredTrip? = null,
    val route: List<LatLon> = emptyList(),
    /** Route with roughness per point; empty for old trips (the map then draws a plain line). */
    val roughRoute: List<RoutePoint> = emptyList()
)

class SummaryViewModel(private val container: AppContainer, private val tripId: Long) : ViewModel() {

    /** "How did the ride feel?" 1-5 stars; saved and re-uploaded (it backs up the Comfort Score in the paper). */
    fun rate(stars: Int) {
        container.appScope.launch {
            val t = container.tripRepository.getTrip(tripId) ?: return@launch
            container.tripRepository.updateTrip(t.copy(riderRating = stars.coerceIn(1, 5), cloudSynced = false))
            container.cloudSync.requestSync()
        }
    }

    val state: StateFlow<SummaryState> = container.tripSink.observeTrip(tripId)
        .map { stored ->
            SummaryState(
                loaded = true,
                stored = stored,
                route = stored?.trip?.routePolyline?.let { Polyline.decode(it) }.orEmpty(),
                roughRoute = stored?.trip?.roughRoute.orEmpty()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())

    companion object {
        /** One ViewModel per trip id (use with key = "summary-$tripId"). */
        fun factory(tripId: Long) = viewModelFactory {
            initializer { SummaryViewModel(biyaheApp().container, tripId) }
        }
    }
}
