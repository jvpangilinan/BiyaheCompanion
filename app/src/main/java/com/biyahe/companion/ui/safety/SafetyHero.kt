package com.biyahe.companion.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.safety.SleepMath
import com.biyahe.companion.ui.theme.ComfortSmooth
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterTextShadow
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.hardShadow
import com.biyahe.companion.ui.theme.skewX
import kotlinx.coroutines.flow.MutableStateFlow

/** The four safety features, in the order they appear everywhere. */
enum class SafetySection { SPEED, SLEEP, WEAVING, CRASH, MORE }

/** Which Safety card to scroll to when the Safety tab opens (set by the Home tiles). */
object SafetyNav {
    val focus = MutableStateFlow<SafetySection?>(null)
}

/**
 * "SAFETY FIRST": the headline block. Four big tiles, each with its live status; tapping one
 * opens that feature. Used at the top of Home and of the Safety tab.
 */
@Composable
fun SafetyHeroPanel(onOpen: (SafetySection) -> Unit, modifier: Modifier = Modifier, compactTitle: Boolean = false) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val s by container.safetyStore.settings.collectAsStateWithLifecycle()
    val today by container.safetyStore.todayCheck.collectAsStateWithLifecycle()

    Column(
        modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .hardShadow(PosterRed, 6.dp)
            .background(Ink)
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "SAFETY FIRST",
            modifier = Modifier.skewX(),
            color = PosterWhite,
            style = PosterDisplay.copy(fontSize = if (compactTitle) 30.sp else 38.sp, letterSpacing = 2.sp, shadow = PosterTextShadow)
        )
        Text(
            "Guards that ride with you, powered by the phone's sensors. Every warning is spoken, so eyes stay on the road.",
            color = Color(0xFFDDDDDD), style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(
                Icons.Filled.Speed, "Speed limit",
                if (s.speedLimitOn) "${s.speedLimitKmh} km/h · ON" else "OFF",
                on = s.speedLimitOn, modifier = Modifier.weight(1f)
            ) { onOpen(SafetySection.SPEED) }
            Tile(
                Icons.Filled.Bedtime, "Sleep check",
                when {
                    today == null -> if (s.morningCheckOn) "Not checked yet" else "OFF"
                    today!!.tired -> "${SleepMath.formatHours(today!!.hours)} · TIRED"
                    else -> "${SleepMath.formatHours(today!!.hours)} · RESTED"
                },
                on = s.morningCheckOn, warn = today?.tired == true, modifier = Modifier.weight(1f)
            ) { onOpen(SafetySection.SLEEP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(
                Icons.Filled.SwapCalls, "Lane weaving",
                if (s.weavingWarningOn) "Watching · ON" else "OFF",
                on = s.weavingWarningOn, modifier = Modifier.weight(1f)
            ) { onOpen(SafetySection.WEAVING) }
            Tile(
                Icons.Filled.CarCrash, "Crash alert",
                when {
                    !s.crashDetectionOn -> "OFF"
                    s.contacts.isEmpty() -> "Add a contact!"
                    else -> "${s.contacts.size} contact${if (s.contacts.size == 1) "" else "s"} · ON"
                },
                on = s.crashDetectionOn, warn = s.crashDetectionOn && s.contacts.isEmpty(), modifier = Modifier.weight(1f)
            ) { onOpen(SafetySection.CRASH) }
        }
        val more = s.moreGuardsOn()
        Row(
            Modifier.fillMaxWidth().background(PosterRed).clickable { onOpen(SafetySection.MORE) }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("+ 7 MORE GUARDS · $more ON", color = PosterWhite, style = PosterDisplay.copy(fontSize = 17.sp))
                Text("Eye watch · tunnels & night · rain · hazards ahead · breaks · loud cabin · mount · SOS",
                    color = PosterWhite, style = MaterialTheme.typography.labelSmall)
            }
            Text("›", color = PosterWhite, style = PosterDisplay.copy(fontSize = 28.sp))
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector, title: String, status: String, on: Boolean,
    modifier: Modifier = Modifier, warn: Boolean = false, onClick: () -> Unit
) {
    Column(
        modifier
            .background(PosterWhite)
            .border(2.dp, if (warn) PosterRed else PosterWhite, RectangleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .height(96.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier.size(38.dp).background(if (warn) PosterRed else if (on) Ink else PosterGrey),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = PosterWhite, modifier = Modifier.size(24.dp)) }
        Column {
            Text(title.uppercase(), color = Ink, style = PosterDisplay.copy(fontSize = 17.sp))
            Text(
                status, color = if (warn) PosterRed else if (on) ComfortSmooth else PosterGrey,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
