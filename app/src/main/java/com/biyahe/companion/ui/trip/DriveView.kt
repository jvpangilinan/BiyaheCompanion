package com.biyahe.companion.ui.trip

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.safety.EyeWatchState
import com.biyahe.companion.safety.GuardLogEntry
import com.biyahe.companion.ui.safety.EyeStatusTag
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.tracking.RecorderState
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.util.formatDuration
import kotlinx.coroutines.launch

/**
 * The driving screen: readable in a half-second glance from a dashboard mount.
 * Huge speed, the speed-limit sign, the latest warning, and a press-and-hold End button.
 * Nothing to read, nothing to tap while moving; the screen stays on.
 */
@Composable
fun DriveView(
    live: RecorderState,
    rainMode: Boolean,
    latest: GuardLogEntry?,
    ending: Boolean,
    eye: EyeWatchState,
    onEnd: () -> Unit,
    onShowMap: () -> Unit
) {
    KeepScreenOn()
    val over = live.overLimit
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top: mode, time, GPS, and a way back to the map view.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DRIVING", color = PosterWhite, style = PosterDisplay.copy(fontSize = 18.sp, letterSpacing = 2.sp),
                modifier = Modifier.background(PosterRed).padding(horizontal = 10.dp, vertical = 2.dp))
            Text("  ${formatDuration(live.elapsedMs)}  ·  ${live.gpsStatus.label}  ", color = Color(0xFFBBBBBB), fontSize = 15.sp,
                modifier = Modifier.weight(1f))
            EyeStatusTag(eye)
            Spacer(Modifier.size(8.dp))
            Text("MAP", color = PosterWhite, style = PosterDisplay.copy(fontSize = 16.sp),
                modifier = Modifier.border(2.dp, PosterWhite, RectangleShape).clickable(onClick = onShowMap)
                    .padding(horizontal = 14.dp, vertical = 8.dp))
        }

        // Middle: the speed, huge, red when too fast; the limit sign beside it.
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.0f".format(live.speedKmh), color = if (over) PosterRed else PosterWhite,
                    style = PosterDisplay.copy(fontSize = 150.sp), textAlign = TextAlign.Center)
                Text("KM/H", color = Color(0xFFBBBBBB), style = PosterDisplay.copy(fontSize = 22.sp, letterSpacing = 4.sp))
            }
            live.speedLimitKmh?.let { limit ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LimitSign(limit, 108.dp)
                    if (rainMode) Text("RAIN", color = ComfortFair, style = PosterDisplay.copy(fontSize = 18.sp),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // The one thing to know right now.
        when {
            over -> Banner("SLOW DOWN", PosterRed)
            latest != null && System.currentTimeMillis() - latest.timeMs < 30_000L -> Banner(latest.title.uppercase(), ComfortFair, latest.text)
            else -> Spacer(Modifier.height(4.dp))
        }

        HoldToEndButton(if (ending) "SAVING..." else "HOLD TO END TRIP", enabled = !ending, onConfirmed = onEnd, height = 88.dp)
    }
}

/** A round speed-limit sign: white with a thick red ring. */
@Composable
private fun LimitSign(limit: Int, size: Dp) {
    Box(
        Modifier.size(size).background(PosterWhite, CircleShape).border(size * 0.11f, PosterRed, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(limit.toString(), color = Ink, style = PosterDisplay.copy(fontSize = (size.value * 0.36f).sp)) }
}

@Composable
private fun Banner(title: String, color: Color, text: String? = null) {
    Column(Modifier.fillMaxWidth().background(color).padding(12.dp)) {
        Text(title, color = Ink, style = PosterDisplay.copy(fontSize = 28.sp))
        if (text != null) Text(text, color = Ink, fontSize = 16.sp, maxLines = 2)
    }
}

/**
 * Press and hold for 1.5 s to confirm (a fill sweeps across). A quick tap at a stoplight does nothing,
 * so a trip is never ended by accident.
 */
@Composable
fun HoldToEndButton(text: String, enabled: Boolean, onConfirmed: () -> Unit, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(if (enabled) PosterRed else PosterRed.copy(alpha = 0.45f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(1_500, easing = LinearEasing))
                        onConfirmed()
                        progress.snapTo(0f)
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(Ink.copy(alpha = 0.45f)))
        Text(text, color = PosterWhite, style = PosterDisplay.copy(fontSize = 24.sp, letterSpacing = 1.5.sp))
    }
}

/** Keeps the screen awake while this is shown (the driving view). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** "Driving or riding?" asked when a trip starts. */
@Composable
fun TripModeDialog(onPick: (TripMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WHO ARE YOU TODAY?", style = PosterDisplay.copy(fontSize = 24.sp), color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeCard("I'm driving", "Big driving screen. Speed, lane and safety warnings are spoken.", Ink) { onPick(TripMode.DRIVING) }
                ModeCard("I'm riding", "A passenger in a jeepney, bus or car. Comfort score; if the vehicle speeds, it's noted quietly.", PosterRed) {
                    onPick(TripMode.RIDING)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModeCard(title: String, body: String, fill: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(fill).clickable(onClick = onClick).padding(14.dp)) {
        Text(title.uppercase(), color = PosterWhite, style = PosterDisplay.copy(fontSize = 22.sp))
        Text(body, color = PosterWhite, fontSize = 15.sp, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Samsung and other phones stop background apps to save battery, which would stop a trip recording
 * with the screen off. Asked once, before the first trip.
 */
object BatteryCheck {
    private const val PREFS = "biyahe_mode"
    private const val KEY = "battery_asked"

    fun shouldAsk(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val asked = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
        return !asked && !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun markAsked(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()

    @SuppressLint("BatteryLife")
    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@Composable
fun BatteryDialog(onAllow: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("KEEP RECORDING WITH THE SCREEN OFF", style = PosterDisplay.copy(fontSize = 22.sp), color = Ink) },
        text = {
            Text("Your phone may stop apps in the background to save battery, and the trip would stop recording. " +
                "Tap Allow, then choose \"Allow\" on the next screen. You only need to do this once.")
        },
        confirmButton = { TextButton(onClick = onAllow) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Not now") } }
    )
}
