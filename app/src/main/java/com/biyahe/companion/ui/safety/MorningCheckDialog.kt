package com.biyahe.companion.ui.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.safety.SafetyStore
import com.biyahe.companion.safety.SleepMath
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed

private enum class Step { ASK, HOURS, RESULT }

/**
 * "Good morning! Did you sleep well?" Asked once each morning before driving (and from the
 * Safety tab). A drowsiness check that needs no camera and no looking at the phone on the road.
 */
@Composable
fun MorningCheckDialog(store: SafetyStore, onDone: () -> Unit) {
    val settings = store.settings.value
    val planned = settings.plannedSleepHours
    var step by remember { mutableStateOf(Step.ASK) }
    var hours by remember { mutableStateOf(planned) }
    var rested by remember { mutableStateOf(true) }

    fun finish(h: Double, ok: Boolean) {
        hours = h; rested = ok
        store.saveSleepCheck(h, ok)
        step = Step.RESULT
    }

    AlertDialog(
        onDismissRequest = { if (step == Step.RESULT) onDone() },
        title = {
            Text(
                when (step) {
                    Step.ASK -> "GOOD MORNING! DID YOU SLEEP WELL?"
                    Step.HOURS -> "ABOUT HOW MANY HOURS?"
                    Step.RESULT -> if (rested && hours >= SleepMath.SHORT_SLEEP_H) "YOU'RE GOOD TO GO" else "TAKE IT EASY TODAY"
                },
                style = PosterDisplay.copy(fontSize = 24.sp), color = Ink
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    Step.ASK -> {
                        Text(
                            "Your sleep schedule: ${SleepMath.formatTime(settings.bedtimeMin)} to ${SleepMath.formatTime(settings.wakeMin)} " +
                                "(${SleepMath.formatHours(planned)}).",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        PosterButton("Yes, I'm rested", { finish(planned, true) }, Modifier.fillMaxWidth())
                        PosterButton("Not really", { step = Step.HOURS }, Modifier.fillMaxWidth(), danger = true)
                    }
                    Step.HOURS -> {
                        listOf(listOf(3, 4, 5), listOf(6, 7, 8)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { h ->
                                    PosterButton(if (h == 8) "8+" else "$h h", { finish(h.toDouble(), false) },
                                        Modifier.weight(1f), compact = true)
                                }
                            }
                        }
                    }
                    Step.RESULT -> {
                        val short = hours < SleepMath.SHORT_SLEEP_H
                        Text(
                            "Sleep: ${SleepMath.formatHours(hours)}",
                            style = PosterDisplay.copy(fontSize = 28.sp),
                            color = if (short || !rested) PosterRed else ComfortSmooth
                        )
                        Text(
                            if (short || !rested) {
                                "Short or poor sleep makes drowsy driving much more likely. If you drive today, take a break " +
                                    "every hour, or let someone else drive. The Trip screen will remind you."
                            } else {
                                "Enjoy the ride. Your speed limit and crash alerts are on."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text("Your answer stays on this phone.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                    }
                }
            }
        },
        confirmButton = {
            if (step == Step.RESULT) TextButton(onClick = onDone) { Text("OK") }
            else TextButton(onClick = onDone) { Text("Ask me later") }
        }
    )
}
