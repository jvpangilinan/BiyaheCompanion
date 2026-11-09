package com.biyahe.companion.ui.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.biyahe.companion.safety.CrashMonitor
import com.biyahe.companion.safety.CrashState
import com.biyahe.companion.safety.EmergencyContact
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite

/**
 * Full-screen crash screen, drawn above everything (even the lock screen: in an emergency
 * nobody should need a fingerprint to call for help).
 */
@Composable
fun CrashOverlay(state: CrashState, monitor: CrashMonitor, contacts: List<EmergencyContact>, isDemo: Boolean) {
    if (state == CrashState.Idle) return
    BackHandler { /* back does nothing: answer with a button */ }
    var note by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(if (state is CrashState.Countdown) PosterRed else Ink)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } // nothing behind is tappable
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isDemo) Text("STAGE DEMO", color = PosterWhite, style = PosterDisplay.copy(fontSize = 16.sp, letterSpacing = 3.sp))
            when (state) {
                is CrashState.Countdown -> {
                    Text(if (state.cause.startsWith("SOS")) "SOS" else "CRASH DETECTED", color = PosterWhite, style = PosterDisplay.copy(fontSize = 40.sp), textAlign = TextAlign.Center)
                    Text("Are you OK?", color = PosterWhite, style = MaterialTheme.typography.headlineSmall)
                    Text(state.secondsLeft.toString(), color = PosterWhite, style = PosterDisplay.copy(fontSize = 110.sp))
                    Text(
                        "Your emergency contacts will be alerted when this reaches 0.",
                        color = PosterWhite, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
                    )
                    BigButton("I'M OK", fill = PosterWhite, textColor = Ink, height = 96.dp) { monitor.imOk() }
                }
                is CrashState.Alerted -> {
                    Text("CONTACTS ALERTED", color = PosterRed, style = PosterDisplay.copy(fontSize = 34.sp), textAlign = TextAlign.Center)
                    state.report.forEach {
                        Text(it, color = PosterWhite, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
                CrashState.Idle -> Unit
            }

            Spacer(Modifier.height(4.dp))
            // The big call buttons, in both states.
            BigButton("CALL ${CrashMonitor.EMERGENCY_NUMBER}", fill = if (state is CrashState.Countdown) Ink else PosterRed,
                textColor = PosterWhite, height = 110.dp) {
                if (isDemo) {
                    note = "Demo mode: ${CrashMonitor.EMERGENCY_NUMBER} was NOT called. In a real crash this opens the phone app with ${CrashMonitor.EMERGENCY_NUMBER} ready; tap call there."
                } else {
                    dialEmergency(context)
                }
            }
            contacts.forEach { c ->
                BigButton("CALL ${c.name.uppercase()}", fill = PosterWhite, textColor = Ink, height = 72.dp) {
                    note = callNow(context, c.phone)
                }
            }
            note?.let { Text(it, color = PosterWhite, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }

            if (state is CrashState.Alerted) {
                BigButton("CLOSE", fill = Color.Transparent, textColor = PosterWhite, height = 56.dp) { monitor.dismiss() }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, fill: Color, textColor: Color, height: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, style = PosterDisplay.copy(fontSize = 30.sp, letterSpacing = 2.sp), textAlign = TextAlign.Center)
    }
}

/**
 * Calls a contact straight away (no extra screen) when the Call permission is granted;
 * otherwise opens the dialer with the number filled in.
 */
private fun callNow(context: Context, number: String): String? {
    val uri = Uri.parse("tel:$number")
    val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    return runCatching {
        context.startActivity(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (canCall) null else "Calling permission is off, so the phone app opened with the number ready."
    }.getOrElse { "Could not start the call." }
}

/** Android lets only the phone app itself place emergency calls, so 911 opens the dialer, ready to tap. */
private fun dialEmergency(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${CrashMonitor.EMERGENCY_NUMBER}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
