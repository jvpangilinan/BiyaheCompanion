package com.biyahe.companion.ui.safety

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.R
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget

private data class Rule(val title: String, val body: String, val critical: Boolean = false)

/** Edit the rules here. The first two are the most important and are shown in red. */
private val RULES = listOf(
    Rule(
        "Never use the phone while driving",
        "Set everything up before you move. Warnings are spoken out loud, so you never need to look at or touch the screen. " +
            "Using a phone while driving is against the law (Anti-Distracted Driving Act, RA 10913).",
        critical = true
    ),
    Rule(
        "Mount it: stable, level, out of the way",
        "Put the phone in a holder on a flat, stable spot in the car, such as the dashboard. It should stay still, be easy to glance at, " +
            "and never block your view of the road. A loose phone sliding around gives wrong bump, turn and crash readings.",
        critical = true
    ),
    Rule(
        "Start before you go, end after you park",
        "Tap Start Trip while parked. When you arrive, park first, then tap End Trip."
    ),
    Rule(
        "Passengers can use it freely",
        "Riding a jeepney, bus or car as a passenger? You can hold the phone and look at it. The rules above are for whoever is driving."
    ),
    Rule(
        "Warnings help, they don't replace you",
        "GPS speed can be a few km/h off, and road signs always beat the app's speed limit. Crash detection and the weaving warning " +
            "can miss things or raise a false alarm. Stay alert and drive carefully."
    ),
    Rule(
        "Don't drive drowsy",
        "Answer the morning sleep check honestly. If you slept badly, take breaks or let someone else drive."
    ),
    Rule(
        "In an emergency",
        "After a crash alarm, tap I'M OK if you're fine. If not, your emergency contacts get a text with your location after 30 seconds. " +
            "The CALL 911 button opens the phone app with 911 ready: if you can, call yourself."
    ),
    Rule(
        "Your data",
        "Trips stay on this phone and upload only to your own private cloud space. Sleep answers and emergency contacts never leave the phone. " +
            "The microphone only measures loudness, it never records sound."
    ),
    Rule(
        "Battery and charging",
        "GPS and sensors use battery. Keep the phone charging on long trips."
    )
)

/**
 * "Read me first": the safety rules, shown before the app is used the first time and again from
 * the Safety tab. Nothing behind it can be tapped until it is accepted.
 */
@Composable
fun ReadMeFirstScreen(firstTime: Boolean, onAccept: () -> Unit) {
    var understood by remember { mutableStateOf(!firstTime) }
    if (firstTime) BackHandler { /* must be accepted first */ } else BackHandler(onBack = onAccept)

    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("READ ME FIRST", color = PosterRed, style = PosterDisplay.copy(fontSize = 16.sp, letterSpacing = 3.sp))
                    PosterHeading("Safety rules", fontSize = 32.sp)
                }
                Image(painterResource(R.drawable.chanelly), contentDescription = "Chanelly", modifier = Modifier.size(84.dp))
            }
            Text(
                "Biyahe Companion helps on the road, but only if it is used safely. Please read these before your first trip.",
                style = MaterialTheme.typography.bodyLarge, color = Ink
            )

            RULES.forEachIndexed { i, r -> RuleCard(i + 1, r) }

            Column(Modifier.tourTarget(TourTargets.RULES_ACCEPT)) {
            Row(
                Modifier.fillMaxWidth().clickable { understood = !understood }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = understood, onCheckedChange = { understood = it },
                    colors = CheckboxDefaults.colors(checkedColor = PosterRed, uncheckedColor = Ink, checkmarkColor = PosterWhite)
                )
                Text("I have read these rules and will follow them.", style = MaterialTheme.typography.titleMedium, color = Ink)
            }
            PosterButton(
                if (firstTime) "I understand, let's go" else "Close",
                onAccept, Modifier.fillMaxWidth(), enabled = understood
            )
            }
            Text("You can read these again any time from the Safety tab.", style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RuleCard(n: Int, r: Rule) {
    Row(
        Modifier
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadow = if (r.critical) PosterRed else Ink, shadowOffset = 4.dp, borderWidth = 2.dp)
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).background(if (r.critical) PosterRed else Ink),
            contentAlignment = Alignment.Center
        ) { Text(n.toString(), color = PosterWhite, style = PosterDisplay.copy(fontSize = 20.sp)) }
        Column(Modifier.weight(1f)) {
            Text(r.title.uppercase(), color = if (r.critical) PosterRed else Ink, style = PosterDisplay.copy(fontSize = 19.sp))
            Spacer(Modifier.width(2.dp))
            Text(r.body, color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
