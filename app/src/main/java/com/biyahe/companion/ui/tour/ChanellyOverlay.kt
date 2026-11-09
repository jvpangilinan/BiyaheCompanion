package com.biyahe.companion.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.R
import com.biyahe.companion.navigation.Routes
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard

/**
 * Chanelly's tour layer, drawn above the whole app.
 *
 * The screen is dimmed except for a hole around the current target. Taps outside the hole are
 * blocked; taps inside it reach the real button underneath, so the audience uses the actual app.
 * Every line is also read aloud (mute with the speaker button), with large, simple text.
 */
@Composable
fun ChanellyOverlay(tour: GuideTour, screen: String?) {
    val step = tour.step ?: return
    if (screen == null || screen == Routes.LOCK) return
    val container = (LocalContext.current.applicationContext as BiyaheApp).container

    val matches = step.screen == screen
    // Read the line aloud when it appears on its screen.
    LaunchedEffect(tour.stepIndex, matches, tour.voiceOn) {
        if (matches && tour.voiceOn) container.safetyAlerts.speak(step.text, com.biyahe.companion.safety.Voice.TOUR)
    }

    // Wandered off the path: don't block anything, just offer to end.
    if (!matches) {
        if (!step.optional) PausedChip(onEnd = tour::end)
        return
    }

    // Just a bubble at the top; the screen below stays usable (e.g. permission prompts).
    if (!step.blocking) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopCenter) {
            ChanellyBubble(tour, step)
        }
        return
    }

    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val hole: Rect? = step.target
        ?.let { tour.targets[it] }
        ?.takeIf { it.width > 0f && it.height > 0f }
        ?.let { Rect(it.left - padPx, it.top - padPx, it.right + padPx, it.bottom + padPx) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        // Dim layer with the spotlight cut out. A Canvas has no pointer input, so it never eats taps.
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            if (hole != null) {
                val r = CornerRadius(10.dp.toPx())
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, r, blendMode = BlendMode.Clear)
                drawRoundRect(PosterRed, hole.topLeft, hole.size, r, style = Stroke(5.dp.toPx()))
            }
        }

        // Tap blockers around the hole (or over everything when there is no hole).
        if (hole == null) {
            Blocker(Rect(0f, 0f, w, h))
        } else {
            Blocker(Rect(0f, 0f, w, hole.top))
            Blocker(Rect(0f, hole.bottom, w, h))
            Blocker(Rect(0f, hole.top, hole.left, hole.bottom))
            Blocker(Rect(hole.right, hole.top, w, hole.bottom))
        }

        // Chanelly goes on the side with more room, away from the spotlight.
        val putOnTop = hole != null && (hole.top + hole.bottom) / 2f > h / 2f
        val align = when {
            hole == null -> Alignment.Center
            putOnTop -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = if (hole == null) 0.dp else 24.dp),
            contentAlignment = align
        ) {
            ChanellyBubble(tour, step)
        }
    }
}

@Composable
private fun ChanellyBubble(tour: GuideTour, step: TourStep) {
    Row(verticalAlignment = Alignment.Bottom) {
        Image(
            painter = painterResource(R.drawable.chanelly),
            contentDescription = "Chanelly, your guide",
            modifier = Modifier.size(96.dp)
        )
        Column(
            Modifier
                .padding(end = 4.dp, bottom = 4.dp)
                .posterCard(shadow = PosterRed, shadowOffset = 4.dp, borderWidth = 2.dp)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CHANELLY", color = PosterRed, style = PosterDisplay.copy(fontSize = 17.sp, letterSpacing = 1.5.sp))
                Spacer(Modifier.width(8.dp))
                Text("${tour.stepIndex + 1} / ${tour.stepCount}", color = PosterGrey, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                // Speaker: read aloud on / off.
                Box(
                    Modifier.size(40.dp).clickable { tour.voiceOn = !tour.voiceOn },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (tour.voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (tour.voiceOn) "Mute Chanelly" else "Let Chanelly talk",
                        tint = Ink
                    )
                }
            }
            // Large, plain text: readable for children and older people.
            Text(step.text, color = Ink, fontSize = 19.sp, lineHeight = 26.sp, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!tour.isLast) {
                    TextButton(onClick = tour::end) { Text("SKIP", color = PosterGrey, style = MaterialTheme.typography.labelLarge) }
                }
                if (!step.waitsForUser && tour.canGoBack) {
                    TextButton(onClick = tour::back) { Text("BACK", color = Ink, style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.weight(1f))
                if (step.waitsForUser) {
                    Text(
                        if (step.blocking && step.target != null) "TAP THE RED BOX" else "FOLLOW THE SCREEN",
                        color = PosterRed, style = PosterDisplay.copy(fontSize = 16.sp),
                        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                    )
                } else {
                    PosterButton(if (tour.isLast) "Done" else "Next", tour::next)
                }
            }
        }
    }
}

/** Small card shown when the user leaves the tour's screen; nothing is blocked. */
@Composable
private fun PausedChip(onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .background(PosterWhite)
                .border(2.dp, Ink, RectangleShape)
                .padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(painterResource(R.drawable.chanelly), contentDescription = null, modifier = Modifier.size(48.dp))
            Text("Tour paused. Go back to continue.", style = MaterialTheme.typography.bodyMedium, color = Ink,
                modifier = Modifier.width(180.dp))
            TextButton(onClick = onEnd) { Text("END", color = PosterRed, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** An invisible rectangle that swallows taps. */
@Composable
private fun Blocker(rect: Rect) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left.toInt(), rect.top.toInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    )
}
