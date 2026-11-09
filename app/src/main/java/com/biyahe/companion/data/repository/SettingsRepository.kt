package com.biyahe.companion.data.repository

import com.biyahe.companion.data.local.dao.UserDao
import com.biyahe.companion.data.local.entity.AppSettings
import com.biyahe.companion.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: UserDao) {

    val profile: Flow<UserProfile> = dao.observeProfile().map { it ?: UserProfile() }
    val settings: Flow<AppSettings> = dao.observeSettings().map { it ?: AppSettings() }

    /** Creates the default profile + settings rows on first launch. */
    suspend fun ensureDefaults() {
        dao.insertProfileIfMissing(UserProfile())
        dao.insertSettingsIfMissing(AppSettings())
    }

    suspend fun currentSettings(): AppSettings = dao.getSettings() ?: AppSettings()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        dao.saveSettings(transform(currentSettings()))
    }

    suspend fun updateDisplayName(name: String) {
        val current = dao.getProfile() ?: UserProfile()
        dao.saveProfile(current.copy(displayName = name.trim().ifEmpty { "Commuter" }))
    }
}
