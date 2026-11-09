package com.biyahe.companion.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.ui.hazards.HazardViewModel
import com.biyahe.companion.ui.history.HistoryViewModel
import com.biyahe.companion.ui.home.HomeViewModel
import com.biyahe.companion.ui.lock.LockViewModel
import com.biyahe.companion.ui.recorder.RecorderCheckViewModel
import com.biyahe.companion.ui.sensorcheck.SensorCheckViewModel
import com.biyahe.companion.ui.trip.TripViewModel

/**
 * One factory for every ViewModel. Later sections add their ViewModels here:
 *   SECTION 6/7 -> TripViewModel, HomeViewModel, HistoryViewModel
 *   (SummaryViewModel has its own factory: it needs a trip id)
 *   SECTION 8 -> HazardViewModel
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { LockViewModel(biyaheApp().container) }
        initializer { SensorCheckViewModel(biyaheApp().container) }
        initializer { RecorderCheckViewModel(biyaheApp().container) }
        initializer { TripViewModel(biyaheApp().container) }
        initializer { HomeViewModel(biyaheApp().container) }
        initializer { HistoryViewModel(biyaheApp().container) }
        initializer { HazardViewModel(biyaheApp().container) }
    }
}

fun CreationExtras.biyaheApp(): BiyaheApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BiyaheApp
