package com.biyahe.companion.ui.safety

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.safety.FocusDefaults
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.util.formatDuration
import kotlinx.coroutines.delay

private enum class FocusStep { SETUP, NO_PERMISSION, CALIBRATE, RUNNING, FAILED }

/**
 * Study / work mode: eye watch at a desk. The user types what they want to hear when their eyes
 * close ("wake up"), when they drift or look away ("lock in") and when it's break time.
 */
@Composable
fun FocusSessionDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container
    val store = container.safetyStore
    val eye = container.eyeWatch
    val saved by store.settings.collectAsStateWithLifecycle()
    val st by eye.state.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(FocusStep.SETUP) }
    var failReason by remember { mutableStateOf("") }
    var wake by remember { mutableStateOf(saved.focusWakeText) }
    var lockIn by remember { mutableStateOf(saved.focusLockInText) }
    var breakText by remember { mutableStateOf(saved.focusBreakText) }
    var breakMin by remember { mutableIntStateOf(saved.focusBreakMin) }
    var alarmOn by remember { mutableStateOf(saved.focusAlarmOn) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        step = if (granted) FocusStep.CALIBRATE else FocusStep.NO_PERMISSION
    }
    DisposableEffect(Unit) { onDispose { if (eye.state.value.focus) eye.stop() } }

    FullDialog(onDismiss = onClose) {
        Title(if (step == FocusStep.RUNNING) "LOCKED IN" else "STUDY / WORK MODE")
        when (step) {
            FocusStep.SETUP -> {
                Body("Stand the phone in front of you so the front camera sees your face. I'll keep you awake and on task.")
                Small("Type what you want to hear. Leave a box empty to use the example.")
                MessageField("When my eyes close", wake, FocusDefaults.WAKE) { wake = it }
                MessageField("When I drift off or look away", lockIn, FocusDefaults.LOCK_IN) { lockIn = it }
                MessageField("When it's break time", breakText, FocusDefaults.BREAK) { breakText = it }

                Text("BREAK EVERY", style = PosterDisplay.copy(fontSize = 16.sp), color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 25 to "25 min", 50 to "50 min", 90 to "90 min").forEach { (m, label) ->
                        Chip(label, breakMin == m, Modifier.weight(1f)) { breakMin = m }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("LOUD ALARM", style = PosterDisplay.copy(fontSize = 16.sp), color = Ink)
                        Small("When your eyes close. Turn off in a library: voice and vibration only.")
                    }
                    Switch(
                        checked = alarmOn, onCheckedChange = { alarmOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PosterWhite, checkedTrackColor = PosterRed,
                            uncheckedThumbColor = PosterWhite, uncheckedTrackColor = PosterGrey, uncheckedBorderColor = Ink
                        )
                    )
                }
                Small("Tip: put the phone just under your screen or at the top of your book. " +
                    "Looking far down at notes can look like closed eyes; if it nags you, pick Gentle in Eye watch.")
                PosterButton("Start session", {
                    store.update {
                        it.copy(
                            focusWakeText = wake.trim(), focusLockInText = lockIn.trim(), focusBreakText = breakText.trim(),
                            focusBreakMin = breakMin, focusAlarmOn = alarmOn
                        )
                    }
                    if (AppPermission.CAMERA.isGranted(context)) step = FocusStep.CALIBRATE
                    else permission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth())
            }
            FocusStep.NO_PERMISSION -> {
                Body("Study / work mode needs the camera. Allow it in Android Settings > Apps > Biyahe Companion > Permissions.")
            }
            FocusStep.CALIBRATE -> CalibrateStep(
                onDone = { b ->
                    if (b != null) { eye.startFocus(b); step = FocusStep.RUNNING }
                    else { failReason = CANT_SEE; step = FocusStep.FAILED }
                },
                onError = { failReason = it; step = FocusStep.FAILED }
            )
            FocusStep.FAILED -> {
                Body(failReason)
                PosterButton("Try again", { step = FocusStep.CALIBRATE }, Modifier.fillMaxWidth())
            }
            FocusStep.RUNNING -> RunningSession(onCameraError = { failReason = it; eye.stop(); step = FocusStep.FAILED })
        }
        PosterButton(if (step == FocusStep.RUNNING) "End session" else "Close", onClose, Modifier.fillMaxWidth(), danger = true)
    }
    if (st.alarm && st.focus) WakeUpOverlay(st.alarmText, eye::dismissAlarm)
}

@Composable
private fun RunningSession(onCameraError: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val eye = container.eyeWatch
    val st by eye.state.collectAsStateWithLifecycle()
    val s by container.safetyStore.settings.collectAsStateWithLifecycle()

    // The screen stays on during a session so the camera keeps watching.
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }

    // No picture during the session (saves battery); the camera still watches.
    EyeCameraView(showPreview = false, onFrame = eye::onFrame, onError = onCameraError)

    val elapsed = (now - st.sessionStartMs).coerceAtLeast(0)
    Text(formatDuration(elapsed), style = PosterDisplay.copy(fontSize = 64.sp), color = Ink)
    EyeStatusTag(st, big = true)
    Column(Modifier.fillMaxWidth().border(2.dp, Ink, RectangleShape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Stat("Wake-ups", st.wakeCount)
        Stat("Lock-ins", st.lockInCount)
        if (s.focusBreakMin > 0) {
            val nextBreakMs = s.focusBreakMin * 60_000L - elapsed % (s.focusBreakMin * 60_000L)
            Text("Next break in ${formatDuration(nextBreakMs)}", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
    }
    Small("The camera is watching your eyes, not recording. Keep this screen open; leaving it pauses the session.")
}

@Composable
private fun Stat(label: String, n: Int) = Row {
    Text(label, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
    Text(n.toString(), style = PosterDisplay.copy(fontSize = 20.sp), color = if (n > 0) PosterRed else Ink)
}

@Composable
private fun MessageField(label: String, value: String, example: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= 200) onChange(it) },
        label = { Text(label) }, placeholder = { Text(example) },
        modifier = Modifier.fillMaxWidth(), maxLines = 3, shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PosterRed, unfocusedBorderColor = Ink, focusedLabelColor = PosterRed, cursorColor = PosterRed
        )
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) Ink else PosterWhite).border(2.dp, Ink, RectangleShape)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) PosterWhite else Ink, style = MaterialTheme.typography.labelLarge) }
}
