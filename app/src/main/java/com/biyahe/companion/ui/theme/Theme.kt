package com.biyahe.companion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Black / red / white manga-print look. Light scheme only (the poster has no dark mode).
private val BiyaheColors = lightColorScheme(
    primary = Ink,
    onPrimary = PosterWhite,
    primaryContainer = Color(0xFFE9E9E6),
    onPrimaryContainer = Ink,
    secondary = PosterRed,
    onSecondary = PosterWhite,
    secondaryContainer = Color(0xFFEAEAE7),
    onSecondaryContainer = Ink,
    tertiary = PosterGrey,
    onTertiary = PosterWhite,
    background = Paper,
    onBackground = Ink,
    surface = PosterWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEDEA),
    onSurfaceVariant = PosterGrey,
    surfaceTint = Color.Transparent,          // no purple/blue tint on raised surfaces
    outline = Ink,
    outlineVariant = Color(0xFFBDBDBA),
    error = PosterRed,
    onError = PosterWhite,
    // Material 3 cards and sheets read these; without them they fall back to a lilac grey.
    surfaceContainerLowest = PosterWhite,
    surfaceContainerLow = Color(0xFFF6F6F3),
    surfaceContainer = Color(0xFFF0F0ED),
    surfaceContainerHigh = Color(0xFFEAEAE7),
    surfaceContainerHighest = Color(0xFFE4E4E1)
)

// Square corners everywhere, as on the poster.
private val BiyaheShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun BiyaheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BiyaheColors,
        typography = BiyaheTypography,
        shapes = BiyaheShapes,
        content = content
    )
}
