package com.biyahe.companion.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

/** Runtime permissions Biyahe needs, grouped the way users understand them. */
enum class AppPermission(
    val title: String,
    val reason: String,
    val icon: ImageVector,
    val required: Boolean
) {
    LOCATION("Location", "Records your route, speed and distance (GPS).", Icons.Filled.LocationOn, true),
    MICROPHONE("Microphone", "Measures how noisy your ride is. Sound is never recorded or saved.", Icons.Filled.Mic, true),
    STEPS("Physical activity", "Counts your walking steps to and from stops.", Icons.AutoMirrored.Filled.DirectionsWalk, true),
    CAMERA("Camera", "Photograph potholes, flooding and other hazards.", Icons.Filled.CameraAlt, false),
    NOTIFICATIONS("Notifications", "Shows the ongoing trip notification and rain warnings.", Icons.Filled.Notifications, false);

    /** Manifest permissions to ask for in one system dialog. Empty = nothing needed on this Android version. */
    val requestList: List<String>
        get() = when (this) {
            LOCATION -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            STEPS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACTIVITY_RECOGNITION)
                else emptyList()
            CAMERA -> listOf(Manifest.permission.CAMERA)
            NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS)
                else emptyList()
        }

    /** What must be granted for the feature to work (precise location is enough; coarse alone is not). */
    private val checkList: List<String>
        get() = if (this == LOCATION) listOf(Manifest.permission.ACCESS_FINE_LOCATION) else requestList

    fun isGranted(context: Context): Boolean =
        checkList.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    companion object {
        fun anyRequiredMissing(context: Context): Boolean =
            entries.any { it.required && !it.isGranted(context) }
    }
}
