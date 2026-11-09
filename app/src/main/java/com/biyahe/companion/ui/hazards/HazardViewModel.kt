package com.biyahe.companion.ui.hazards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.model.HazardType
import com.biyahe.companion.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HazardViewModel(private val container: AppContainer) : ViewModel() {

    val hazards: StateFlow<List<Hazard>> = container.hazardRepository.allHazards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncState: StateFlow<SyncState> = container.cloudSync.state

    val isDemo: Boolean = container.isDemo

    fun syncNow() = container.cloudSync.requestSync()

    /** Saves the report to SQLite first (so it can never be lost), then starts an upload. */
    fun saveReport(
        type: HazardType,
        note: String,
        photoPath: String,
        latitude: Double,
        longitude: Double,
        takenAtMs: Long,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            container.hazardRepository.saveHazard(
                Hazard(
                    type = type.name,
                    note = note.trim(),
                    photoPath = photoPath,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = takenAtMs
                )
            )
            container.cloudSync.requestSync()
            onSaved()
        }
    }

    suspend fun load(id: Long): Hazard? = container.hazardRepository.getHazard(id)

    /**
     * Saves an edited report. It is marked "not uploaded" so the next sync overwrites the cloud copy
     * (same document id, so there is never a duplicate). A replaced photo file is removed.
     */
    fun updateReport(original: Hazard, type: HazardType, note: String, photoPath: String, latitude: Double, longitude: Double, onSaved: () -> Unit) {
        viewModelScope.launch {
            container.hazardRepository.updateHazard(
                original.copy(
                    type = type.name, note = note.trim(), photoPath = photoPath,
                    latitude = latitude, longitude = longitude, cloudSynced = false
                )
            )
            if (photoPath != original.photoPath && original.photoPath.isNotBlank()) {
                withContext(Dispatchers.IO) { runCatching { File(original.photoPath).delete() } }
            }
            container.cloudSync.requestSync()
            onSaved()
        }
    }

    /** Removes the report from the cloud (when online) and from this phone. */
    fun delete(hazard: Hazard, onResult: (cloudRemoved: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val cloud = container.cloudSync.deleteHazardInCloud(hazard)
            withContext(Dispatchers.IO) {
                runCatching { File(hazard.photoPath).delete() }
                container.hazardRepository.deleteHazard(hazard.id)
            }
            onResult(cloud)
        }
    }
}
