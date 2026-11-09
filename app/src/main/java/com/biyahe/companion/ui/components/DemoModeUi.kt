package com.biyahe.companion.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import com.biyahe.companion.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.DemoMode
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard

/**
 * Red strip at the very top of every screen while demo mode is on, with the button that starts
 * Chanelly's tour. Handles the status bar itself.
 */
@Composable
fun DemoBanner(onAskChanelly: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(PosterRed)
            .statusBarsPadding()
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "DEMO MODE  ·  NOTHING IS UPLOADED",
            color = PosterWhite,
            style = PosterDisplay.copy(fontSize = 13.sp, letterSpacing = 1.sp),
            modifier = Modifier.weight(1f)
        )
        Row(
            Modifier
                .background(Ink)
                .clickable(onClick = onAskChanelly)
                .padding(start = 2.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.chanelly), contentDescription = null, modifier = Modifier.size(30.dp))
            Text("ASK CHANELLY", color = PosterWhite, style = PosterDisplay.copy(fontSize = 13.sp, letterSpacing = 1.sp))
        }
    }
}

/**
 * Home screen card with the demo mode switch. Switching asks first (the app restarts), and is
 * blocked while a trip is recording so a real trip is never cut off.
 */
@Composable
fun DemoModeCard(isDemo: Boolean, recording: Boolean, modifier: Modifier = Modifier) {
    val activity = LocalContext.current.findActivity()
    var confirm by remember { mutableStateOf(false) }

    Row(
        modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadow = if (isDemo) PosterRed else Ink, shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("DEMO MODE", style = PosterDisplay.copy(fontSize = 18.sp), color = if (isDemo) PosterRed else Ink)
            Text(
                when {
                    recording -> "Finish the current trip before switching."
                    isDemo -> "Showing sample trips and hazards. Your real data is untouched."
                    else -> "Show sample trips and hazards for presenting. Real data stays separate."
                },
                style = MaterialTheme.typography.bodySmall,
                color = PosterGrey
            )
        }
        Switch(
            checked = isDemo,
            onCheckedChange = { confirm = true },
            enabled = !recording && activity != null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PosterWhite,
                checkedTrackColor = PosterRed,
                uncheckedThumbColor = PosterWhite,
                uncheckedTrackColor = PosterGrey,
                uncheckedBorderColor = Ink
            )
        )
    }

    if (confirm && activity != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (isDemo) "Leave demo mode?" else "Turn on demo mode?") },
            text = {
                Text(
                    if (isDemo) {
                        "The app restarts with your real trips and hazards, and cloud sync works again."
                    } else {
                        "The app restarts with sample trips and hazards. Anything you record in demo mode " +
                            "stays in the demo data and is never uploaded. Your real data is not changed."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { DemoMode.switchAndRestart(activity, !isDemo) }) { Text("Restart now") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
