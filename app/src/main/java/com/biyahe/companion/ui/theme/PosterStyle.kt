package com.biyahe.companion.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.tan

/**
 * Slants whatever it wraps to the right by [degrees] (poster headings use about 8), keeping the
 * bottom-left corner fixed. Drawing only: layout and touch areas are not changed.
 */
fun Modifier.skewX(degrees: Float = 8f): Modifier = drawWithContent {
    // Lean to the right: x' = x + k * (y - bottom), so the bottom-left corner stays where it is.
    val k = -tan(Math.toRadians(degrees.toDouble())).toFloat()
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(0f, size.height)
    canvas.skew(k, 0f)
    canvas.translate(0f, -size.height)
    drawContent()
    canvas.restore()
}

/** Hard, un-blurred offset shadow behind the element. Put it BEFORE .background() in the chain. */
fun Modifier.hardShadow(color: Color = Ink, offset: Dp = 6.dp): Modifier = drawBehind {
    val o = offset.toPx()
    drawRect(color = color, topLeft = Offset(o, o), size = size)
}

/** Thick square ink border. */
fun Modifier.inkBorder(width: Dp = 3.dp, color: Color = Ink): Modifier =
    border(width, color, RectangleShape)

/** The poster card: hard shadow, flat fill, thick square border. Add .padding() after it. */
fun Modifier.posterCard(
    fill: Color = PosterWhite,
    shadow: Color = Ink,
    shadowOffset: Dp = 8.dp,
    borderColor: Color = Ink,
    borderWidth: Dp = 3.dp
): Modifier = this
    .hardShadow(shadow, shadowOffset)
    .background(fill)
    .border(borderWidth, borderColor, RectangleShape)
