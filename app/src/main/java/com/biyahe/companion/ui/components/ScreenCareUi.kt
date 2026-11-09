package com.biyahe.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.biyahe.companion.sensors.ScreenCare
import com.biyahe.companion.sensors.ScreenCareState
import com.biyahe.companion.util.findActivity
import android.view.WindowManager

/**
 * Applies the light/proximity-driven brightness to the current window and
 * restores the system brightness when the screen leaves composition.
 */
@Composable
fun ScreenCareEffect(state: ScreenCareState, autoBrightness: Boolean, pauseInPocket: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val target = ScreenCare.targetBrightness(state, autoBrightness, pauseInPocket)

    DisposableEffect(target) {
        setWindowBrightness(activity.window, target)
        onDispose { setWindowBrightness(activity.window, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
}

private fun setWindowBrightness(window: android.view.Window, value: Float) {
    val params = window.attributes
    params.screenBrightness = value
    window.attributes = params
}

/** Black cover shown while the phone is in a pocket. Recording keeps running underneath. */
@Composable
fun PocketOverlay(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("In pocket - recording continues", color = Color.White.copy(alpha = 0.35f))
    }
}
