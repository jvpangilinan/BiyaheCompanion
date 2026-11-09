package com.biyahe.companion.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory "is the app unlocked?" state. It starts locked on every launch
 * (so a killed process always asks for the fingerprint again) and re-locks
 * if the app stays in the background longer than [relockTimeoutMs].
 */
class AuthSession(private val relockTimeoutMs: Long = 60_000L) {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var skipNextRelock = false

    fun unlock() {
        _isUnlocked.value = true
        backgroundedAt = null
    }

    fun lock() {
        _isUnlocked.value = false
    }

    /**
     * Call right before leaving the app on purpose (e.g. opening the camera
     * app in Section 8) so coming back does not demand a fingerprint again.
     */
    fun suppressNextRelock() {
        skipNextRelock = true
    }

    fun onBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForegrounded(lockEnabled: Boolean) {
        val left = backgroundedAt ?: return
        backgroundedAt = null
        if (skipNextRelock) {
            skipNextRelock = false
            return
        }
        val away = SystemClock.elapsedRealtime() - left
        if (lockEnabled && away >= relockTimeoutMs) lock()
    }
}
