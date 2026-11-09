package com.biyahe.companion.ui.lock

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.biyahe.companion.BiyaheApp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.security.Availability
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.theme.BiyaheNavy
import com.biyahe.companion.ui.theme.BiyaheNavyLight
import com.biyahe.companion.ui.theme.BiyaheYellow
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHero
import com.biyahe.companion.ui.components.PosterLogo
import com.biyahe.companion.ui.components.PosterTagline
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.util.findActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(viewModel: LockViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Back on the lock screen leaves the app instead of bypassing the lock.
    BackHandler { activity?.finish() }

    // Re-check after returning from the system "add fingerprint" screen.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose { }
    }

    // Show the fingerprint prompt automatically the first time.
    LaunchedEffect(ui.availability) {
        if (ui.availability == Availability.AVAILABLE && !ui.autoPrompted && activity != null) {
            viewModel.markAutoPrompted()
            viewModel.authenticate(activity)
        }
    }

    val buttonLabel: String
    val info: String
    val onPrimary: () -> Unit
    when (ui.availability) {
        Availability.AVAILABLE -> {
            buttonLabel = "Unlock with fingerprint"
            info = "Your trip history is private."
            onPrimary = { activity?.let { viewModel.authenticate(it) } }
        }
        Availability.NONE_ENROLLED -> {
            buttonLabel = "Set up fingerprint"
            info = "No fingerprint is registered on this phone yet."
            onPrimary = {
                try {
                    context.startActivity(viewModel.enrollmentIntent())
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        Availability.NO_HARDWARE -> {
            buttonLabel = "Continue without lock"
            info = "This device has no fingerprint sensor."
            onPrimary = { viewModel.continueWithoutLock() }
        }
        else -> {
            buttonLabel = "Try again"
            info = "Fingerprint is not available right now."
            onPrimary = { viewModel.refreshAvailability() }
        }
    }

    Column(Modifier.fillMaxSize().background(Paper).safeDrawingPadding()) {
        PosterHero(height = 270.dp) {
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 24.dp)) {
                Text("SENSORVERSE 2026 · GROUP 1", style = MaterialTheme.typography.labelMedium, color = Ink)
                Spacer(Modifier.height(6.dp))
                PosterLogo(big = 60.sp, small = 28.sp)
                Spacer(Modifier.height(12.dp))
                PosterTagline(fontSize = 15.sp)
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(118.dp)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .posterCard(shadow = PosterRed, shadowOffset = 6.dp)
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = "Fingerprint",
                    tint = Ink,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                ui.message ?: info,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
            PosterButton(buttonLabel, onPrimary, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            // Emergency SOS without unlocking (like a phone's own "Emergency" button).
            PosterButton(
                "SOS · Emergency",
                { (context.applicationContext as BiyaheApp).container.crashMonitor.trigger("SOS button (lock screen)", countdownS = 10) },
                Modifier.fillMaxWidth(), danger = true
            )
        }
    }
}
