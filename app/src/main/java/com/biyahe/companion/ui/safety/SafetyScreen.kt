package com.biyahe.companion.ui.safety

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import com.biyahe.companion.safety.CrashDemoLevel
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.safety.EmergencyContact
import com.biyahe.companion.safety.SafetySettings
import com.biyahe.companion.safety.SleepMath
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import java.util.Calendar

/** Speed limit presets (km/h). Based on RA 4136 and EDSA's limit; local signs always win. */
private val SPEED_PRESETS = listOf(
    30 to "City streets",
    40 to "Through streets",
    60 to "EDSA / major roads",
    80 to "Open highways"
)

/** The Safety tab: speed limit warning, sleep check, lane weaving, crash detection and emergency contacts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SafetyScreen(onMorningCheck: () -> Unit, onDriftDemo: () -> Unit, onReadRules: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container
    val store = container.safetyStore
    val settings by store.settings.collectAsStateWithLifecycle()
    val today by store.todayCheck.collectAsStateWithLifecycle()
    val demoArmed by container.crashMonitor.demoArmed.collectAsStateWithLifecycle()

    // Tapping a tile (here or on Home) scrolls to that feature's card.
    val requesters = remember { SafetySection.entries.associateWith { BringIntoViewRequester() } }
    val focus by SafetyNav.focus.collectAsStateWithLifecycle()
    LaunchedEffect(focus) {
        val target = focus ?: return@LaunchedEffect
        delay(150)
        requesters.getValue(target).bringIntoView()
        SafetyNav.focus.value = null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SafetyHeroPanel(onOpen = { SafetyNav.focus.value = it })
        PosterButton("Read the safety rules", onReadRules, Modifier.fillMaxWidth(), compact = true)

        // ---- Speed limit -------------------------------------------------------------------------
        SafetyCard(Modifier.bringIntoViewRequester(requesters.getValue(SafetySection.SPEED)).tourTarget(TourTargets.SAFETY_SPEED)) {
            ToggleHeader("Speed limit warning", settings.speedLimitOn) { on -> store.update { it.copy(speedLimitOn = on) } }
            Text(
                "Over the limit for 3 seconds: a beep, a buzz and \"You're going too fast\". Repeats every 20 seconds until you slow down.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("−5") { store.update { it.copy(speedLimitKmh = (it.speedLimitKmh - 5).coerceAtLeast(10)) } }
                Text("${settings.speedLimitKmh} km/h", style = PosterDisplay.copy(fontSize = 34.sp), color = Ink,
                    modifier = Modifier.weight(1f))
                StepButton("+5") { store.update { it.copy(speedLimitKmh = (it.speedLimitKmh + 5).coerceAtMost(150)) } }
            }
            SPEED_PRESETS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (kmh, label) ->
                        Chip("$kmh · $label", selected = settings.speedLimitKmh == kmh, modifier = Modifier.weight(1f)) {
                            store.update { it.copy(speedLimitKmh = kmh) }
                        }
                    }
                }
            }
            Text("Presets follow RA 4136 and EDSA's 60 km/h. Road signs always come first.",
                style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            PosterButton("Test the warning", { container.safetyAlerts.speedWarning(settings.speedLimitKmh) },
                Modifier.fillMaxWidth(), compact = true)
            if (container.isDemo) SpeedDemo(settings.speedLimitKmh, container.safetyAlerts)
        }

        // ---- Sleep check -------------------------------------------------------------------------
        SafetyCard(Modifier.bringIntoViewRequester(requesters.getValue(SafetySection.SLEEP)).tourTarget(TourTargets.SAFETY_SLEEP)) {
            ToggleHeader("Morning sleep check", settings.morningCheckOn) { on -> store.update { it.copy(morningCheckOn = on) } }
            Text(
                "Each morning the app asks if you slept well before you drive. Under 6 hours, the Trip screen shows a sleep warning.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
            TimeRow("Bedtime", settings.bedtimeMin) { d -> store.update { it.copy(bedtimeMin = wrap(it.bedtimeMin + d)) } }
            TimeRow("Wake up", settings.wakeMin) { d -> store.update { it.copy(wakeMin = wrap(it.wakeMin + d)) } }
            TextButton(onClick = {
                nextAlarmMinutes(context)?.let { m -> store.update { it.copy(wakeMin = m) } }
            }) {
                Text("USE MY PHONE'S NEXT ALARM" + (nextAlarmMinutes(context)?.let { " (${SleepMath.formatTime(it)})" } ?: " (none set)"),
                    style = MaterialTheme.typography.labelLarge)
            }
            val planned = settings.plannedSleepHours
            Text(
                "Planned sleep: ${SleepMath.formatHours(planned)}",
                style = PosterDisplay.copy(fontSize = 20.sp),
                color = if (planned < SleepMath.SHORT_SLEEP_H) PosterRed else ComfortSmooth
            )
            today?.let {
                Text(
                    "Today: ${SleepMath.formatHours(it.hours)}, " + if (it.tired) "short sleep warning is on" else "rested",
                    style = MaterialTheme.typography.bodyMedium, color = if (it.tired) PosterRed else Ink
                )
            }
            PosterButton("Ask me now", onMorningCheck, Modifier.fillMaxWidth(), compact = true)
        }

        // ---- Drift check (demo) --------------------------------------------------------------------
        SafetyCard(Modifier.bringIntoViewRequester(requesters.getValue(SafetySection.WEAVING)).tourTarget(TourTargets.SAFETY_DRIFT)) {
            ToggleHeader("Lane weaving warning", settings.weavingWarningOn) { on -> store.update { it.copy(weavingWarningOn = on) } }
            Text(
                "On real trips the gyroscope watches how the car turns. One lane change is normal; 5 or more in 2 minutes at 40 km/h and up " +
                    "gets a spoken \"Are you tired?\". Curves are ignored, and so is someone handling the phone. Works best with the phone mounted.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
            Text("DRIFT CHECK (STAGE DEMO)", style = MaterialTheme.typography.labelLarge, color = Ink)
            Text(
                "A concept demo: walk a straight line on a pretend road. The gyroscope and compass track your direction, the accelerometer " +
                    "counts your steps, and leaving the lane is warned out loud.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
            PosterButton("Open drift check", onDriftDemo, Modifier.fillMaxWidth(), compact = true)
        }

        // ---- Crash detection ---------------------------------------------------------------------
        SafetyCard(Modifier.bringIntoViewRequester(requesters.getValue(SafetySection.CRASH)).tourTarget(TourTargets.SAFETY_CRASH)) {
            ToggleHeader("Crash detection", settings.crashDetectionOn) { on -> store.update { it.copy(crashDetectionOn = on) } }
            Text(
                "While a trip records: a hard impact or a sudden stop from speed starts a loud alarm and a 30 second countdown. " +
                    "If you don't tap I'M OK, your emergency contacts get a text with your location, and big Call buttons appear.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
            ContactsEditor(settings) { list -> store.update { it.copy(contacts = list) } }
            PermissionRow()

            if (container.isDemo) {
                Spacer(Modifier.height(4.dp))
                val level = CrashDemoLevel.fromNumber(settings.crashDemoLevel)
                Text("STAGE DEMO", style = PosterDisplay.copy(fontSize = 18.sp), color = PosterRed)
                Text("DIFFICULTY", style = MaterialTheme.typography.labelLarge, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrashDemoLevel.entries.forEach { lvl ->
                        Chip("${lvl.number} · ${lvl.title}", selected = level == lvl, modifier = Modifier.weight(1f)) {
                            store.update { it.copy(crashDemoLevel = lvl.number) }
                            if (demoArmed) { // re-arm at the new level
                                container.crashMonitor.disarmDemo()
                                container.crashMonitor.armDemo()
                            }
                        }
                    }
                }
                Text(level.howTo, style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
                Text(
                    if (demoArmed) "Armed (level ${level.number}, ${level.title}): ${level.howTo}"
                    else "Indoors there is no GPS, so the demo uses walking speed: arm it, walk, then stop sharply. No real text is sent in demo mode.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosterButton(
                        if (demoArmed) "Disarm" else "Arm crash demo",
                        { if (demoArmed) container.crashMonitor.disarmDemo() else container.crashMonitor.armDemo() },
                        Modifier.weight(1f), compact = true
                    )
                    PosterButton("Simulate crash", { container.crashMonitor.trigger("Simulated crash (stage demo)") },
                        Modifier.weight(1f), compact = true, danger = true)
                }
            }
        }
        MoreGuardsCard(Modifier.bringIntoViewRequester(requesters.getValue(SafetySection.MORE)).tourTarget(TourTargets.SAFETY_MORE))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ContactsEditor(settings: SafetySettings, onChange: (List<EmergencyContact>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Text("EMERGENCY CONTACTS", style = MaterialTheme.typography.labelLarge, color = Ink)
    if (settings.contacts.isEmpty()) {
        Text("None yet. Add at least one person to alert.", style = MaterialTheme.typography.bodyMedium, color = PosterRed)
    }
    settings.contacts.forEachIndexed { i, c ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
            }
            IconButton(onClick = { onChange(settings.contacts.filterIndexed { j, _ -> j != i }) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${c.name}", tint = Ink)
            }
        }
    }
    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
        label = { Text("Mobile number") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    PosterButton(
        "Add contact",
        {
            onChange(settings.contacts + EmergencyContact(name.trim().ifEmpty { "Contact" }, phone.replace(" ", "")))
            name = ""; phone = ""
        },
        Modifier.fillMaxWidth(), compact = true, enabled = phone.count { it.isDigit() } >= 7
    )
}

/** SMS (to alert contacts) and Call (for the big call buttons) permissions. */
@Composable
private fun PermissionRow() {
    val context = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var sms by remember { mutableStateOf(granted(Manifest.permission.SEND_SMS)) }
    var call by remember { mutableStateOf(granted(Manifest.permission.CALL_PHONE)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        sms = granted(Manifest.permission.SEND_SMS)
        call = granted(Manifest.permission.CALL_PHONE)
    }
    Text(
        "Texting contacts: ${if (sms) "allowed" else "not allowed"}  ·  Calling: ${if (call) "allowed" else "not allowed"}",
        style = MaterialTheme.typography.bodySmall, color = if (sms && call) PosterGrey else PosterRed
    )
    if (!sms || !call) {
        PosterButton("Allow texting and calling", {
            launcher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE))
        }, Modifier.fillMaxWidth(), compact = true)
    }
}

// ---- Small building blocks ------------------------------------------------------------------------

@Composable
private fun SafetyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun ToggleHeader(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PosterHeading(title, fontSize = 20.sp, modifier = Modifier.weight(1f))
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
private fun TimeRow(label: String, minutes: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.width(80.dp))
        StepButton("−") { onStep(-15) }
        Text(SleepMath.formatTime(minutes), style = PosterDisplay.copy(fontSize = 24.sp), color = Ink,
            modifier = Modifier.weight(1f))
        StepButton("+") { onStep(15) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).background(Ink).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = PosterWhite, style = PosterDisplay.copy(fontSize = 20.sp)) }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Ink else PosterWhite)
            .border(2.dp, Ink, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) PosterWhite else Ink, style = MaterialTheme.typography.labelLarge)
    }
}

private fun wrap(min: Int) = ((min % 1440) + 1440) % 1440

/** Time of the phone's next alarm (minutes after midnight), from the system alarm clock. */
private fun nextAlarmMinutes(context: Context): Int? {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val next = am.nextAlarmClock ?: return null
    val c = Calendar.getInstance().apply { timeInMillis = next.triggerTime }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
