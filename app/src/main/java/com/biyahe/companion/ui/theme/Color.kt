package com.biyahe.companion.ui.theme

import androidx.compose.ui.graphics.Color

// ---- v2 poster palette (BIYAHE_COMPANION_v2, section 3.11) -------------------------------------
// Red is for emphasis only. Everything else is ink, paper, white or grey.
val Ink = Color(0xFF0A0A0A)
val Paper = Color(0xFFFBFBF9)
val PosterRed = Color(0xFFD7141A)
val PosterGrey = Color(0xFF555555)
val PosterWhite = Color(0xFFFFFFFF)

// ---- Legacy names -------------------------------------------------------------------------------
// The old navy/yellow names are kept so every screen still compiles. They now point at the new
// palette. Each screen will switch to Ink / PosterRed directly as it is redone, and then these
// aliases can be deleted.
val BiyaheNavy = Ink
val BiyaheNavyLight = Color(0xFF2B2B2B)
val BiyaheYellow = PosterRed
val BiyaheBlue = PosterGrey
val BiyaheRed = PosterRed
val BiyaheGreen = Ink          // "ok" = ink, "problem" = red; still two clearly different states
val BiyaheBackground = Paper
val BiyaheSurface = PosterWhite
val BiyaheText = Ink

// ---- Comfort colours -----------------------------------------------------------------------------
// Used ONLY for the Comfort Score and the roughness map, so a colour always means the same thing:
// green = Smooth (80-100), amber = Fair (60-79), red = Rough (0-59). Red elsewhere is just emphasis.
val ComfortSmooth = Color(0xFF1B7F3A)
val ComfortFair = Color(0xFFB86E00)
val ComfortRough = PosterRed
val ComfortUnknown = Color(0xFF9A9A9A)
