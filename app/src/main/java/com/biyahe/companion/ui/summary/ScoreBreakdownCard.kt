package com.biyahe.companion.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.scoring.ComfortScoreCalculator
import com.biyahe.companion.trip.CompletedTrip
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.scoreColor
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortRough
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard

/**
 * "Why this score": 100 minus the four penalties, each with the sensor it came from and a bar
 * showing how much of its maximum was used. Rebuilt from the saved trip values.
 */
@Composable
fun ScoreBreakdownCard(trip: CompletedTrip, modifier: Modifier = Modifier) {
    val b = ComfortScoreCalculator.breakdown(
        bumpinessIndex = trip.bumpinessIndex,
        bumpCount = trip.bumpCount,
        sharpTurnCount = trip.sharpTurnCount,
        averageDb = trip.avgNoiseDb.takeIf { it > 0.0 },
        durationMs = trip.durationMs
    )
    val pts = b.wholePoints()

    Column(
        modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PosterHeading("Why this score", fontSize = 20.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Every trip starts at", style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f))
            Text("100", style = PosterDisplay.copy(fontSize = 22.sp), color = Ink)
        }

        PenaltyRow("Vibration", "Accelerometer", pts[0], ComfortScoreCalculator.VIBRATION_WEIGHT.toInt())
        PenaltyRow("Big bumps", "Accelerometer", pts[1], ComfortScoreCalculator.BUMP_WEIGHT.toInt())
        PenaltyRow("Sharp turns", "Gyroscope", pts[2], ComfortScoreCalculator.TURN_WEIGHT.toInt())
        if (b.noiseMeasured) {
            PenaltyRow("Noise", "Microphone", pts[3], ComfortScoreCalculator.NOISE_WEIGHT.toInt())
        } else {
            Text("Noise: not measured (microphone off), left out of the score.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
        }

        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("COMFORT SCORE", style = PosterDisplay.copy(fontSize = 18.sp), color = Ink, modifier = Modifier.weight(1f))
            Text(b.score.toString(), style = PosterDisplay.copy(fontSize = 30.sp), color = scoreColor(b.score))
        }
        if (b.score != trip.comfortScore) {
            Text(
                "Saved score: ${trip.comfortScore}. This trip was scored with earlier settings.",
                style = MaterialTheme.typography.bodySmall, color = PosterGrey
            )
        }
    }
}

@Composable
private fun PenaltyRow(label: String, sensor: String, points: Int, max: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("  ${sensor.uppercase()}", style = MaterialTheme.typography.labelSmall, color = PosterGrey, modifier = Modifier.padding(bottom = 2.dp))
            Spacer(Modifier.weight(1f))
            Text(if (points == 0) "0" else "−$points", style = PosterDisplay.copy(fontSize = 20.sp), color = if (points == 0) PosterGrey else ComfortRough)
            Text(" / $max", style = MaterialTheme.typography.bodySmall, color = PosterGrey, modifier = Modifier.padding(bottom = 2.dp))
        }
        // Bar: how much of this penalty's maximum was used.
        val frac = if (max == 0) 0f else (points.toFloat() / max).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFE6E6E2)).border(1.dp, Ink, RectangleShape)) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(ComfortRough))
        }
    }
}

/** Key under the roughness map. */
@Composable
fun RoughnessLegend(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(ComfortSmooth, "Smooth")
        LegendSwatch(ComfortFair, "Fair")
        LegendSwatch(ComfortRough, "Rough")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(ComfortRough, CircleShape).border(1.5.dp, PosterWhite, CircleShape))
            Text(" Big bump", style = MaterialTheme.typography.bodySmall, color = Ink)
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp).height(6.dp).background(color))
        Text(" $label", style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}
