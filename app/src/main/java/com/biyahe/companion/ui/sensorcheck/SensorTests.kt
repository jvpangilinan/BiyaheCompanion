package com.biyahe.companion.ui.sensorcheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.sensors.BumpLevel
import com.biyahe.companion.sensors.MotionAnalyzer
import com.biyahe.companion.sensors.ScreenCare
import com.biyahe.companion.sensors.ScreenCareState
import com.biyahe.companion.sensors.SensorKind
import com.biyahe.companion.sensors.compassLabel
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Every sensor has its own test screen now. */
val testableSensors: Set<SensorKind> = SensorKind.entries.toSet()

internal const val GRAPH_POINTS = 60
private const val BUMP_LIMIT = 2.5f   // same as MotionAnalyzer.bumpThreshold
private const val TURN_LIMIT = 1.0f   // same as MotionAnalyzer.turnThreshold

/**
 * One sensor, on its own. While this screen is open only the sensor being tested is read: the
 * list's live readings stop, and screen dimming / pocket mode are not applied (so covering the
 * proximity sensor does not black the screen out). Every number comes from the same classes
 * the trip recorder uses, so a pass here means it will work during a trip.
 */
@Composable
fun SensorTestScreen(
    kind: SensorKind,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextTitle: String?,
    viewModel: SensorCheckViewModel
) {
    BackHandler(onBack = onBack)
    val story = kind.story()

    // key(kind): moving to the next sensor starts it fresh (scroll position, counters, checks).
    key(kind) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${story.order} OF 11  ·  ${kind.title.uppercase()}",
                        style = MaterialTheme.typography.labelMedium, color = PosterRed
                    )
                    PosterHeading(story.stageTitle, fontSize = 22.sp)
                }
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(story.pitch, style = MaterialTheme.typography.titleMedium, color = Ink)
                TestCard("Try this") {
                    Text(tryThis(kind), style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
                when (kind) {
                    SensorKind.GPS -> GpsTest(viewModel)
                    SensorKind.ACCELEROMETER -> AccelerometerTest(viewModel)
                    SensorKind.GYROSCOPE -> GyroscopeTest(viewModel)
                    SensorKind.MAGNETOMETER -> MagnetometerTest(viewModel)
                    SensorKind.BAROMETER -> BarometerTest(viewModel)
                    SensorKind.MICROPHONE -> MicrophoneTest(viewModel)
                    SensorKind.STEP_COUNTER -> StepsTest(viewModel)
                    SensorKind.CAMERA -> CameraTest()
                    SensorKind.AMBIENT_LIGHT -> LightTest(viewModel)
                    SensorKind.PROXIMITY -> ProximityTest(viewModel)
                    SensorKind.FINGERPRINT -> FingerprintTest(viewModel)
                }
                TestCard("What it affects in the app") {
                    Text(affects(kind), style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
                Text(
                    "Only this sensor is being read while this screen is open.",
                    style = MaterialTheme.typography.labelMedium, color = PosterGrey
                )
                PosterButton(
                    text = if (nextTitle != null) "Next: $nextTitle" else "Finish",
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ accelerometer

@Composable
private fun AccelerometerTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var got by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var jolt by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var live by remember { mutableFloatStateOf(0f) }
    var bumps by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(resetKey) {
        val analyzer = MotionAnalyzer()   // the real bump detector the trip recorder uses
        history.clear(); bumps = 0; peak = 0f; live = 0f; jolt = 0f
        var gx = 0f; var gy = 0f; var gz = 0f
        var ready = false
        var lastUi = 0L
        var windowMax = 0f
        vm.accelRaw().collect { s ->
            analyzer.addAccelerometer(s)
            if (!ready) {
                gx = s.x; gy = s.y; gz = s.z; ready = true
            } else {
                gx = 0.8f * gx + 0.2f * s.x
                gy = 0.8f * gy + 0.2f * s.y
                gz = 0.8f * gz + 0.2f * s.z
            }
            val dx = s.x - gx; val dy = s.y - gy; val dz = s.z - gz
            windowMax = max(windowMax, sqrt(dx * dx + dy * dy + dz * dz))
            if (s.timestampMs - lastUi >= 100) {
                lastUi = s.timestampMs
                got = true
                x = s.x; y = s.y; z = s.z
                jolt = windowMax
                history.add(windowMax); if (history.size > GRAPH_POINTS) history.removeAt(0)
                windowMax = 0f
                val sum = analyzer.summary()
                bumps = sum.bumpCount
                peak = sum.peakJolt.toFloat()
                live = analyzer.liveBumpiness().toFloat()
            }
        }
    }

    TestCard("Live reading") {
        LineGraph(history, maxValue = 6f, thresholds = listOf(BUMP_LIMIT))
        Caption("Jolt in m/s² with gravity removed. Red line = bump limit ($BUMP_LIMIT).")
        PosterRow("X axis", "${fmt(x)} m/s²")
        PosterRow("Y axis", "${fmt(y)} m/s²")
        PosterRow("Z axis", "${fmt(z)} m/s²")
        PosterRow("Jolt now", fmt(jolt))
        PosterRow("Peak jolt", fmt(peak))
        PosterRow("Bumps counted", bumps.toString())
        PosterRow("Live bumpiness", "${fmt(live)} (${BumpLevel.from(live.toDouble()).label})")
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", got)
        CheckRow("Bump detected (jolt above $BUMP_LIMIT)", bumps >= 1)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ gyroscope

@Composable
private fun GyroscopeTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var got by remember { mutableStateOf(false) }
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var z by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var turns by remember { mutableIntStateOf(0) }
    var peak by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(resetKey) {
        val analyzer = MotionAnalyzer()
        history.clear(); turns = 0; peak = 0f; speed = 0f
        var lastUi = 0L
        var windowMax = 0f
        var peakSeen = 0f
        vm.gyroRaw().collect { s ->
            analyzer.addGyroscope(s)
            val m = s.magnitude.toFloat()
            windowMax = max(windowMax, m)
            peakSeen = max(peakSeen, m)
            if (s.timestampMs - lastUi >= 100) {
                lastUi = s.timestampMs
                got = true
                x = s.x; y = s.y; z = s.z
                speed = windowMax
                history.add(windowMax); if (history.size > GRAPH_POINTS) history.removeAt(0)
                windowMax = 0f
                turns = analyzer.summary().sharpTurnCount
                peak = peakSeen
            }
        }
    }

    TestCard("Live reading") {
        LineGraph(history, maxValue = 6f, thresholds = listOf(TURN_LIMIT))
        Caption("Rotation speed in rad/s. Red line = sharp-turn limit ($TURN_LIMIT).")
        PosterRow("X axis", "${fmt(x)} rad/s")
        PosterRow("Y axis", "${fmt(y)} rad/s")
        PosterRow("Z axis", "${fmt(z)} rad/s")
        PosterRow("Rotation speed", "${fmt(speed)} rad/s")
        PosterRow("Fastest so far", "${fmt(peak)} rad/s")
        PosterRow("Sharp turns counted", turns.toString())
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", got)
        CheckRow("Sharp turn detected (faster than $TURN_LIMIT rad/s)", turns >= 1)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ magnetometer

@Composable
private fun MagnetometerTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var field by remember { mutableFloatStateOf(-1f) }
    var heading by remember { mutableFloatStateOf(0f) }
    var start by remember { mutableStateOf<Float?>(null) }
    var turned by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.magnetRaw().collect { field = it.magnitude.toFloat() } }
    LaunchedEffect(resetKey) {
        start = null; turned = false
        vm.headingFlow().collect { h ->
            heading = h
            val s = start
            if (s == null) {
                start = h
            } else if (abs(((h - s + 540f) % 360f) - 180f) >= 45f) {
                turned = true
            }
        }
    }

    TestCard("Live reading") {
        Canvas(Modifier.align(Alignment.CenterHorizontally).size(150.dp)) {
            val c = center
            val r = size.minDimension / 2 - 4.dp.toPx()
            drawCircle(Ink, r, c, style = Stroke(3.dp.toPx()))
            rotate(-heading, pivot = c) {
                drawLine(PosterRed, c, Offset(c.x, c.y - r + 6.dp.toPx()), strokeWidth = 8.dp.toPx())
                drawLine(Ink, c, Offset(c.x, c.y + r * 0.6f), strokeWidth = 5.dp.toPx())
            }
        }
        Text(
            "${compassLabel(heading)}  ${heading.toInt()}°",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 30.sp), color = Ink
        )
        Caption("Red end of the needle points to magnetic north.")
        PosterRow("Field strength", if (field < 0f) "waiting..." else "${field.toInt()} µT")
        if (field > 100f) {
            Text(
                "Strong magnetic interference. Move away from magnets, speakers or metal.",
                color = PosterRed, style = MaterialTheme.typography.titleMedium
            )
        }
        Caption("The needle also reads the accelerometer to stay level. The field strength is the magnetometer alone.")
    }
    TestCard("Result") {
        CheckRow("Field strength looks normal (20 to 80 µT)", field in 20f..80f)
        CheckRow("Heading follows rotation (turn 45° or more)", turned)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ ambient light

@Composable
private fun LightTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var lux by remember { mutableFloatStateOf(-1f) }
    var minLux by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var maxLux by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(resetKey) {
        minLux = Float.MAX_VALUE; maxLux = -1f
        vm.luxFlow().collect { v ->
            lux = v
            if (v < minLux) minLux = v
            if (v > maxLux) maxLux = v
        }
    }

    val have = lux >= 0f
    val zone = when {
        !have -> -1
        lux < ScreenCareState.DARK_LUX -> 0
        lux >= ScreenCareState.SUN_LUX -> 2
        else -> 1
    }
    // The exact rule the app uses (auto-brightness on, pocket mode off for this test).
    val target = if (have) ScreenCare.targetBrightness(ScreenCareState(lux = lux), true, false) else -1f
    val brightnessText = when {
        !have -> "-"
        target < 0f -> "No change"
        target >= 1f -> "100% (bright sun)"
        else -> "${(target * 100).toInt()}% (dim)"
    }

    TestCard("Live reading") {
        Text(
            if (have) "${lux.toInt()} lux" else "waiting...",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            style = PosterDisplay.copy(fontSize = 40.sp), color = Ink
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("DARK", "NORMAL", "SUNLIGHT").forEachIndexed { i, name ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (i == zone) Ink else PosterWhite)
                        .border(2.dp, Ink, RectangleShape)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, color = if (i == zone) PosterWhite else Ink, style = PosterDisplay.copy(fontSize = 16.sp))
                }
            }
        }
        Caption("Dark is under ${ScreenCareState.DARK_LUX.toInt()} lux. Sunlight is ${ScreenCareState.SUN_LUX.toInt()} lux or more.")
        PosterRow("Auto-brightness would set", brightnessText)
        PosterRow("Darkest seen", if (maxLux < 0f) "-" else "${minLux.toInt()} lux")
        PosterRow("Brightest seen", if (maxLux < 0f) "-" else "${maxLux.toInt()} lux")
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", have)
        CheckRow("Light changed (cover it, then uncover it)", have && maxLux >= (minLux + 1f) * 3f)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ proximity

@Composable
private fun ProximityTest(vm: SensorCheckViewModel) {
    var resetKey by remember { mutableIntStateOf(0) }
    var near by remember { mutableStateOf<Boolean?>(null) }
    var sawNear by remember { mutableStateOf(false) }
    var sawFar by remember { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        sawNear = false; sawFar = false
        vm.nearFlow().collect { n ->
            near = n
            if (n) sawNear = true else sawFar = true
        }
    }

    val n = near
    TestCard("Live reading") {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(120.dp)
                .background(if (n == true) Ink else PosterWhite)
                .border(3.dp, Ink, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (n) { null -> "..."; true -> "NEAR"; false -> "FAR" },
                color = if (n == true) PosterWhite else Ink,
                style = PosterDisplay.copy(fontSize = 28.sp)
            )
        }
        Caption("NEAR = something is covering the sensor (pocket, hand, ear).")
    }
    TestCard("Result") {
        CheckRow("Sensor is sending readings", n != null)
        CheckRow("Near detected (cover the top edge)", sawNear)
        CheckRow("Far detected (uncover it)", sawFar)
        PosterButton("Reset", { resetKey++ }, compact = true)
    }
}

// ------------------------------------------------------------------ text

private fun tryThis(kind: SensorKind): String = when (kind) {
    SensorKind.ACCELEROMETER -> "Lay the phone on a table and tap the table firmly next to it, or give the phone a short shake. A tall spike that crosses the red line counts as a bump."
    SensorKind.GYROSCOPE -> "Hold the phone and twist it quickly, like flicking a steering wheel. Turning faster than the red line counts as a sharp turn."
    SensorKind.MAGNETOMETER -> "Hold the phone flat and slowly turn yourself around. The needle should keep pointing the same way while the heading changes."
    SensorKind.AMBIENT_LIGHT -> "Cover the top of the phone with your hand, then uncover it. Try pointing it at a lamp or a window too."
    SensorKind.PROXIMITY -> "Cover the top edge of the phone with your hand, then take your hand away."
    SensorKind.GPS -> "Go near a window or outside so the phone can see the sky. Wait for the first fix, then walk or ride a little and watch the speed move."
    SensorKind.BAROMETER -> "Watch the live pressure. Then tap the three demo buttons to see how the app turns falling pressure into a rain warning."
    SensorKind.MICROPHONE -> "Stay quiet for a moment, then talk or clap near the phone and watch the level jump."
    SensorKind.STEP_COUNTER -> "Walk about 10 steps with the phone in your hand or pocket."
    SensorKind.CAMERA -> "Point the camera at something and tap Take test photo. The photo is deleted right away."
    SensorKind.FINGERPRINT -> "Tap Test fingerprint and touch the sensor. Try a wrong finger too and see it refuse."
}

private fun affects(kind: SensorKind): String = when (kind) {
    SensorKind.ACCELEROMETER ->
        "Bumpiness is the biggest part of the Comfort Score: up to 45 points come off for vibration and up to 10 more for the number of big bumps.\n\n" +
            "A trip also needs Low bumpiness (under 1.2) to count as a Fast Run."
    SensorKind.GYROSCOPE ->
        "Each sharp turn counts once every 1.5 seconds, and the turn rate takes up to 10 points off the Comfort Score.\n\n" +
            "It is what tells a swerving ride from a straight one."
    SensorKind.MAGNETOMETER ->
        "It gives the travel heading (N, NE, E ...) shown while a trip is recording.\n\n" +
            "It does not change the Comfort Score."
    SensorKind.AMBIENT_LIGHT ->
        "With auto-brightness on, the screen dims to 20% when it is dark (night or a tunnel) and goes to full brightness in direct sun.\n\n" +
            "It also helps the pocket check: covered and under ${ScreenCareState.POCKET_LUX.toInt()} lux counts as in a pocket."
    SensorKind.PROXIMITY ->
        "Together with the light sensor, if the phone is covered and dark it counts as in a pocket, and the screen goes black to save battery.\n\n" +
            "Recording keeps running underneath. Pocket mode is switched off on this test screen."
    SensorKind.GPS ->
        "Draws your route on the map and measures the distance and speed of every trip.\n\n" +
            "Average speed of 40 km/h or more is half of what makes a Fast Run."
    SensorKind.BAROMETER ->
        "Falling pressure gives the rain warning on the live trip screen and in the trip summary: dropping 1 hPa per hour means rain is possible, 2 hPa per hour means likely.\n\n" +
            "It needs about 10 minutes of readings during a trip. It does not change the Comfort Score."
    SensorKind.MICROPHONE ->
        "The average loudness becomes Low (under 65 dB), Medium (65 to 78) or High (78 and up), and takes up to 25 points off the Comfort Score.\n\n" +
            "Sound is analysed in memory and thrown away. Nothing is recorded or saved."
    SensorKind.STEP_COUNTER ->
        "Steps walked in the minutes before Start Trip and after End Trip are saved with the trip. They are the walking steps you see on Home and History.\n\n" +
            "It needs the Physical activity permission."
    SensorKind.CAMERA ->
        "Used to photograph a hazard (pothole, flood and so on). The report is tagged with GPS and uploaded from the Hazards screen.\n\n" +
            "The camera is only on while you are taking a photo."
    SensorKind.FINGERPRINT ->
        "Locks the app. Trip history opens only after a fingerprint, with the phone PIN as a backup, and it locks again after 60 seconds away."
}

// ------------------------------------------------------------------ shared bits

@Composable
internal fun TestCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PosterHeading(title, fontSize = 18.sp)
        content()
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = PosterGrey)
}

/** PASSED (ink block) once the check has happened, WAITING (outline) until then. */
@Composable
internal fun CheckRow(label: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Ink)
        Text(
            if (passed) "PASSED" else "WAITING",
            modifier = Modifier
                .padding(start = 8.dp)
                .background(if (passed) Ink else Color.Transparent)
                .border(2.dp, if (passed) Ink else PosterGrey, RectangleShape)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            color = if (passed) PosterWhite else PosterGrey,
            style = PosterDisplay.copy(fontSize = 14.sp)
        )
    }
}

/** Scrolling line graph of the last [GRAPH_POINTS] values with an optional dashed red limit line. */
@Composable
internal fun LineGraph(
    values: List<Float>,
    maxValue: Float,
    thresholds: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
    minValue: Float = 0f
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(Paper)
            .border(2.dp, Ink, RectangleShape)
    ) {
        val w = size.width
        val h = size.height
        val span = (maxValue - minValue).coerceAtLeast(0.0001f)
        thresholds.forEach { limit ->
            val ty = h - ((limit - minValue) / span).coerceIn(0f, 1f) * h
            drawLine(
                PosterRed, Offset(0f, ty), Offset(w, ty),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        }
        if (values.size > 1) {
            val path = Path()
            values.forEachIndexed { i, v ->
                val px = w * i / (GRAPH_POINTS - 1f)
                val py = h - ((v - minValue) / span).coerceIn(0f, 1f) * h
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Ink, style = Stroke(3.dp.toPx()))
        }
    }
}

internal fun fmt(v: Float): String = String.format(Locale.US, "%.1f", v)
