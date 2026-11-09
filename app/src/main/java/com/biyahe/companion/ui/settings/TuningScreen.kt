package com.biyahe.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.safety.Calibration
import com.biyahe.companion.safety.Level
import com.biyahe.companion.safety.Tuning
import com.biyahe.companion.safety.TuningPresets
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Sensitivity & sound. Each feature: a simple Gentle / Normal / Sensitive choice, and Fine-tune
 * sliders in plain words. Only detection is tunable; the Comfort Score formula stays the same for everyone.
 */
@Composable
fun TuningScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val store = container.tuningStore
    val t by store.tuning.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            PosterHeading("Sensitivity & sound", fontSize = 24.sp)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Every phone, car and holder shakes differently. Adjust how easily the phone notices things. " +
                    "Changes apply from your next trip.",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )

            // ---- Comfort ---------------------------------------------------------------------------
            Section("Bumps & turns", "Accelerometer · gyroscope", TuningPresets.levelOf(t, TuningPresets::comfort),
                onLevel = { l -> store.update { TuningPresets.comfort(it, l) } }) {
                ShakeMeter(t) { rms -> store.update { Calibration.apply(it, rms) } }
                Fine("A bump counts when it's at least", "%.1f m/s²".format(Locale.US, t.bumpThreshold),
                    "Higher = only big potholes count.", t.bumpThreshold.toFloat(), 1.5f..5f) { v -> store.update { it.copy(bumpThreshold = v.round1()) } }
                Fine("Normal shaking to ignore", "%.2f m/s²".format(Locale.US, t.vibrationFloor),
                    "The engine and road always shake a little. Calibrate sets this for you.", t.vibrationFloor.toFloat(), 0f..1.5f) { v ->
                    store.update { it.copy(vibrationFloor = (v * 100).roundToInt() / 100.0) }
                }
                Fine("A sharp turn is faster than", "${t.turnDps.roundToInt()}°/s",
                    "Higher = only sudden swerves count.", t.turnDps.toFloat(), 30f..100f) { v -> store.update { it.copy(turnDps = v.roundToInt().toDouble()) } }
                Text("The Comfort Score formula never changes, so scores stay comparable between riders.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }

            // ---- Speed -----------------------------------------------------------------------------
            Section("Speed warning", "GPS", TuningPresets.levelOf(t, TuningPresets::speed),
                onLevel = { l -> store.update { TuningPresets.speed(it, l) } }) {
                Fine("Warn after being over the limit for", "${t.speedGraceSec} s",
                    "A short wait ignores GPS glitches.", t.speedGraceSec.toFloat(), 1f..10f) { v -> store.update { it.copy(speedGraceSec = v.roundToInt()) } }
                Fine("Repeat the warning every", "${t.speedRepeatSec} s",
                    "While still too fast.", t.speedRepeatSec.toFloat(), 10f..60f) { v -> store.update { it.copy(speedRepeatSec = v.roundToInt()) } }
            }

            // ---- Crash -----------------------------------------------------------------------------
            Section("Crash alert", "Accelerometer · GPS", TuningPresets.levelOf(t, TuningPresets::crash),
                onLevel = { l -> store.update { TuningPresets.crash(it, l) } }) {
                Fine("Impact strength", "%.1f g".format(Locale.US, t.crashImpactG),
                    "Lower = more sensitive, but more false alarms.", t.crashImpactG.toFloat(), 2f..5f) { v -> store.update { it.copy(crashImpactG = v.round1()) } }
                Fine("Only when going faster than", "${t.crashFromKmh.roundToInt()} km/h",
                    "Dropping the phone while parked never counts.", t.crashFromKmh.toFloat(), 10f..50f) { v -> store.update { it.copy(crashFromKmh = v.roundToInt().toDouble()) } }
                Text("The stage demo has its own Easy / Medium / Normal setting in the Safety tab.",
                    style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            }

            // ---- Weaving ---------------------------------------------------------------------------
            Section("Lane weaving", "Gyroscope", TuningPresets.levelOf(t, TuningPresets::weaving),
                onLevel = { l -> store.update { TuningPresets.weaving(it, l) } }) {
                Fine("A lane change is a swing of at least", "%.0f°".format(Locale.US, t.weaveDeg),
                    "Smaller = notices gentler lane changes.", t.weaveDeg.toFloat(), 2f..8f) { v -> store.update { it.copy(weaveDeg = v.roundToInt().toDouble()) } }
                Fine("Ask \"are you tired?\" after", "${t.weaveCount} in 2 min",
                    "How many lane changes are too many.", t.weaveCount.toFloat(), 3f..10f) { v -> store.update { it.copy(weaveCount = v.roundToInt()) } }
            }

            // ---- More guards -------------------------------------------------------------------------
            Section("More guards", "Light · microphone · accelerometer", TuningPresets.levelOf(t, TuningPresets::guards),
                onLevel = { l -> store.update { TuningPresets.guards(it, l) } }) {
                Fine("Tunnel / night when darker than", "${t.tunnelLux.roundToInt()} lux",
                    "Higher = dims the screen sooner.", t.tunnelLux, 2f..30f) { v -> store.update { it.copy(tunnelLux = v.roundToInt().toFloat()) } }
                Fine("Loud cabin from", "${t.loudDb.roundToInt()} dB",
                    "About 85 dB is heavy traffic with the windows open.", t.loudDb.toFloat(), 70f..100f) { v -> store.update { it.copy(loudDb = v.roundToInt().toDouble()) } }
                Fine("...for at least", "${t.loudSec} s", null, t.loudSec.toFloat(), 10f..120f) { v -> store.update { it.copy(loudSec = v.roundToInt()) } }
                Fine("Mount check: phone tipped more than", "${t.mountTiltDeg.roundToInt()}°",
                    "Hard braking tilts the reading about 30°, so keep this above that.", t.mountTiltDeg.toFloat(), 20f..70f) { v ->
                    store.update { it.copy(mountTiltDeg = v.roundToInt().toDouble()) }
                }
            }

            // ---- Sound ---------------------------------------------------------------------------------
            Section("Voice & sound", "Speaker", null, onLevel = null) {
                Fine("Voice volume", "${(t.voiceVolume * 100).roundToInt()}%", null, t.voiceVolume, 0.2f..1f) { v ->
                    store.update { it.copy(voiceVolume = (v * 20).roundToInt() / 20f) }
                }
                Fine("Speaking speed", "%.1fx".format(Locale.US, t.voiceRate),
                    "Slower is easier to follow, for children and older listeners too.", t.voiceRate, 0.6f..1.5f) { v ->
                    store.update { it.copy(voiceRate = (v * 10).roundToInt() / 10f) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BEEP BEFORE WARNINGS", style = PosterDisplay.copy(fontSize = 16.sp), color = Ink)
                        Text("A short beep gets attention before the voice.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
                    }
                    Switch(
                        checked = t.beepOn, onCheckedChange = { on -> store.update { it.copy(beepOn = on) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = PosterWhite, checkedTrackColor = PosterRed,
                            uncheckedThumbColor = PosterWhite, uncheckedTrackColor = PosterGrey, uncheckedBorderColor = Ink)
                    )
                }
                PosterButton("Test the voice", {
                    container.safetyAlerts.speedWarning(container.safetyStore.settings.value.speedLimitKmh)
                }, Modifier.fillMaxWidth(), compact = true)
            }

            TextButton(onClick = { store.reset() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("RESET EVERYTHING TO NORMAL", color = PosterRed, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Live: how much the phone is shaking right now against the bump line, plus Calibrate
 * (10 s with the car stopped, engine on, phone in its holder).
 */
@Composable
private fun ShakeMeter(t: Tuning, onCalibrated: (Double) -> Unit) {
    val hub = (LocalContext.current.applicationContext as BiyaheApp).container.sensorHub
    var jolt by remember { mutableDoubleStateOf(0.0) }
    var bumps by remember { mutableIntStateOf(0) }
    var calibrating by remember { mutableIntStateOf(0) }   // seconds left; 0 = not calibrating
    var calibrateResult by remember { mutableStateOf<String?>(null) }
    var sumSq by remember { mutableDoubleStateOf(0.0) }
    var n by remember { mutableIntStateOf(0) }

    LaunchedEffect(t.bumpThreshold) {
        var gx = 0.0; var gy = 0.0; var gz = 0.0; var ready = false; var lastBump = 0L; var ema = 0.0
        hub.accelerometer().collect { v ->
            if (!ready) { gx = v.x.toDouble(); gy = v.y.toDouble(); gz = v.z.toDouble(); ready = true; return@collect }
            gx = 0.8 * gx + 0.2 * v.x; gy = 0.8 * gy + 0.2 * v.y; gz = 0.8 * gz + 0.2 * v.z
            val lx = v.x - gx; val ly = v.y - gy; val lz = v.z - gz
            val j = sqrt(lx * lx + ly * ly + lz * lz)
            ema += 0.15 * (j - ema)
            jolt = maxOf(ema, j * 0.6)
            if (j >= t.bumpThreshold && v.timestampMs - lastBump > 500) { bumps++; lastBump = v.timestampMs }
            if (calibrating > 0) { sumSq += j * j; n++ }
        }
    }
    LaunchedEffect(calibrating > 0) {
        if (calibrating <= 0) return@LaunchedEffect
        while (calibrating > 0) { delay(1_000); calibrating-- }
        val rms = if (n == 0) 0.0 else sqrt(sumSq / n)
        onCalibrated(rms)
        calibrateResult = "Calibrated: normal shaking %.2f m/s², bumps count from %.1f m/s².".format(Locale.US,
            rms.coerceIn(0.0, 1.5), (rms * 4).coerceIn(2.0, 5.0))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("SHAKING RIGHT NOW", style = PosterDisplay.copy(fontSize = 15.sp), color = Ink)
        val max = 6.0
        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).border(2.dp, Ink, RectangleShape).background(PosterWhite)) {
            val frac = (jolt / max).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(if (jolt >= t.bumpThreshold) PosterRed else if (jolt >= t.vibrationFloor) ComfortFair else ComfortSmooth))
            val line = maxWidth * (t.bumpThreshold / max).toFloat().coerceIn(0f, 1f)
            Box(Modifier.offset(x = line).width(3.dp).fillMaxHeight().background(Ink))
        }
        Text("Black line = bump threshold. Bumps counted here: $bumps (tap the table or a bump to test).",
            style = MaterialTheme.typography.bodySmall, color = PosterGrey)
        PosterButton(
            if (calibrating > 0) "Measuring... $calibrating s" else "Calibrate (10 s)",
            { sumSq = 0.0; n = 0; calibrateResult = null; calibrating = 10 },
            Modifier.fillMaxWidth(), compact = true, enabled = calibrating == 0
        )
        Text(calibrateResult ?: "Car stopped, engine on, phone in its holder. Then tap Calibrate and don't touch the phone.",
            style = MaterialTheme.typography.bodySmall, color = if (calibrateResult != null) ComfortSmooth else PosterGrey)
    }
}

@Composable
private fun Section(title: String, sensors: String, level: Level?, onLevel: ((Level) -> Unit)?, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp).fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PosterHeading(title, fontSize = 20.sp)
        Text(sensors.uppercase(), style = MaterialTheme.typography.labelSmall, color = PosterGrey)
        if (onLevel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Level.entries.forEach { l ->
                    val selected = level == l
                    Box(
                        Modifier.weight(1f).background(if (selected) Ink else PosterWhite).border(2.dp, Ink, RectangleShape)
                            .clickable { onLevel(l) }.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(l.label.uppercase(), color = if (selected) PosterWhite else Ink, style = PosterDisplay.copy(fontSize = 16.sp)) }
                }
            }
            Text(
                when (level) {
                    Level.GENTLE -> "Fewer alerts. Good for rough roads or a loose holder."
                    Level.NORMAL -> "Recommended for most phones and cars."
                    Level.SENSITIVE -> "Notices more, but may give more alerts."
                    null -> "Custom: you fine-tuned this section."
                },
                style = MaterialTheme.typography.bodySmall, color = PosterGrey
            )
            TextButton(onClick = { open = !open }) {
                Text(if (open) "HIDE FINE-TUNING ▲" else "FINE-TUNE ▼", color = Ink, style = MaterialTheme.typography.labelLarge)
            }
            if (open) content()
        } else {
            content()
        }
    }
}

@Composable
private fun Fine(title: String, value: String, hint: String?, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
            Text(value, style = PosterDisplay.copy(fontSize = 18.sp), color = PosterRed)
        }
        Slider(
            value = current.coerceIn(range), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = PosterRed, activeTrackColor = PosterRed, inactiveTrackColor = PosterGrey.copy(alpha = 0.3f))
        )
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = PosterGrey)
    }
}

private fun Float.round1() = (this * 10).roundToInt() / 10.0
