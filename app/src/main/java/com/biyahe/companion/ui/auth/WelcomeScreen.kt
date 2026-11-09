package com.biyahe.companion.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.DemoMode
import com.biyahe.companion.R
import com.biyahe.companion.ui.components.PosterHero
import com.biyahe.companion.ui.components.PosterLogo
import com.biyahe.companion.ui.components.PosterTagline
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.Paper
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.hardShadow
import com.biyahe.companion.util.findActivity

/**
 * The very first screen: use the real app, or try demo mode with Chanelly as the guide.
 * Picking the other mode restarts the app into it (it uses a different database).
 */
@Composable
fun WelcomeScreen(onContinue: (demoTour: Boolean) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container

    fun pick(demo: Boolean) {
        if (demo == container.isDemo) onContinue(demo)
        else context.findActivity()?.let { DemoMode.switchAndRestart(it, demo) }   // lands on log-in, tour ready
    }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        PosterHero(height = 220.dp) {
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 24.dp, vertical = 16.dp)) {
                PosterLogo(big = 52.sp, small = 24.sp)
                Spacer(Modifier.height(10.dp))
                PosterTagline(fontSize = 14.sp)
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.chanelly), contentDescription = "Chanelly", modifier = Modifier.size(88.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text("MABUHAY!", color = PosterRed, style = PosterDisplay.copy(fontSize = 26.sp))
                    Text("How would you like to start?", color = Ink, fontSize = 20.sp, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Choice(
                title = "Use the app",
                body = "Log in with Google or a username. Your own trips and reports.",
                fill = Ink, shadow = PosterRed
            ) { pick(demo = false) }

            Choice(
                title = "Demo mode",
                body = "Chanelly shows you everything, step by step. Sample data, no internet needed. Great for kids, adults and lolos and lolas!",
                fill = PosterRed, shadow = Ink
            ) { pick(demo = true) }

            Text(
                if (container.isDemo) "Right now: demo mode." else "Right now: the real app.",
                color = PosterGrey, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Choice(title: String, body: String, fill: Color, shadow: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .hardShadow(shadow, 6.dp)
            .background(fill)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title.uppercase(), color = PosterWhite, style = PosterDisplay.copy(fontSize = 28.sp))
        Text(body, color = PosterWhite, fontSize = 17.sp, lineHeight = 23.sp, style = MaterialTheme.typography.bodyLarge)
    }
}
