package com.biyahe.companion.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.data.local.entity.AppSettings
import com.biyahe.companion.ui.components.DemoModeCard
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.util.formatDate
import kotlinx.coroutines.launch

/** The rider app's Settings: account, security, cloud, screen and app. Safety settings live in the Safety tab. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit, onOpenSafety: () -> Unit, onReadRules: () -> Unit, onReplayTour: () -> Unit,
    onSensorCheck: () -> Unit, onRecorderTest: () -> Unit, onTuning: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container
    val account by container.accounts.account.collectAsStateWithLifecycle()
    val app by container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
    val sync by container.cloudSync.state.collectAsStateWithLifecycle()
    val recording by container.tripRecorder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var editName by remember { mutableStateOf(false) }
    var editPassword by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var wipeResult by remember { mutableStateOf<String?>(null) }

    fun update(t: (AppSettings) -> AppSettings) = scope.launch { container.settingsRepository.updateSettings(t) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            PosterHeading("Settings", fontSize = 26.sp)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Account ----------------------------------------------------------------------------
            Section("Account") {
                val a = account
                if (a != null) {
                    Text(a.name.uppercase(), style = PosterDisplay.copy(fontSize = 22.sp), color = Ink)
                    Text("@${a.username}" + if (a.demo) " · demo" else "", style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
                    Text("Your trips and hazard reports are saved to this account.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PosterButton("Change name", { editName = true }, Modifier.weight(1f), compact = true)
                        PosterButton("Change password", { editPassword = true }, Modifier.weight(1f), compact = true,
                            enabled = !container.accounts.isBuiltIn(a.username) && !container.accounts.isGoogle(a.username))
                    }
                    if (container.accounts.isGoogle(a.username)) {
                        Text("Signed in with Google. Your password is managed by Google.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                    }
                    if (container.accounts.isBuiltIn(a.username)) {
                        Text("Built-in demo accounts keep their passwords.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                    }
                    PosterButton(if (recording.isRecording) "End the trip to log out" else "Log out", { confirmLogout = true },
                        Modifier.fillMaxWidth(), danger = true, enabled = !recording.isRecording)
                    TextButton(onClick = { confirmWipe = true }, enabled = !recording.isRecording) {
                        Text("DELETE MY TRIPS AND REPORTS", color = PosterRed, style = MaterialTheme.typography.labelLarge)
                    }
                    wipeResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink) }
                }
            }

            // ---- Security ---------------------------------------------------------------------------
            Section("Security") {
                Toggle("Fingerprint lock", "Ask for your fingerprint when the app opens or after a minute in the background.",
                    app.fingerprintLockEnabled) { on -> update { it.copy(fingerprintLockEnabled = on) } }
            }

            // ---- Cloud -----------------------------------------------------------------------------------
            Section("Cloud") {
                Toggle("Auto sync", "Upload trips and hazard reports to your account after each trip and report.",
                    app.cloudSyncEnabled) { on -> update { it.copy(cloudSyncEnabled = on) } }
                if (sync.message.isNotBlank()) Text(sync.message, style = MaterialTheme.typography.bodyMedium, color = Ink)
                if (sync.lastSyncMs > 0) Text("Last synced: ${formatDate(sync.lastSyncMs)}", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                PosterButton(if (sync.running) "Syncing..." else "Sync now", { container.cloudSync.restoreThenSync() },
                    Modifier.fillMaxWidth(), compact = true, enabled = !sync.running)
            }

            // ---- Screen ----------------------------------------------------------------------------------
            Section("Screen") {
                Toggle("Auto brightness", "Light sensor: dims in the dark, brightens in the sun (Sensor Check screen).",
                    app.autoBrightness) { on -> update { it.copy(autoBrightness = on) } }
                Toggle("Pocket mode", "Proximity sensor: rests the screen in a pocket (Sensor Check screen).",
                    app.pauseScreenInPocket) { on -> update { it.copy(pauseScreenInPocket = on) } }
                Text("Tunnel & night dimming during trips is in Safety → More guards.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }

            // ---- Safety shortcuts --------------------------------------------------------------------------
            Section("Safety") {
                PosterButton("Safety settings", onOpenSafety, Modifier.fillMaxWidth(), compact = true)
                PosterButton("Read the safety rules", onReadRules, Modifier.fillMaxWidth(), compact = true)
                if (container.isDemo) PosterButton("Replay Chanelly's tour", onReplayTour, Modifier.fillMaxWidth(), compact = true)
            }

            // ---- Sensitivity ------------------------------------------------------------------------------------
            Section("Sensitivity & sound") {
                Text("How easily the phone notices bumps, turns, speeding, crashes and lane changes, plus voice volume and speed. " +
                    "Includes a 10-second Calibrate for your car.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                PosterButton("Adjust sensitivity & sound", onTuning, Modifier.fillMaxWidth(), compact = true)
            }

            // ---- Tools ------------------------------------------------------------------------------------------
            Section("Tools") {
                var sampleMsg by remember { mutableStateOf<String?>(null) }
                var adding by remember { mutableStateOf(false) }
                if (!container.isDemo) {
                    PosterButton(if (adding) "Adding..." else "Add sample trips to my account", {
                        adding = true
                        scope.launch { sampleMsg = container.cloudSync.addSamplesToMyAccount(); adding = false }
                    }, Modifier.fillMaxWidth(), compact = true, enabled = !adding && !recording.isRecording)
                    Text(sampleMsg ?: "Six sample trips and a few hazard reports, saved and uploaded like real ones. " +
                        "Remove them any time with \"Delete my trips and reports\" above.",
                        style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                }
                PosterButton("Sensor Check: test all 11 sensors", onSensorCheck, Modifier.fillMaxWidth(), compact = true)
                PosterButton("Recorder test", onRecorderTest, Modifier.fillMaxWidth(), compact = true)
            }

            // ---- App -------------------------------------------------------------------------------------------
            Section("App") {
                DemoModeCard(isDemo = container.isDemo, recording = recording.isRecording)
                PosterButton("Phone permissions", {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }, Modifier.fillMaxWidth(), compact = true)
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
                Text("Biyahe Companion $version", style = PosterDisplay.copy(fontSize = 18.sp), color = Ink)
                Text("SENSORVERSE 2026 · Group 1. Uses 11 phone sensors for safer, more comfortable commutes.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editName) NameDialog(account?.name ?: "", onDismiss = { editName = false }) { n ->
        scope.launch { container.accounts.rename(n); editName = false }
    }
    if (editPassword) PasswordDialog(onDismiss = { editPassword = false }) { cur, new, done ->
        scope.launch { done(container.accounts.changePassword(cur, new).exceptionOrNull()?.message) }
    }
    if (confirmWipe) {
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmWipe = false },
            title = { Text("Delete all your trips and reports?") },
            text = {
                Text(
                    "Every trip and hazard report of @${account?.username} is deleted from this phone AND from your account in the cloud. " +
                        "This can't be undone. Your account, settings and emergency contacts stay."
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val err = container.cloudSync.deleteAllMyData()
                        wipeResult = err ?: "Done: your trips and reports were deleted."
                        busy = false
                        confirmWipe = false
                    }
                }) { Text(if (busy) "Deleting..." else "Delete everything", color = PosterRed) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmWipe = false }) { Text("Cancel") } }
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out of @${account?.username}?") },
            text = { Text("Your trips and reports stay saved to this account. Log in again any time to see them.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; container.accounts.logOut() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp).fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PosterHeading(title, fontSize = 20.sp)
        content()
    }
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = PosterDisplay.copy(fontSize = 17.sp), color = Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PosterGrey)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PosterWhite, checkedTrackColor = PosterRed,
                uncheckedThumbColor = PosterWhite, uncheckedTrackColor = PosterGrey, uncheckedBorderColor = Ink
            )
        )
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change name") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSave: (String, String, (String?) -> Unit) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pw = KeyboardOptions(keyboardType = KeyboardType.Password)
    var show by remember { mutableStateOf(false) }
    val vt = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true,
                    visualTransformation = vt, keyboardOptions = pw)
                OutlinedTextField(new, { new = it }, label = { Text("New password (6+ characters)") }, singleLine = true,
                    visualTransformation = vt, keyboardOptions = pw)
                OutlinedTextField(again, { again = it }, label = { Text("New password again") }, singleLine = true,
                    visualTransformation = vt, keyboardOptions = pw)
                TextButton(onClick = { show = !show }) { Text(if (show) "HIDE PASSWORDS" else "SHOW PASSWORDS") }
                error?.let { Text(it, color = PosterRed) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && current.isNotEmpty() && new.length >= 6,
                onClick = {
                    if (new != again) { error = "The new passwords don't match."; return@TextButton }
                    busy = true; error = null
                    onSave(current, new) { err -> busy = false; if (err == null) onDismiss() else error = err }
                }
            ) { Text(if (busy) "Saving..." else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
