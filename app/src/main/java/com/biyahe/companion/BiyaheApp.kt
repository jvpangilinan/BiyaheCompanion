package com.biyahe.companion

import android.app.Application
import com.biyahe.companion.data.DemoData
import kotlinx.coroutines.launch

/**
 * Application class. Holds the single [AppContainer] that every screen,
 * ViewModel and service pulls its dependencies from (simple manual DI).
 */
class BiyaheApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Just logged in and restarted into this account's data: don't ask for the fingerprint again.
        if (DemoMode.consumeUnlockOnce(this)) container.authSession.unlock()

        container.appScope.launch {
            // Sample data only goes into the separate demo database, never into the real one.
            if (container.isDemo) {
                DemoData.seedDemo(this@BiyaheApp, container.database, container.tripRepository, container.hazardRepository)
            }
            container.settingsRepository.ensureDefaults()
            // A rider's cloud data comes back on any phone they log in to; then anything new uploads.
            container.cloudSync.restoreThenSync()
        }
    }
}
