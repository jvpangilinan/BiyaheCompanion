package com.biyahe.companion.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.biyahe.companion.ui.theme.Paper

/**
 * The poster hero look, built once and reused: the picture in grayscale, a paper-to-clear
 * gradient (so text on top stays readable), and a fine halftone dot grid over everything.
 *
 *  - [fadeFromStart] = true : paper on the left fading to clear on the right (text sits on the left)
 *  - [fadeFromStart] = false: only the soft paper fade at the bottom edge
 */
@Composable
fun HalftoneImage(
    @DrawableRes image: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fadeFromStart: Boolean = true
) {
    val gray = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    Box(modifier.clipToBounds()) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alignment = alignment,
            colorFilter = gray
        )
        if (fadeFromStart) {
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to Paper.copy(alpha = 0.97f),
                        0.5f to Paper.copy(alpha = 0.82f),
                        1f to Paper.copy(alpha = 0.05f)
                    )
                )
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.8f to Color.Transparent,
                    1f to Paper.copy(alpha = 0.9f)
                )
            )
        )
        // Halftone dots: one cached point list, drawn in a single call.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val step = 3.dp.toPx()
                val dots = ArrayList<Offset>()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        dots.add(Offset(x, y)); x += step
                    }
                    y += step
                }
                val dotSize = 1.1.dp.toPx()
                onDrawBehind {
                    drawPoints(dots, PointMode.Points, Color.Black.copy(alpha = 0.28f), dotSize, StrokeCap.Round)
                }
            }
        )
    }
}
