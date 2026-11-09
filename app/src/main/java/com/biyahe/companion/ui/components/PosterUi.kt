package com.biyahe.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.R
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterTextShadow
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.hardShadow
import com.biyahe.companion.ui.theme.skewX

/**
 * Square ink button with a hard red shadow. [danger] flips it to red with an ink shadow (End Trip).
 * [compact] is the smaller version for use inside a row or card.
 */
@Composable
fun PosterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    compact: Boolean = false
) {
    val fill = (if (danger) PosterRed else Ink).copy(alpha = if (enabled) 1f else 0.45f)
    val shadow = if (danger) Ink else PosterRed
    val offset = if (compact) 3.dp else 5.dp
    Box(
        modifier
            .padding(end = offset, bottom = offset)
            .hardShadow(shadow, offset)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = if (compact) 38.dp else 50.dp)
            .padding(horizontal = if (compact) 12.dp else 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            color = PosterWhite,
            style = PosterDisplay.copy(fontSize = if (compact) 16.sp else 20.sp, letterSpacing = 1.5.sp)
        )
    }
}

/** Section heading: bold italic caps, slanted, with the thick red underline. */
@Composable
fun PosterHeading(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 26.sp) {
    Text(
        text.uppercase(),
        modifier = modifier
            .skewX()
            .drawBehind {
                val h = 5.dp.toPx()
                drawRect(PosterRed, Offset(0f, size.height - h), Size(size.width, h))
            }
            .padding(bottom = 4.dp),
        style = PosterDisplay.copy(fontSize = fontSize),
        color = Ink
    )
}

/** BIYAHE in big slanted ink with a red offset shadow, COMPANION in an ink block with a red shadow. */
@Composable
fun PosterLogo(modifier: Modifier = Modifier, big: TextUnit = 56.sp, small: TextUnit = 26.sp) {
    Column(modifier.skewX()) {
        Text(
            "BIYAHE",
            style = PosterDisplay.copy(fontSize = big, letterSpacing = 2.sp, shadow = PosterTextShadow),
            color = Ink
        )
        Text(
            "COMPANION",
            modifier = Modifier
                .padding(start = 4.dp, end = 5.dp, bottom = 5.dp)
                .hardShadow(PosterRed, 5.dp)
                .background(Ink)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            style = PosterDisplay.copy(fontSize = small, letterSpacing = 4.sp),
            color = PosterWhite
        )
    }
}

/** "KNOW HOW YOUR ROAD REALLY FEELS." with the red bar on its left edge. */
@Composable
fun PosterTagline(modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp) {
    Text(
        "KNOW HOW YOUR ROAD REALLY FEELS.",
        modifier = modifier
            .drawBehind { drawRect(PosterRed, size = Size(6.dp.toPx(), size.height)) }
            .padding(start = 12.dp),
        style = PosterDisplay.copy(fontSize = fontSize, letterSpacing = 0.5.sp),
        color = Ink
    )
}

/** Poster header band: halftone night road behind your content, thick ink line under it. */
@Composable
fun PosterHero(
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxWidth().height(height)) {
        HalftoneImage(R.drawable.road_hero, Modifier.fillMaxSize())
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Ink))
    }
}
