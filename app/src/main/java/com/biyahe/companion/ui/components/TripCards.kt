package com.biyahe.companion.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.R
import com.biyahe.companion.trip.StoredTrip
import com.biyahe.companion.scoring.ComfortLabel
import com.biyahe.companion.ui.theme.ComfortFair
import com.biyahe.companion.ui.theme.ComfortRough
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.util.formatDuration
import com.biyahe.companion.ui.util.formatKm
import com.biyahe.companion.ui.util.formatShortDate

// Old names kept so the screens that still import them compile (yellow is gone from the poster).
// BiyaheGold now means "the one red accent"; BiyaheAmber (the Fair colour) is now grey.
val BiyaheGold = PosterRed
val BiyaheAmber = PosterGrey

/** Smooth = green, Fair = amber, Rough = red (the comfort colours, see Color.kt). */
fun labelColor(label: String): Color = when (label) {
    "Smooth" -> ComfortSmooth
    "Fair" -> ComfortFair
    else -> ComfortRough
}

/** Colour for a score number, by the same 80 / 60 cut-offs as the labels. */
fun scoreColor(score: Int): Color = labelColor(ComfortLabel.from(score).title)

/** Small "FAST RUN" chip: ink with a red edge, like the last sensor chip on the poster. */
@Composable
fun FastRunTag(modifier: Modifier = Modifier) {
    Text(
        "FAST RUN",
        modifier = modifier
            .background(Ink)
            .drawBehind { drawRect(PosterRed, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 10.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
        style = PosterDisplay.copy(fontSize = 13.sp, letterSpacing = 1.sp),
        color = PosterWhite
    )
}

/** The "86" in a square, used in lists. */
@Composable
fun ScoreBadge(score: Int, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .background(labelColor(label))
            .border(2.dp, Ink, RectangleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(score.toString(), color = PosterWhite, style = PosterDisplay.copy(fontSize = 26.sp))
    }
}

/**
 * The poster score ring: red arc on a light grey track, score in the centre.
 * [onDark] = true for the old dark summary card (grey-on-white would vanish there); the
 * redesigned summary in the next section is light and uses the default.
 */
@Composable
fun ScoreRing(score: Int, size: Dp = 180.dp, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val track = if (onDark) Color(0x33FFFFFF) else Color(0xFFD9D9D6)
    val text = if (onDark) PosterWhite else Ink
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.width * 0.08f   // DrawScope.size (pixels), not the Dp parameter
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                scoreColor(score), -90f, 360f * (score.coerceIn(0, 100) / 100f), false,
                Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), color = text, style = PosterDisplay.copy(fontSize = (size.value * 0.34f).sp))
            Text("COMFORT SCORE", color = if (onDark) Color(0xFFDDDDDD) else PosterGrey, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Label on the left, bold value on the right, dashed line above: the poster summary rows. */
@Composable
fun PosterRow(label: String, value: String, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val line = if (onDark) Color(0x66FFFFFF) else Color(0xFF888888)
    val ink = if (onDark) PosterWhite else Ink
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    line, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                )
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ink, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = ink, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TripRowCard(stored: StoredTrip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = stored.trip
    // End/bottom padding leaves room for the hard shadow so neighbours do not cover it.
    Row(
        modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScoreBadge(t.comfortScore, t.comfortLabel)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatShortDate(t.startedAtMs), style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                "${formatKm(t.distanceKm)} · ${formatDuration(t.durationMs)} · ${t.stepsBefore + t.stepsAfter} steps",
                style = MaterialTheme.typography.bodyMedium, color = PosterGrey
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t.comfortLabel.uppercase(), color = labelColor(t.comfortLabel), style = PosterDisplay.copy(fontSize = 18.sp))
            if (t.isFastRun) FastRunTag()
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(value, color = Ink, style = PosterDisplay.copy(fontSize = 32.sp))
        Text(title.uppercase(), color = PosterGrey, style = MaterialTheme.typography.labelMedium)
    }
}

/** The three poster result cards (v2 Table 3). Fair has no card of its own. */
enum class RideKind(
    val title: String,
    val subtitle: String,
    val description: String,
    @DrawableRes val image: Int,
    val inverted: Boolean
) {
    SMOOTH("SMOOTH", "Score 80 to 100", "Sleep-easy road. Low bumps, low noise.", R.drawable.still_headband, false),
    ROUGH("ROUGH", "Score under 60", "Bumpy road. Photograph it, report it, avoid it.", R.drawable.still_roadside, true),
    FAST_RUN("FAST RUN", "High speed, low bumps", "Open and quick. The roads worth taking when you want to go.", R.drawable.still_cabin, false)
}

/** Which card a finished trip earns: Fast Run wins, then Smooth or Rough; Fair gets none. */
fun rideKindFor(label: String, isFastRun: Boolean): RideKind? = when {
    isFastRun -> RideKind.FAST_RUN
    label == "Smooth" -> RideKind.SMOOTH
    label == "Rough" -> RideKind.ROUGH
    else -> null
}

@Composable
fun RideKindCard(kind: RideKind, modifier: Modifier = Modifier) {
    val fill = if (kind.inverted) Ink else PosterWhite
    val titleColor = if (kind.inverted) PosterRed else Ink
    val bodyColor = if (kind.inverted) Color(0xFFDDDDDD) else Color(0xFF333333)
    val subColor = if (kind.inverted) PosterWhite else Ink
    Column(
        modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .posterCard(fill = fill, shadowOffset = 8.dp, borderWidth = 3.dp)
            .fillMaxWidth()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(kind.image),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .border(2.dp, if (kind.inverted) PosterWhite else Ink, RectangleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(6.dp))
        Text(kind.title, color = titleColor, style = PosterDisplay.copy(fontSize = 26.sp), textAlign = TextAlign.Center)
        Text(kind.subtitle, color = subColor, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        Text(kind.description, color = bodyColor, fontSize = 17.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}
