package com.biyahe.companion.ui.lock

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biyahe.companion.AppContainer
import com.biyahe.companion.security.Availability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val availability: Availability = Availability.UNKNOWN,
    val message: String? = null,
    val isPrompting: Boolean = false,
    val autoPrompted: Boolean = false
)

class LockViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    init {
        // If the user turned the fingerprint lock off in settings, skip the gate.
        viewModelScope.launch {
            if (!container.settingsRepository.currentSettings().fingerprintLockEnabled) {
                container.authSession.unlock()
            }
        }
        refreshAvailability()
    }

    fun refreshAvailability() {
        _ui.update { it.copy(availability = container.authenticator.availability()) }
    }

    fun markAutoPrompted() {
        _ui.update { it.copy(autoPrompted = true) }
    }

    fun enrollmentIntent(): Intent = container.authenticator.enrollmentIntent()

    fun authenticate(activity: FragmentActivity) {
        if (_ui.value.isPrompting) return
        _ui.update { it.copy(isPrompting = true, message = null) }
        container.authenticator.authenticate(
            activity = activity,
            title = "Unlock Biyahe Companion",
            subtitle = "Confirm it's you to open your trip history",
            onSuccess = {
                _ui.update { it.copy(isPrompting = false) }
                container.authSession.unlock()
            },
            onFailedAttempt = {
                _ui.update { it.copy(message = "Fingerprint not recognized. Try again.") }
            },
            onError = { userCancelled, text ->
                _ui.update {
                    it.copy(
                        isPrompting = false,
                        message = if (userCancelled) "Tap the fingerprint to try again." else text
                    )
                }
            }
        )
    }

    /** Only offered on phones with no fingerprint hardware at all (e.g. some emulators). */
    fun continueWithoutLock() {
        container.authSession.unlock()
    }
}
