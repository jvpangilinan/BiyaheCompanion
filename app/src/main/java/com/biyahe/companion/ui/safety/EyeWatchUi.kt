package com.biyahe.companion.ui.safety

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.safety.EyeCalibration
import com.biyahe.companion.safety.EyeCamera
import com.biyahe.companion.safety.EyeStatus
import com.biyahe.companion.safety.EyeWatchState
import com.biyahe.companion.safety.TripMode
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import java.util.Locale

// ---------------------------------------------------------------- camera

/**
 * Front camera feeding [onFrame]. With [showPreview] the picture is shown too (setup and test);
 * without it nothing is drawn (during the trip). The camera closes when this leaves the screen.
 */
@Composable
fun EyeCameraView(
    showPreview: Boolean,
    onFrame: (Long, Float?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val frame by rememberUpdatedState(onFrame)
    val error by rememberUpdatedState(onError)
    val previewView = remember(showPreview) {
        if (showPreview) PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // works inside dialogs
        } else null
    }
    DisposableEffect(owner, previewView) {
        val cam = EyeCamera(context, owner, { t, o -> frame(t, o) }, { error(it) })
        cam.start(previewView?.surfaceProvider)
        onDispose { cam.release() }
    }
    if (previewView != null) AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Drawn once at the app root, next to the crash screen: runs the eye watch camera during a
 * driving trip (on any tab) and shows the WAKE UP screen when the alarm goes off.
 * [paused] = a screen that needs the camera itself (hazard photos) is open.
 */
@Composable
fun EyeWatchHost(paused: Boolean) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val eye = container.eyeWatch
    val st by eye.state.collectAsStateWithLifecycle()
    val live by container.tripRecorder.state.collectAsStateWithLifecycle()

    val run = st.active && !st.standalone && !paused && live.isRecording && live.mode == TripMode.DRIVING
    if (run) {
        EyeCameraView(showPreview = false, onFrame = eye::onFrame, onError = { msg ->
            container.safetyGuards.logQuietly("Eye watch off", msg)
            eye.stop()
        })
    }
    if (st.alarm && !st.standalone) WakeUpOverlay(st.alarmText, eye::dismissAlarm)
}

// ---------------------------------------------------------------- setup before a driving trip

private sealed interface SetupStep {
    object Ask : SetupStep
    object NoPermission : SetupStep
    object Calibrate : SetupStep
    data class Ready(val baseline: Float) : SetupStep
    data class Failed(val reason: String) : SetupStep
}

/**
 * Asked after "I'm driving": is the phone in a holder facing you? Then a few seconds of
 * calibration. [onFinished] gets the calibrated baseline, or null to drive without eye watch.
 */
@Composable
fun EyeWatchSetupDialog(onFinished: (Float?) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf<SetupStep>(SetupStep.Ask) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        step = if (granted) SetupStep.Calibrate else SetupStep.NoPermission
    }

    FullDialog(onDismiss = { onFinished(null) }) {
        Title("EYE WATCH")
        when (val s = step) {
            SetupStep.Ask -> {
                Body("Is your phone in a holder that FACES YOU, and doesn't block your view of the road?")
                Bullet("The front camera watches your eyes. If they close or keep drooping while you drive, I'll sound an alarm and tell you to focus.")
                Bullet("Everything stays on the phone. Nothing is recorded, saved or uploaded.")
                Bullet("Works best with light on your face. Sunglasses or a dark cabin at night make it less reliable.")
                PosterButton("Yes, it faces me", {
                    if (AppPermission.CAMERA.isGranted(context)) step = SetupStep.Calibrate
                    else permission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth())
                PosterButton("No, drive without it", { onFinished(null) }, Modifier.fillMaxWidth(), danger = true)
                Small("You can switch eye watch off in Safety > More guards.")
            }
            SetupStep.NoPermission -> {
                Body("Eye watch needs the camera. You can allow it later in Android Settings > Apps > Biyahe Companion > Permissions.")
                PosterButton("Drive without eye watch", { onFinished(null) }, Modifier.fillMaxWidth())
            }
            SetupStep.Calibrate -> {
                CalibrateStep(
                    onDone = { b -> step = if (b != null) SetupStep.Ready(b) else SetupStep.Failed(CANT_SEE) },
                    onError = { step = SetupStep.Failed(it) }
                )
                PosterButton("Skip, drive without it", { onFinished(null) }, Modifier.fillMaxWidth(), danger = true)
            }
            is SetupStep.Ready -> {
                Body("Ready. Eye watch is on for this trip.")
                Small("Normal eye openness: ${pct(s.baseline)}. Warnings only sound while the vehicle is moving.")
                PosterButton("Start trip", { onFinished(s.baseline) }, Modifier.fillMaxWidth())
            }
            is SetupStep.Failed -> {
                Body(s.reason)
                PosterButton("Try again", { step = SetupStep.Calibrate }, Modifier.fillMaxWidth())
                PosterButton("Drive without it", { onFinished(null) }, Modifier.fillMaxWidth(), danger = true)
            }
        }
    }
}

/** Live preview + 4 seconds of "look at the road normally" to learn this driver's open-eye level. */
@Composable
internal fun CalibrateStep(onDone: (Float?) -> Unit, onError: (String) -> Unit) {
    val cal = remember { EyeCalibration() }
    var count by remember { mutableIntStateOf(0) }
    var lastFaceAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onDone)

    Body("Sit as you drive and look at the road for a few seconds.")
    PreviewBox {
        EyeCameraView(true, onFrame = { t, o ->
            now = t
            if (finished) return@EyeCameraView
            if (o != null) { lastFaceAt = t; cal.add(o); count = cal.count }
            if (cal.count >= CALIBRATION_FRAMES) { finished = true; done(cal.result()) }
        }, onError = onError, modifier = Modifier.fillMaxSize())
    }
    val faceOk = now != 0L && now - lastFaceAt < 1_000L
    Text(
        when {
            now == 0L -> "Opening the front camera..."
            faceOk -> "Face found. Keep looking at the road..."
            else -> "I can't see your face. Tilt the holder so your face is in the picture."
        },
        color = if (faceOk) ComfortSmooth else PosterRed, style = MaterialTheme.typography.titleMedium
    )
    LinearProgressIndicator(
        progress = { (count.toFloat() / CALIBRATION_FRAMES).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp), color = PosterRed, trackColor = PosterGrey.copy(alpha = 0.3f)
    )
}

// ---------------------------------------------------------------- live test (Safety tab)

/** Calibrate, then watch live with the readings on screen. No speed check, so it works sitting still. */
@Composable
fun EyeWatchTestDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val eye = (context.applicationContext as BiyaheApp).container.eyeWatch
    val st by eye.state.collectAsStateWithLifecycle()
    var baseline by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(AppPermission.CAMERA.isGranted(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    DisposableEffect(Unit) { onDispose { if (eye.state.value.testing) eye.stop() } }

    FullDialog(onDismiss = onClose) {
        Title("EYE WATCH TEST")
        val b = baseline
        when {
            !granted -> {
                Body(AppPermission.CAMERA.reason + " Eye watch needs it too.")
                PosterButton("Allow camera", { permission.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
            }
            failed != null -> {
                Body(failed!!)
                PosterButton("Try again", { failed = null }, Modifier.fillMaxWidth())
            }
            b == null -> CalibrateStep(
                onDone = { r -> if (r != null) { baseline = r; eye.startTest(r) } else failed = CANT_SEE },
                onError = { failed = it }
            )
            else -> {
                PreviewBox {
                    EyeCameraView(true, onFrame = eye::onFrame, onError = { failed = it; baseline = null; eye.stop() },
                        modifier = Modifier.fillMaxSize())
                }
                EyeStatusTag(st, big = true)
                Text(
                    "Eyes open: ${st.openness?.let { pct(it) } ?: "-"}   (normal ${pct(b)})\n" +
                        "Closed in the last minute: ${pct(st.perclos.toFloat())}",
                    style = PosterDisplay.copy(fontSize = 18.sp), color = Ink
                )
                Small("Close your eyes for about 2 seconds: the alarm goes off. Open them to stop it. " +
                    "Squint or blink slowly for a minute to get the \"eyes getting heavy\" warning.")
            }
        }
        PosterButton("Close", onClose, Modifier.fillMaxWidth(), danger = true)
    }
    if (st.alarm && st.testing) WakeUpOverlay(st.alarmText, eye::dismissAlarm)
}

// ---------------------------------------------------------------- shown while driving

/** Small tag on the driving screen: is eye watch seeing you? */
@Composable
fun EyeStatusTag(st: EyeWatchState, big: Boolean = false) {
    if (!st.active) return
    val (fill, text) = when (st.status) {
        EyeStatus.WATCHING -> ComfortSmooth to "EYES OK"
        EyeStatus.NO_FACE -> PosterGrey to "CAN'T SEE YOU"
        EyeStatus.DROWSY -> ComfortFair to "DROWSY"
        EyeStatus.ALARM -> PosterRed to "WAKE UP"
        EyeStatus.OFF -> return
    }
    Text(
        "◉ $text", color = PosterWhite,
        style = PosterDisplay.copy(fontSize = if (big) 22.sp else 14.sp, letterSpacing = 1.sp),
        modifier = Modifier.background(fill).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Flashing full screen while the eyes-closed alarm rings. Opening your eyes stops it; so does a tap. */
@Composable
fun WakeUpOverlay(text: String, onDismiss: () -> Unit) {
    BackHandler { onDismiss() }
    val flash by rememberInfiniteTransition(label = "wake").animateColor(
        initialValue = PosterRed, targetValue = Color(0xFF0B0B0B),
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse), label = "flash"
    )
    Box(
        Modifier.fillMaxSize().background(flash).clickable(onClick = onDismiss).systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("WAKE UP", color = PosterWhite, style = PosterDisplay.copy(fontSize = 84.sp), textAlign = TextAlign.Center)
            Text(text,
                color = PosterWhite, fontSize = 22.sp, textAlign = TextAlign.Center)
            Text("TAP ANYWHERE TO STOP THE ALARM", color = PosterWhite,
                style = PosterDisplay.copy(fontSize = 18.sp, letterSpacing = 1.5.sp), textAlign = TextAlign.Center,
                modifier = Modifier.border(2.dp, PosterWhite, RectangleShape).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- small pieces

@Composable
internal fun FullDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).background(Paper).border(3.dp, Ink, RectangleShape)
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

@Composable
internal fun PreviewBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(260.dp).border(2.dp, Ink, RectangleShape).clipToBounds()) { content() }
}

@Composable internal fun Title(t: String) = Text(t, style = PosterDisplay.copy(fontSize = 28.sp), color = Ink)
@Composable internal fun Body(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = Ink)
@Composable internal fun Small(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = PosterGrey)

@Composable
private fun Bullet(t: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("■", color = PosterRed, style = MaterialTheme.typography.bodyMedium)
    Text(t, style = MaterialTheme.typography.bodyMedium, color = Ink)
}

internal fun pct(v: Float) = String.format(Locale.US, "%.0f%%", v * 100f)

private const val CALIBRATION_FRAMES = 40   // ~4 seconds at 10 frames a second
internal const val CANT_SEE = "I can't see your eyes clearly. Take off sunglasses, make sure there's light on your face, " +
    "and tilt the holder so your face is in the picture."
