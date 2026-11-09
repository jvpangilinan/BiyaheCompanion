package com.biyahe.companion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.biyahe.companion.navigation.BiyaheNavHost
import com.biyahe.companion.ui.theme.BiyaheTheme
import kotlinx.coroutines.launch

/**
 * Single-activity app. Extends FragmentActivity (not ComponentActivity)
 * because BiometricPrompt requires it.
 */
class MainActivity : FragmentActivity() {

    private val container get() = (application as BiyaheApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Re-lock the app if it was in the background for too long.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.authSession.onBackgrounded()
            }

            override fun onStart(owner: LifecycleOwner) {
                lifecycleScope.launch {
                    val lockEnabled = container.settingsRepository.currentSettings().fingerprintLockEnabled
                    container.authSession.onForegrounded(lockEnabled)
                }
            }
        })

        setContent {
            BiyaheTheme {
                BiyaheNavHost()
            }
        }
    }
}
