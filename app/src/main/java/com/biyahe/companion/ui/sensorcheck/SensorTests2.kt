package com.biyahe.companion.ui.sensorcheck

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.scoring.RainRisk
import com.biyahe.companion.scoring.WeatherAssessment
import com.biyahe.companion.scoring.WeatherWatcher
import com.biyahe.companion.security.Availability
import com.biyahe.companion.tracking.GpsStatus
import com.biyahe.companion.tracking.NoiseLevel
import com.biyahe.companion.tracking.PressureSample
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.util.findActivity
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Batch 2 of the sensor tests: GPS, barometer, microphone, step counter, camera, fingerprint.
// Same template and helpers as SensorTests.kt (TestCard, CheckRow, LineGraph ...).

/** Shows [content] once the permission is granted; until then a card with an Allow button. */
@Composable
internal fun PermissionGate(permission: AppPermission, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(permission.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = permission.isGranted(context)
    }
    if (granted) {
        content()
    } else {
        TestCard("Permission needed") {
            Text(permission.reason, style = MaterialTheme.typography.bodyLarge, color = Ink)
            PosterButton("Allow ${permission.title}", { launcher.launch(permission.requestList.toTypedArray()) })
            Caption("If nothing happens, open Android Settings > Apps > Biyahe Companion > Permissions.")
        }
    }
}

// ------------------------------------------------------------------ GPS

@Composable
internal fun GpsTest(vm: SensorCheckViewModel) = PermissionGate(AppPermission.LOCATION) { GpsPanel(vm) }

@Composable
private fun GpsPanel(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var fixes by remember { mutableIntStateOf(0) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var accuracy by remember { mutableFloatStateOf(-1f) }
    var speedKmh by remember { mutableFloatStateOf(-1f) }
    var moved by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Float>() }
    val gpsOn = vm.gpsEnabled()

    LaunchedEffect(resetKey) {
        fixes = 0; lat = null; lon = null; accuracy = -1f; speedKmh = -1f; moved = false
        history.clear()
        vm.gpsFlow().collect { p ->
            fixes++
            lat = p.lat; lon = p.lon
            accuracy = if (p.accuracyM == Float.MAX_VALUE) -1f else p.accuracyM
            speedKmh = if (p.speedMps >= 0f) p.speedMps * 3.6f else -1f
            if (speedKmh >= 2f) moved = true
            history.add(max(speedKmh, 0f)); if (history.size > GRAPH_POINTS) history.removeAt(0)
        }
    }

    val goodAccuracy = accuracy in 0f..25f
    val status = when {
        !gpsOn -> GpsStatus.OFF
        fixes == 0 -> GpsStatus.SEARCHING
        goodAccuracy -> GpsStatus.GOOD
        else -> GpsStatus.WEAK
    }

    TestCard("Live reading") {
        Text(
            if (speedKmh < 0f) "- km/h" else "${speedKmh.toInt()} km/h",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 40.sp), color = Ink
        )
        LineGraph(history, maxValue = 60f)
        Caption("Speed in km/h, one reading per second.")
        if (!gpsOn) {
            Text("Location is switched off. Turn it on in the phone's quick settings.", color = PosterRed, style = MaterialTheme.typography.titleMedium)
        }
        PosterRow("Signal", status.label)
        PosterRow("Accuracy", if (accuracy < 0f) "-" else "±${accuracy.toInt()} m")
        PosterRow("Latitude", lat?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "-")
        PosterRow("Longitude", lon?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "-")
        PosterRow("Fixes received", fixes.toString())
    }
    TestCard("Result") {
        CheckRow("GPS is switched on", gpsOn)
        CheckRow("First fix received", fixes >= 1)
        CheckRow("Good accuracy (25 m or better)", goodAccuracy)
        CheckRow("Speed detected (walk or ride a little)", moved)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ barometer

@Composable
internal fun BarometerTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var hpa by remember { mutableFloatStateOf(-1f) }
    val history = remember { mutableStateListOf<Float>() }
    var demo by remember { mutableStateOf<WeatherAssessment?>(null) }

    LaunchedEffect(resetKey) {
        history.clear(); hpa = -1f
        var last = 0L
        vm.pressureFlow().collect { v ->
            hpa = v
            val now = System.currentTimeMillis()
            if (now - last >= 250) {
                last = now
                history.add(v); if (history.size > GRAPH_POINTS) history.removeAt(0)
            }
        }
    }

    // The app's real rain rule, fed with made-up readings over 30 minutes.
    fun runDemo(dropPerHour: Double) {
        val base = if (hpa > 0f) hpa else 1013f
        val samples = (0..30 step 5).map { m ->
            PressureSample(m * 60_000L, (base - dropPerHour * m / 60.0).toFloat())
        }
        demo = WeatherWatcher.assess(samples)
    }

    val lo = history.minOrNull() ?: 1000f
    val hi = history.maxOrNull() ?: 1020f
    val d = demo

    TestCard("Live reading") {
        Text(
            if (hpa < 0f) "waiting..." else "${fmt(hpa)} hPa",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 36.sp), color = Ink
        )
        LineGraph(history, minValue = lo - 0.3f, maxValue = hi + 0.3f)
        Caption("Air pressure, zoomed in so small changes show.")
    }
    TestCard("Rain warning demo") {
        Caption("Pretend the pressure has been changing for 30 minutes. The app's real rain rule gives its verdict.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosterButton("Steady", { runDemo(0.0) }, Modifier.weight(1f), compact = true)
            PosterButton("Falling", { runDemo(1.5) }, Modifier.weight(1f), compact = true)
            PosterButton("Fast drop", { runDemo(3.0) }, Modifier.weight(1f), compact = true)
        }
        if (d != null) {
            PosterRow("Pressure change", "${fmt(d.changeHpaPerHour.toFloat())} hPa/h")
            PosterRow("Rain warning", d.risk.label)
            Text(
                d.message,
                color = if (d.risk == RainRisk.NONE) Ink else PosterRed,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", hpa > 0f)
        CheckRow("Pressure looks real (850 to 1085 hPa)", hpa in 850f..1085f)
        CheckRow("Rain rule demo run", d != null)
        PosterButton("Reset", { resetKey++; demo = null }, compact = true)
    }
}

// ------------------------------------------------------------------ microphone

@Composable
internal fun MicrophoneTest(vm: SensorCheckViewModel) = PermissionGate(AppPermission.MICROPHONE) { MicPanel(vm) }

@Composable
private fun MicPanel(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var db by remember { mutableFloatStateOf(0f) }
    var avg by remember { mutableFloatStateOf(0f) }
    var quiet by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var loud by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(resetKey) {
        count = 0; db = 0f; avg = 0f; quiet = Float.MAX_VALUE; loud = 0f
        history.clear()
        var sum = 0.0
        vm.noiseFlow().collect { v ->
            count++
            sum += v
            db = v.toFloat()
            avg = (sum / count).toFloat()
            quiet = min(quiet, db); loud = max(loud, db)
            history.add(db); if (history.size > GRAPH_POINTS) history.removeAt(0)
        }
    }

    TestCard("Live reading") {
        Text(
            if (count == 0) "waiting..." else "${db.toInt()} dB",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 40.sp), color = Ink
        )
        LineGraph(history, maxValue = 100f, thresholds = listOf(65f, 78f))
        Caption("Approximate dB, four readings a second. Red lines: Low under 65, High from 78.")
        PosterRow("Average", if (count == 0) "-" else "${avg.toInt()} dB")
        PosterRow("Noise level (average)", if (count == 0) "-" else NoiseLevel.from(avg.toDouble()).label)
        PosterRow("Quietest", if (count == 0) "-" else "${quiet.toInt()} dB")
        PosterRow("Loudest", if (count == 0) "-" else "${loud.toInt()} dB")
        Caption("Sound is analysed in memory and thrown away. Nothing is recorded or saved.")
    }
    TestCard("Result") {
        CheckRow("Microphone is listening", count > 0)
        CheckRow("Level reacts to sound (talk or clap)", count > 0 && loud - quiet >= 8f)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ step counter

@Composable
internal fun StepsTest(vm: SensorCheckViewModel) = PermissionGate(AppPermission.STEPS) { StepsPanel(vm) }

@Composable
private fun StepsPanel(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var total by remember { mutableFloatStateOf(-1f) }
    var first by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(resetKey) {
        total = -1f; first = -1f
        vm.stepsFlow().collect { v ->
            total = v
            if (first < 0f) first = v
        }
    }
    val walked = if (first < 0f) 0 else (total - first).toInt().coerceAtLeast(0)

    TestCard("Live reading") {
        Text(
            if (total < 0f) "waiting..." else "$walked steps",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 40.sp), color = Ink
        )
        Caption("Steps counted since you opened this test.")
        PosterRow("Counter since phone restart", if (total < 0f) "-" else total.toLong().toString())
        PosterRow("Steps this test", walked.toString())
        Caption("Some phones report steps in small batches, so keep walking for a few seconds.")
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", total >= 0f)
        CheckRow("Steps counted (walk 10 steps)", walked >= 10)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ camera

@Composable
internal fun CameraTest() = PermissionGate(AppPermission.CAMERA) { CameraPanel() }

@Composable
private fun CameraPanel() {
    val context = LocalContext.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var shotKb by remember { mutableIntStateOf(-1) }

    TestCard("Live reading") {
        Box(Modifier.fillMaxWidth().height(260.dp).border(2.dp, Ink, RectangleShape).clipToBounds()) {
            CameraTestPreview(Modifier.fillMaxWidth().height(260.dp), onReady = { capture = it }, onError = { error = it })
        }
        error?.let { Text(it, color = PosterRed, style = MaterialTheme.typography.titleMedium) }
        Caption("Nothing is saved. The test photo is deleted as soon as it has been taken.")
    }
    TestCard("Result") {
        CheckRow("Camera opens (live picture)", capture != null)
        CheckRow("Photo can be taken", shotKb >= 0)
        if (shotKb >= 0) PosterRow("Test photo size", "$shotKb KB (deleted)")
        PosterButton(
            text = if (busy) "Taking photo..." else "Take test photo",
            onClick = {
                val imageCapture = capture ?: return@PosterButton
                busy = true
                val file = File(context.cacheDir, "sensor_test.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            shotKb = (file.length() / 1024).toInt()
                            runCatching { file.delete() }
                            busy = false
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                            error = "Could not take the photo. Try again."
                        }
                    }
                )
            },
            enabled = capture != null && !busy,
            compact = true
        )
    }
}

/** Live back-camera preview. Releases the camera as soon as the test screen is left. */
@Composable
private fun CameraTestPreview(modifier: Modifier, onReady: (ImageCapture) -> Unit, onError: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose { runCatching { providerRef[0]?.unbindAll() } }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    providerRef[0] = provider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    onReady(imageCapture)
                } catch (e: Exception) {
                    onError("Camera is not available on this device.")
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

// ------------------------------------------------------------------ fingerprint

@Composable
internal fun FingerprintTest(vm: SensorCheckViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    var availability by remember { mutableStateOf(vm.authAvailability()) }
    var accepted by remember { mutableIntStateOf(0) }
    var rejected by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    val status = when (availability) {
        Availability.AVAILABLE -> "Ready"
        Availability.NONE_ENROLLED -> "No fingerprint added yet"
        Availability.NO_HARDWARE -> "No sensor on this phone"
        else -> "Not available right now"
    }
    val sensorFound = availability == Availability.AVAILABLE || availability == Availability.NONE_ENROLLED

    TestCard("Live reading") {
        PosterRow("Fingerprint sensor", status)
        PosterRow("Fingerprints accepted", accepted.toString())
        PosterRow("Not recognised", rejected.toString())
        message?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink) }
        Caption("This only tests the sensor. It does not unlock or lock anything in the app.")
    }
    TestCard("Result") {
        CheckRow("Fingerprint sensor found", sensorFound)
        CheckRow("A fingerprint is registered on this phone", availability == Availability.AVAILABLE)
        CheckRow("Fingerprint accepted", accepted >= 1)
        when (availability) {
            Availability.AVAILABLE -> PosterButton("Test fingerprint", {
                activity?.let {
                    vm.authenticator.authenticate(
                        it, "Biyahe Companion test", "Touch the fingerprint sensor",
                        onSuccess = { accepted++; message = "Accepted" },
                        onFailedAttempt = { rejected++; message = "Not recognised. Try again." },
                        onError = { cancelled, text -> message = if (cancelled) "Cancelled" else text }
                    )
                }
            })
            Availability.NONE_ENROLLED -> PosterButton("Add a fingerprint", {
                runCatching { context.startActivity(vm.authenticator.enrollmentIntent()) }
            })
            else -> Unit
        }
        PosterButton("Check again", { availability = vm.authAvailability() }, compact = true)
    }
}
