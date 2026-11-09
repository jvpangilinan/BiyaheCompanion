package com.biyahe.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.RectangleShape
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterWhite
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.DemoModeCard
import com.biyahe.companion.ui.auth.AccountCard
import com.biyahe.companion.ui.auth.AccountChip
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterHero
import com.biyahe.companion.ui.components.PosterLogo
import com.biyahe.companion.ui.components.PosterTagline
import com.biyahe.companion.ui.components.StatCard
import com.biyahe.companion.ui.components.TripRowCard
import com.biyahe.companion.ui.safety.SafetyHeroPanel
import com.biyahe.companion.ui.safety.SafetyNav
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import com.biyahe.companion.ui.util.formatKm

@Composable
fun HomeScreen(
    onStartTrip: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onSeeHistory: () -> Unit,
    onSensorCheck: () -> Unit,
    onRecorderTest: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recording by viewModel.isRecording.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PosterHero(height = 200.dp) {
            Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, top = 24.dp, end = 20.dp)) {
                PosterLogo(big = 46.sp, small = 22.sp)
                Spacer(Modifier.height(10.dp))
                PosterTagline(fontSize = 14.sp)
            }
            // Always-visible account button (log out) in the top-right corner.
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountChip(recording = recording, modifier = Modifier.tourTarget(TourTargets.HOME_ACCOUNT))
                Box(
                    Modifier.size(34.dp).background(Ink).border(2.dp, PosterWhite, RectangleShape).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = PosterWhite, modifier = Modifier.size(20.dp)) }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SafetyHeroPanel(
                onOpen = { section -> SafetyNav.focus.value = section; onOpenSafety() },
                modifier = Modifier.tourTarget(TourTargets.HOME_SAFETY)
            )

            PosterButton(
                text = if (recording) "Trip in progress - open it" else "Start a trip",
                onClick = onStartTrip,
                modifier = Modifier.fillMaxWidth()
            )

            Column(Modifier.tourTarget(TourTargets.HOME_STATS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Trips", stats.tripCount.toString(), Modifier.weight(1f))
                    StatCard("Average score", stats.averageScore?.toString() ?: "-", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Distance", formatKm(stats.totalKm), Modifier.weight(1f))
                    StatCard("Walking steps", stats.totalSteps.toString(), Modifier.weight(1f))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PosterHeading("Recent trips", fontSize = 22.sp)
                if (stats.recent.isNotEmpty()) TextButton(onClick = onSeeHistory) { Text("SEE ALL", style = MaterialTheme.typography.labelLarge) }
            }
            if (stats.recent.isEmpty()) {
                Text("No trips yet. Tap Start a trip to record your first commute.", style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
            } else {
                stats.recent.forEachIndexed { i, trip ->
                    TripRowCard(
                        trip,
                        onClick = { onOpenTrip(trip.id) },
                        modifier = if (i == 0) Modifier.tourTarget(TourTargets.HOME_FIRST_TRIP) else Modifier
                    )
                }
            }


        }
    }
}
