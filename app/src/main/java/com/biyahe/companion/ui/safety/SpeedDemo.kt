package com.biyahe.companion.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.safety.SafetyAlerts
import com.biyahe.companion.safety.SpeedWatcher
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import kotlinx.coroutines.delay

/**
 * STAGE DEMO: a 25-second pretend drive (GPS does not work indoors). The speed follows a script
 * but goes through the SAME SpeedWatcher rules as a real trip: 3 seconds over the limit, then the
 * spoken warning, repeated while still too fast.
 */
@Composable
fun SpeedDemo(limitKmh: Int, alerts: SafetyAlerts) {
    var running by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(0.0) }
    var warnings by remember { mutableStateOf(0) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val watcher = SpeedWatcher()
        val over = limitKmh + 15.0
        // seconds -> speed: speed up, cruise below, go over the limit, then slow down
        val script: (Int) -> Double = { s ->
            when {
                s < 5 -> limitKmh * 0.8 * s / 5.0
                s < 8 -> limitKmh * 0.8
                s < 11 -> limitKmh * 0.8 + (over - limitKmh * 0.8) * (s - 8) / 3.0
                s < 19 -> over
                s < 23 -> over - (over - (limitKmh - 10.0)) * (s - 19) / 4.0
                else -> limitKmh - 10.0
            }
        }
        val start = System.currentTimeMillis()
        for (s in 0..25) {
            speed = script(s).coerceAtLeast(0.0)
            if (watcher.update(speed, limitKmh, start + s * 1_000L)) alerts.speedWarning(limitKmh)
            warnings = watcher.warnings
            delay(1_000)
        }
        running = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("STAGE DEMO", style = PosterDisplay.copy(fontSize = 18.sp), color = PosterRed)
        Text(
            "No GPS indoors, so this plays a 25-second pretend drive through the real speed rules.",
            style = MaterialTheme.typography.bodyMedium, color = Ink
        )
        if (running || speed > 0.0) {
            val tooFast = speed > limitKmh
            Box(
                Modifier.fillMaxWidth().background(if (tooFast) PosterRed else Ink).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f km/h".format(speed), color = PosterWhite, style = PosterDisplay.copy(fontSize = 44.sp))
                    Text(
                        if (tooFast) "OVER THE $limitKmh LIMIT" else "LIMIT $limitKmh",
                        color = if (tooFast) PosterWhite else ComfortSmooth,
                        style = PosterDisplay.copy(fontSize = 18.sp), textAlign = TextAlign.Center
                    )
                    Text("Warnings: $warnings", color = PosterWhite, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        PosterButton(
            if (running) "Driving..." else "Simulate a drive",
            { warnings = 0; running = true },
            Modifier.fillMaxWidth(), compact = true, enabled = !running
        )
    }
}
