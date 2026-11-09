package com.biyahe.companion.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val WELCOME = "welcome"          // first screen: use the app, or demo mode
    const val LOGIN = "login"              // log in / create account
    const val ADMIN = "admin"              // the separate administrator dashboard
    const val SETTINGS = "settings"        // rider app settings
    const val TUNING = "tuning"            // sensitivity & sound
    const val LOCK = "lock"                // fingerprint gate (Section 3)
    const val PERMISSIONS = "permissions"  // runtime permission request (Section 3)
    const val SENSORS = "sensors"          // sensor check / diagnostics (Section 4)
    const val RECORDER = "recorder"        // trip recorder test screen (Section 5)
    const val HOME = "home"                // dashboard (Section 7)
    const val TRIP = "trip"                // live trip + route map (Section 7)
    const val SUMMARY = "summary/{tripId}" // comfort score + route (Section 7)
    const val HISTORY = "history"          // past trips + steps (Section 7)
    const val HAZARDS = "hazards"          // photo reports (Section 8)
    const val SAFETY = "safety"            // speed limit, sleep check, crash detection
    const val DRIFT_DEMO = "drift_demo"    // walk-a-straight-line drift demo (gyroscope + compass + steps)

    const val ARG_TRIP_ID = "tripId"
    fun summary(tripId: Long) = "summary/$tripId"
}

/** Screens shown in the bottom bar. */
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home),
    SAFETY(Routes.SAFETY, "Safety", Icons.Filled.Shield),
    TRIP(Routes.TRIP, "Trip", Icons.Filled.DirectionsBus),
    HISTORY(Routes.HISTORY, "History", Icons.Filled.History),
    HAZARDS(Routes.HAZARDS, "Hazards", Icons.Filled.Warning)
}
