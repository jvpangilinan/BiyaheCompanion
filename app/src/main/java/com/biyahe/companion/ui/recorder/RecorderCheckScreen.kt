package com.biyahe.companion.ui.recorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.sensors.BumpLevel
import com.biyahe.companion.sensors.compassLabel
import com.biyahe.companion.service.TripService
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.theme.BiyaheGreen
import com.biyahe.companion.ui.theme.BiyaheNavy
import com.biyahe.companion.ui.theme.BiyaheRed
import java.util.Locale
import androidx.compose.ui.unit.sp
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.posterCard

/**
 * Section 5 check screen: start the background recorder, watch GPS / noise / motion
 * numbers update live, stop it, and see what was captured. Try it with the screen
 * off or the app in the background to confirm the service keeps recording.
 */
@Composable
fun RecorderCheckScreen(
    onBack: () -> Unit,
    viewModel: RecorderCheckViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BiyaheNavy)
            }
            Column {
                PosterHeading("Trip recorder test", fontSize = 24.sp)
                Text(
                    if (state.isRecording) "Recording..." else "Not recording",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isRecording) PosterRed else PosterGrey
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (state.isRecording) {
                    PosterButton("Stop recording", { TripService.stop(context); message = null }, Modifier.fillMaxWidth(), danger = true)
                } else {
                    PosterButton(
                        text = "Start recording",
                        onClick = {
                            message = when {
                                !viewModel.hasLocationPermission() ->
                                    "Location permission is needed. Allow it in the app's Settings, then try again."
                                !viewModel.isGpsEnabled() ->
                                    "Location is switched off. Turn it on in the quick settings, then try again."
                                else -> {
                                    TripService.start(context)
                                    if (!viewModel.hasMicPermission()) "Recording without noise: microphone permission is off." else null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = PosterRed, modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (state.isRecording) {
                item {
                    ReadoutCard(
                        "GPS",
                        listOf(
                            "Signal" to state.gpsStatus.label + (state.gpsAccuracyM?.let { "  (±${it.toInt()} m)" } ?: ""),
                            "Distance" to fmt("%.2f km", state.distanceM / 1000.0),
                            "Speed" to fmt("%.0f km/h", state.speedKmh),
                            "Top speed" to fmt("%.0f km/h", state.maxSpeedKmh),
                            "Route points" to state.pointCount.toString(),
                            "Time" to duration(state.elapsedMs)
                        )
                    )
                }
                item {
                    ReadoutCard(
                        "Motion",
                        listOf(
                            "Bumpiness now" to "${fmt("%.1f", state.bumpiness)} m/s²  (${BumpLevel.from(state.bumpiness).label})",
                            "Bumps" to state.bumpCount.toString(),
                            "Sharp turns" to state.sharpTurnCount.toString(),
                            "Heading" to (state.headingDeg?.let { "${compassLabel(it)}  ${it.toInt()}°" } ?: "-")
                        )
                    )
                }
                item {
                    ReadoutCard(
                        "Noise and air",
                        listOf(
                            "Noise now" to (state.noiseDb?.let { "${it.toInt()} dB (approx.)" } ?: "no microphone data"),
                            "Ride average" to (state.avgNoiseDb?.let { "${it.toInt()} dB" } ?: "-"),
                            "Pressure" to (state.pressureHpa?.let { fmt("%.1f hPa", it.toDouble()) } ?: "no barometer data")
                        )
                    )
                }
            }

            finished?.let { trip ->
                item {
                    ReadoutCard(
                        "Last recording",
                        listOf(
                            "Duration" to duration(trip.durationMs),
                            "Distance" to fmt("%.2f km", trip.distanceKm),
                            "Average speed" to fmt("%.0f km/h", trip.avgSpeedKmh),
                            "Bumpiness (RMS)" to "${fmt("%.2f", trip.motion.bumpinessIndex)} m/s²",
                            "Bumps / sharp turns" to "${trip.motion.bumpCount} / ${trip.motion.sharpTurnCount}",
                            "Noise average" to "${trip.noise.averageDb.toInt()} dB (${trip.noise.level.label})",
                            "Route points" to trip.route.size.toString(),
                            "Pressure samples" to trip.pressure.size.toString()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadoutCard(title: String, rows: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(14.dp)
    ) {
        PosterHeading(title, fontSize = 20.sp)
        Column(Modifier.padding(top = 8.dp)) {
            rows.forEach { (label, value) -> PosterRow(label, value) }
        }
    }
}

private fun fmt(pattern: String, value: Double): String = String.format(Locale.US, pattern, value)

private fun duration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
