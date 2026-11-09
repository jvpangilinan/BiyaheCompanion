package com.biyahe.companion.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.safety.EyeSensitivity
import com.biyahe.companion.safety.RainMode
import com.biyahe.companion.safety.SafetySettings
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import java.util.Locale

/** How many of the "more guards" are switched on (for the Home tile). */
fun SafetySettings.moreGuardsOn(): Int =
    listOf(eyeWatchOn, tunnelGuardOn, rainModeOn, hazardAheadOn, breakReminderOn, loudCabinOn, mountCheckOn).count { it }

/**
 * The seven extra guards, each tied to a sensor, each with a switch and a way to try it on stage,
 * plus the SOS button.
 */
@Composable
fun MoreGuardsCard(modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val store = container.safetyStore
    val guards = container.safetyGuards
    val s by store.settings.collectAsStateWithLifecycle()
    val lux by guards.lux.collectAsStateWithLifecycle()
    val night by guards.nightMode.collectAsStateWithLifecycle()
    val rain by guards.rainActive.collectAsStateWithLifecycle()
    val tilt by guards.tilt.collectAsStateWithLifecycle()
    val lightTest by guards.lightTestOn.collectAsStateWithLifecycle()
    val mountTest by guards.mountTestOn.collectAsStateWithLifecycle()
    val recording by container.tripRecorder.state.collectAsStateWithLifecycle()
    var eyeTest by remember { mutableStateOf(false) }
    if (eyeTest) EyeWatchTestDialog(onClose = { eyeTest = false })
    var focusSession by remember { mutableStateOf(false) }
    if (focusSession) FocusSessionDialog(onClose = { focusSession = false })

    // Live tests stop when you leave the Safety tab.
    DisposableEffect(Unit) { onDispose { guards.stopLightTest(); guards.stopMountTest() } }

    Column(
        modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadow = PosterRed, shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PosterHeading("More guards", fontSize = 22.sp)
        Text(
            "Seven more ways the phone's sensors look out for you on every trip. All spoken, all automatic.",
            style = MaterialTheme.typography.bodyMedium, color = PosterGrey
        )

        Guard(
            Icons.Filled.Visibility, "Eye watch", "FRONT CAMERA", s.eyeWatchOn,
            { on -> store.update { it.copy(eyeWatchOn = on) } },
            "With the phone in a holder facing you, the front camera watches your eyes while you drive. Eyes closed too long: " +
                "a loud alarm. Eyes drooping again and again: \"stay focused, take a break\". Asked before every driving trip; " +
                "nothing is recorded or saved."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EyeSensitivity.entries.forEachIndexed { i, sens ->
                    SmallChip(sens.label, s.eyeSensitivity == i + 1, Modifier.weight(1f)) {
                        store.update { it.copy(eyeSensitivity = i + 1) }
                    }
                }
            }
            Text(
                "Alarm after eyes are closed for ${"%.1f".format(Locale.US, EyeSensitivity.fromLevel(s.eyeSensitivity).microsleepMs / 1000.0)} s.",
                style = MaterialTheme.typography.bodySmall, color = PosterGrey
            )
            if (recording.isRecording) {
                Text("End the trip to run the live test.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }
            PosterButton("Live test", { eyeTest = true }, Modifier.fillMaxWidth(), enabled = !recording.isRecording, compact = true)
            Text(
                "Not driving? Use it at your desk: study / work mode keeps you awake and locked in, with your own words and break reminders.",
                style = MaterialTheme.typography.bodySmall, color = PosterGrey
            )
            PosterButton("Study / work mode", { focusSession = true }, Modifier.fillMaxWidth(), enabled = !recording.isRecording, compact = true)
        }

        Guard(
            Icons.Filled.Nightlight, "Tunnel & night", "AMBIENT LIGHT", s.tunnelGuardOn,
            { on -> store.update { it.copy(tunnelGuardOn = on) } },
            "Sudden darkness means a tunnel; slow darkness means night. I dim the screen so it doesn't glare, and remind you: headlights on."
        ) {
            if (lightTest) {
                Text(
                    "Light now: ${lux?.let { "%.0f lux".format(Locale.US, it) } ?: "-"}" + if (night) "  ·  TUNNEL MODE" else "",
                    style = PosterDisplay.copy(fontSize = 18.sp), color = if (night) PosterRed else Ink
                )
                Text("Cover the top of the phone with your hand to drive into a tunnel; uncover it to drive out.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }
            PosterButton(if (lightTest) "Stop live test" else "Live test", {
                if (lightTest) guards.stopLightTest() else guards.startLightTest()
            }, Modifier.fillMaxWidth(), compact = true)
        }

        Guard(
            Icons.Filled.Umbrella, "Rain mode", "BAROMETER", s.rainModeOn,
            { on -> store.update { it.copy(rainModeOn = on) } },
            "Falling air pressure means rain is coming and roads get slippery, so your speed limit drops by ${RainMode.CUT_KMH} km/h until it passes."
        ) {
            if (rain) {
                Text("RAIN MODE ON · limit ${guards.effectiveLimit()?.let { "$it km/h" } ?: "off"}",
                    style = PosterDisplay.copy(fontSize = 18.sp), color = PosterRed)
            }
            PosterButton("Try it", guards::simulateRain, Modifier.fillMaxWidth(), compact = true)
        }

        Guard(
            Icons.Filled.Report, "Hazard ahead", "GPS · CAMERA · COMPASS", s.hazardAheadOn,
            { on -> store.update { it.copy(hazardAheadOn = on) } },
            "Potholes and floods you photographed are announced when you're about to reach them again: within 300 m, in the direction you're heading."
        ) {
            PosterButton("Try it", guards::tryHazardAhead, Modifier.fillMaxWidth(), compact = true)
        }

        Guard(
            Icons.Filled.DirectionsWalk, "Break reminder", "STEP COUNTER", s.breakReminderOn,
            { on -> store.update { it.copy(breakReminderOn = on) } },
            "After a long drive I remind you to rest. Stop, get out and walk a little: the step counter logs it as a real break and the timer restarts."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120).forEach { m ->
                    SmallChip(if (m % 60 == 0) "Every ${m / 60} h" else "Every $m min", s.breakEveryMin == m, Modifier.weight(1f)) {
                        store.update { it.copy(breakEveryMin = m) }
                    }
                }
            }
            PosterButton("Try it", guards::tryBreak, Modifier.fillMaxWidth(), compact = true)
        }

        Guard(
            Icons.Filled.Hearing, "Loud cabin", "MICROPHONE", s.loudCabinOn,
            { on -> store.update { it.copy(loudCabinOn = on) } },
            "85 dB or louder for 30 seconds and you may not hear horns or sirens. I'll tell you. Sound is only measured, never recorded."
        ) {
            PosterButton("Try it", guards::tryLoud, Modifier.fillMaxWidth(), compact = true)
        }

        Guard(
            Icons.Filled.PhoneAndroid, "Mount check", "ACCELEROMETER", s.mountCheckOn,
            { on -> store.update { it.copy(mountCheckOn = on) } },
            "If the phone slides or falls out of its holder while you drive, I'll tell you, so you can fix it once you've stopped (not while driving)."
        ) {
            if (mountTest) {
                Text("Tilt from the mount position: %.0f°".format(Locale.US, tilt),
                    style = PosterDisplay.copy(fontSize = 18.sp), color = if (tilt >= 40) PosterRed else Ink)
                Text("Hold the phone still for 3 seconds, then tip it over more than 40° and hold it there.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }
            PosterButton(if (mountTest) "Stop live test" else "Live test", {
                if (mountTest) guards.stopMountTest() else guards.startMountTest()
            }, Modifier.fillMaxWidth(), compact = true)
        }

        // SOS (works on the fingerprint lock screen too)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBox(Icons.Filled.Sos, on = true, warn = true)
                Column(Modifier.weight(1f)) {
                    Text("SOS", style = PosterDisplay.copy(fontSize = 19.sp), color = PosterRed)
                    Text("FINGERPRINT LOCK SCREEN", style = MaterialTheme.typography.labelSmall, color = PosterGrey)
                }
            }
            Text(
                "Need help but no crash? Press SOS: 10 seconds to cancel, then your emergency contacts get your location. " +
                    "It's also on the fingerprint lock screen, so anyone can use it without unlocking.",
                style = MaterialTheme.typography.bodyMedium, color = Ink
            )
            Box(
                Modifier.fillMaxWidth().height(64.dp).background(PosterRed)
                    .clickable { container.crashMonitor.trigger("SOS button", countdownS = 10) },
                contentAlignment = Alignment.Center
            ) { Text("SOS · ALERT MY CONTACTS", color = PosterWhite, style = PosterDisplay.copy(fontSize = 22.sp)) }
        }

        Text(
            "Also: pocket mode (proximity sensor) rests the screen when the phone is in a pocket. Try it in Sensor Check.",
            style = MaterialTheme.typography.bodySmall, color = PosterGrey
        )
    }
}

@Composable
private fun Guard(
    icon: ImageVector, title: String, sensor: String, on: Boolean, onToggle: (Boolean) -> Unit,
    description: String, extra: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBox(icon, on)
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = PosterDisplay.copy(fontSize = 19.sp), color = Ink)
                Text(sensor, style = MaterialTheme.typography.labelSmall, color = if (on) ComfortSmooth else PosterGrey)
            }
            Switch(
                checked = on, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PosterWhite, checkedTrackColor = PosterRed,
                    uncheckedThumbColor = PosterWhite, uncheckedTrackColor = PosterGrey, uncheckedBorderColor = Ink
                )
            )
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = Ink)
        extra()
    }
}

@Composable
private fun IconBox(icon: ImageVector, on: Boolean, warn: Boolean = false) {
    Box(
        Modifier.size(40.dp).background(if (warn) PosterRed else if (on) Ink else PosterGrey),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = PosterWhite, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Ink else PosterWhite)
            .border(2.dp, Ink, RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) PosterWhite else Ink, style = MaterialTheme.typography.labelLarge) }
}
