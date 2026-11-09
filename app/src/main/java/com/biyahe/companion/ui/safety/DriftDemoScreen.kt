package com.biyahe.companion.ui.safety

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.safety.DriftSnapshot
import com.biyahe.companion.safety.DriftStatus
import com.biyahe.companion.safety.DriftTracker
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortRough
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Drift demo: a straight road, and you walk it. Gyroscope + compass give your direction, the
 * accelerometer counts your steps, and leaving the lane triggers the spoken drift warning.
 */
@Composable
fun DriftDemoScreen(onBack: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val hub = container.sensorHub
    var tracker by remember { mutableStateOf(DriftTracker()) }
    var running by remember { mutableStateOf(false) }
    var snap by remember { mutableStateOf(DriftSnapshot()) }

    // Sensors run only while this screen is open and a walk is in progress.
    LaunchedEffect(running, tracker) {
        if (!running) return@LaunchedEffect
        val t = tracker
        var lastUi = 0L
        var lastStatus = t.status()
        fun publish(now: Long, force: Boolean = false) {
            if (force || now - lastUi >= 50L) {
                lastUi = now
                snap = t.snapshot()
            }
        }
        coroutineScope {
            launch { hub.heading().collect { t.onCompass(it); publish(System.currentTimeMillis()) } }
            launch { hub.gyroscope().collect { t.onGyro(it); publish(it.timestampMs) } }
            launch {
                hub.accelerometer().collect { v ->
                    if (t.onAccel(v)) {
                        if (t.justDrifted(lastStatus)) {
                            container.safetyAlerts.driftWarning(left = t.status() == DriftStatus.DRIFT_LEFT)
                        }
                        lastStatus = t.status()
                        publish(v.timestampMs, force = true)
                        if (t.snapshot().finished) {
                            container.safetyAlerts.speak(
                                if (t.snapshot().driftWarnings == 0) "Finished. You stayed in your lane." else "Finished."
                            )
                            running = false
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            Column {
                PosterHeading("Drift check", fontSize = 24.sp)
                Text("Stage demo: walk a straight line", style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
            }
        }

        StatusBanner(snap, running)

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            RoadView(snap, tracker, Modifier.fillMaxSize())
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Readout("Off centre", String.format(Locale.US, "%+.0f cm", snap.x * 100), Modifier.weight(1f))
                Readout("Facing", String.format(Locale.US, "%+.0f°", snap.deviationDeg), Modifier.weight(1f))
                Readout("Walked", String.format(Locale.US, "%.1f m", snap.y), Modifier.weight(1f))
            }
            PosterRow("Gyroscope turn rate", String.format(Locale.US, "%+.0f °/s", snap.turnRateDps))
            PosterRow("Compass", snap.compassDeg?.let { String.format(Locale.US, "%.0f°", it) } ?: "-")
            PosterRow("Steps (accelerometer)", snap.steps.toString())

            if (snap.finished) {
                PosterRow("Most off centre", String.format(Locale.US, "%.0f cm", snap.maxDriftM * 100))
                PosterRow("Drift warnings", snap.driftWarnings.toString())
            }

            PosterButton(
                text = when {
                    running -> "Stop"
                    snap.steps > 0 -> "Try again"
                    else -> "Face ahead and start"
                },
                onClick = {
                    if (running) {
                        running = false
                    } else {
                        // A fresh tracker each walk; "straight ahead" is the way the phone faces now.
                        val fresh = DriftTracker()
                        tracker = fresh
                        snap = fresh.snapshot()
                        running = true
                        container.safetyAlerts.speak("Walk straight ahead.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                danger = running
            )
        }
    }

    // "Straight ahead" is fixed after the sensors have settled for a moment.
    LaunchedEffect(running, tracker) {
        if (running) {
            kotlinx.coroutines.delay(600)
            tracker.start()
            snap = tracker.snapshot()
        }
    }
}

@Composable
private fun StatusBanner(s: DriftSnapshot, running: Boolean) {
    val (text, color) = when {
        s.finished -> (if (s.driftWarnings == 0) "FINISHED · STAYED IN LANE" else "FINISHED · DRIFTED ${s.driftWarnings}×") to
            (if (s.driftWarnings == 0) ComfortSmooth else ComfortRough)
        !running -> "HOLD THE PHONE IN FRONT OF YOU" to Ink
        s.status == DriftStatus.DRIFT_LEFT -> "DRIFTING LEFT! STEER BACK" to ComfortRough
        s.status == DriftStatus.DRIFT_RIGHT -> "DRIFTING RIGHT! STEER BACK" to ComfortRough
        s.status == DriftStatus.CAUTION -> "CAREFUL · KEEP STRAIGHT" to ComfortFair
        else -> "IN YOUR LANE" to ComfortSmooth
    }
    Box(Modifier.fillMaxWidth().background(color).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = PosterWhite, style = PosterDisplay.copy(fontSize = 22.sp, letterSpacing = 1.sp))
    }
}

/**
 * The "map": a straight road seen from above. Sideways distances are drawn bigger than forward
 * ones so a 20 cm wobble is easy to see from the audience.
 */
@Composable
private fun RoadView(s: DriftSnapshot, tracker: DriftTracker, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val halfSpanM = 1.5                                   // metres shown each side of the centre
        val pxPerMx = (w / 2f) / halfSpanM.toFloat()
        val pxPerMy = h / (tracker.goalM.toFloat() + 0.6f)
        fun sx(xm: Double) = w / 2f + (xm * pxPerMx).toFloat()
        fun sy(ym: Double) = h - 12f - (ym * pxPerMy).toFloat()

        // Off-road danger zones, the road, its edges and the dashed centre line.
        drawRect(Color(0xFFF3D9D9), Offset.Zero, Size(w, h))
        val laneL = sx(-tracker.laneHalfM); val laneR = sx(tracker.laneHalfM)
        drawRect(Color(0xFF3A3A3A), Offset(laneL, 0f), Size(laneR - laneL, h))
        val cautionL = sx(-tracker.cautionM); val cautionR = sx(tracker.cautionM)
        drawRect(Color(0xFF474747), Offset(cautionL, 0f), Size(cautionR - cautionL, h))
        drawLine(PosterWhite, Offset(laneL, 0f), Offset(laneL, h), strokeWidth = 6f)
        drawLine(PosterWhite, Offset(laneR, 0f), Offset(laneR, h), strokeWidth = 6f)
        drawLine(Color(0xFFFFD54F), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 18f)))
        // Finish line
        val fy = sy(tracker.goalM)
        for (i in 0 until 8) {
            val cw = (laneR - laneL) / 8f
            drawRect(if (i % 2 == 0) PosterWhite else Ink, Offset(laneL + i * cw, fy - 10f), Size(cw, 10f))
        }

        // Your path so far, each stretch coloured by how far off centre it was.
        val trail = s.trail
        for (i in 1 until trail.size) {
            val (x0, y0) = trail[i - 1]; val (x1, y1) = trail[i]
            val c = when {
                abs(x1) >= tracker.laneHalfM -> ComfortRough
                abs(x1) >= tracker.cautionM -> ComfortFair
                else -> ComfortSmooth
            }
            drawLine(c, Offset(sx(x0), sy(y0)), Offset(sx(x1), sy(y1)), strokeWidth = 10f, cap = StrokeCap.Round)
        }

        // You: a dot with an arrow showing which way you face.
        val cx = sx(s.x); val cy = sy(s.y)
        rotate(s.deviationDeg.toFloat(), pivot = Offset(cx, cy)) {
            val arrow = Path().apply {
                moveTo(cx, cy - 46f); lineTo(cx - 16f, cy - 18f); lineTo(cx + 16f, cy - 18f); close()
            }
            drawPath(arrow, PosterWhite)
        }
        drawCircle(Ink, 20f, Offset(cx, cy))
        drawCircle(
            when (s.status) {
                DriftStatus.OK -> ComfortSmooth
                DriftStatus.CAUTION -> ComfortFair
                else -> ComfortRough
            }, 15f, Offset(cx, cy)
        )
        drawCircle(PosterWhite, 20f, Offset(cx, cy), style = Stroke(3f))
    }
}

@Composable
private fun Readout(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.padding(end = 3.dp, bottom = 3.dp).posterCard(shadowOffset = 3.dp, borderWidth = 2.dp).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = PosterDisplay.copy(fontSize = 22.sp), color = Ink)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = PosterGrey)
    }
}
