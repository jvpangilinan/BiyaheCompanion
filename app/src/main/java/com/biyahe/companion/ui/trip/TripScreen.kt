package com.biyahe.companion.ui.trip

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.safety.GuardLogEntry
import com.biyahe.companion.safety.SleepCheck
import com.biyahe.companion.safety.SleepMath
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.components.RouteMap
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import com.biyahe.companion.ui.util.formatDuration
import java.util.Locale

/**
 * Live trip screen: route map on top, Start/End Trip, live numbers and the rain warning below.
 * When End Trip finishes saving, it opens the Trip Summary for that trip.
 */
@Composable
fun TripScreen(
    onTripSaved: (Long) -> Unit,
    viewModel: TripViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val weather by viewModel.liveWeather.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val ending by viewModel.ending.collectAsStateWithLifecycle()
    val sleep by viewModel.sleepCheck.collectAsStateWithLifecycle()
    val safetyLog by viewModel.safetyLog.collectAsStateWithLifecycle()
    val rainMode by viewModel.rainMode.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.savedTripId.collect { onTripSaved(it) } }

    val context = androidx.compose.ui.platform.LocalContext.current
    var askMode by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<TripMode?>(null) }
    var showMap by remember { mutableStateOf(false) }

    val eye by viewModel.eyeWatch.collectAsStateWithLifecycle()
    var eyeSetupFor by remember { mutableStateOf<TripMode?>(null) }

    // Driving: the big glanceable screen (the map is one tap away).
    if (live.isRecording && live.mode == TripMode.DRIVING && !showMap) {
        DriveView(live, rainMode, safetyLog.firstOrNull(), ending, eye, onEnd = viewModel::endTrip, onShowMap = { showMap = true })
        return
    }
    if (live.isRecording) KeepScreenOn()

    fun startOrAskBattery(mode: TripMode) {
        if (BatteryCheck.shouldAsk(context)) pendingMode = mode else viewModel.startTrip(mode)
    }

    if (askMode) TripModeDialog(
        onPick = { mode ->
            askMode = false
            showMap = false
            // Driving: first ask about the phone holder and calibrate eye watch.
            if (mode == TripMode.DRIVING && viewModel.eyeWatchWanted()) eyeSetupFor = mode
            else { viewModel.setEyeWatch(null); startOrAskBattery(mode) }
        },
        onDismiss = { askMode = false }
    )
    eyeSetupFor?.let { mode ->
        com.biyahe.companion.ui.safety.EyeWatchSetupDialog(onFinished = { baseline ->
            eyeSetupFor = null
            viewModel.setEyeWatch(baseline)
            startOrAskBattery(mode)
        })
    }
    pendingMode?.let { mode ->
        BatteryDialog(
            onAllow = { BatteryCheck.markAsked(context); BatteryCheck.openSettings(context); pendingMode = null; viewModel.startTrip(mode) },
            onSkip = { BatteryCheck.markAsked(context); pendingMode = null; viewModel.startTrip(mode) }
        )
    }

    // Rebuild the line only when a new GPS point arrives, not every second.
    val points = remember(live.pointCount, live.isRecording) { viewModel.routePoints() }
    val rough = remember(live.pointCount, live.isRecording) { viewModel.roughRoutePoints() }

    Column(Modifier.fillMaxSize()) {
        RouteMap(points = points, roughRoute = rough, follow = live.isRecording, modifier = Modifier.fillMaxWidth().height(260.dp).clipToBounds())
        // Thick ink line under the map, like the poster hero.
        Box(Modifier.fillMaxWidth().height(6.dp).background(Ink))

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (live.isRecording) {
                Text(
                    if (live.mode == TripMode.DRIVING) "DRIVING" else "RIDING · passenger",
                    color = PosterWhite, style = PosterDisplay.copy(fontSize = 16.sp, letterSpacing = 2.sp),
                    modifier = Modifier.background(if (live.mode == TripMode.DRIVING) PosterRed else Ink).padding(horizontal = 10.dp, vertical = 2.dp)
                )
                if (live.mode == TripMode.DRIVING) {
                    PosterButton("Back to the driving screen", { showMap = false }, Modifier.fillMaxWidth())
                }
                HoldToEndButton(if (ending) "SAVING..." else "HOLD TO END TRIP", enabled = !ending, onConfirmed = viewModel::endTrip)
            } else {
                PosterButton("Start Trip", { askMode = true }, Modifier.fillMaxWidth().tourTarget(TourTargets.TRIP_START))
                Text(
                    "Driving? Mount the phone and start the trip BEFORE you move. Don't touch it while driving.",
                    style = MaterialTheme.typography.bodyMedium, color = PosterRed
                )
            }

            message?.let { Text(it, color = PosterRed, style = MaterialTheme.typography.titleMedium) }

            sleep?.takeIf { it.tired }?.let { SleepWarningCard(it) }

            if (live.isRecording && live.overLimit) {
                SpeedingCard(live.speedKmh, live.speedLimitKmh ?: 0)
            }

            if (safetyLog.isNotEmpty()) SafetyLogCard(safetyLog)

            if (live.isRecording) {
                if (weather.shouldWarn) {
                    Column(
                        Modifier.padding(end = 4.dp, bottom = 4.dp)
                            .posterCard(fill = Ink, shadow = PosterRed, shadowOffset = 4.dp, borderWidth = 2.dp)
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text("RAIN WATCH", color = PosterRed, style = PosterDisplay.copy(fontSize = 18.sp))
                        Text(weather.message, color = PosterWhite, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(
                    Modifier.padding(end = 4.dp, bottom = 4.dp)
                        .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    PosterHeading("Live trip", fontSize = 20.sp)
                    Column(Modifier.padding(top = 8.dp)) {
                        PosterRow("Time", formatDuration(live.elapsedMs))
                        PosterRow("Distance", String.format(Locale.US, "%.2f km", live.distanceM / 1000.0))
                        PosterRow("Speed", String.format(Locale.US, "%.0f km/h", live.speedKmh))
                        PosterRow("Speed limit", live.speedLimitKmh?.let { "$it km/h" + if (rainMode) " (rain mode)" else "" } ?: "Off")
                        if (live.speedWarnings > 0) PosterRow("Speed warnings", live.speedWarnings.toString())
                        PosterRow("Lane changes / swerves", live.weavingCount.toString())
                        PosterRow("GPS", live.gpsStatus.label)
                        PosterRow("Bumps", live.bumpCount.toString())
                        PosterRow("Noise", live.noiseDb?.let { "${it.toInt()} dB" } ?: "-")
                    }
                }
            } else {
                Text(
                    "Tap Start Trip when you board. The map follows you, and the Comfort Score appears when you end the trip.",
                    style = MaterialTheme.typography.bodyLarge, color = PosterGrey
                )
            }
        }
    }
}

/** Big red card while over the limit (the voice warning plays as well). */
@Composable
private fun SpeedingCard(speedKmh: Double, limitKmh: Int) {
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp)
            .posterCard(fill = PosterRed, shadow = Ink, shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Text("SLOW DOWN", color = PosterWhite, style = PosterDisplay.copy(fontSize = 26.sp))
        Text(
            String.format(Locale.US, "%.0f km/h in a %d km/h limit", speedKmh, limitKmh),
            color = PosterWhite, style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Shown when today's sleep check said short sleep. */
@Composable
private fun SleepWarningCard(check: SleepCheck) {
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp)
            .posterCard(fill = Ink, shadow = PosterRed, shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Text("SLEEP WARNING", color = PosterRed, style = PosterDisplay.copy(fontSize = 18.sp))
        Text(
            "You slept about ${SleepMath.formatHours(check.hours)} last night. Drowsy driving is risky: " +
                "take breaks, or let someone else drive if you can.",
            color = PosterWhite, style = MaterialTheme.typography.titleMedium
        )
    }
}

/** The guards' spoken warnings this trip, newest first. */
@Composable
private fun SafetyLogCard(entries: List<GuardLogEntry>) {
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        PosterHeading("Safety log", fontSize = 20.sp)
        val fmt = java.text.SimpleDateFormat("h:mm a", Locale.US)
        entries.take(6).forEach { e ->
            Column(Modifier.padding(top = 8.dp)) {
                Text("${fmt.format(java.util.Date(e.timeMs))} · ${e.title.uppercase()}", color = PosterRed,
                    style = MaterialTheme.typography.labelLarge)
                Text(e.text, color = Ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
