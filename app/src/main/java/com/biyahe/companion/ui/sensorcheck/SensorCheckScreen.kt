package com.biyahe.companion.ui.sensorcheck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.sensors.SensorKind
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.PocketOverlay
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.ScreenCareEffect
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import com.biyahe.companion.ui.theme.posterCard

/**
 * Sensor check: the list of all 11 components, and a tap on a testable one opens that sensor's
 * own test screen (SensorTests.kt). Showing the test replaces the list, so the list's live
 * readings (and the screen dimming / pocket effect) are switched off while you test one sensor.
 */
@Composable
fun SensorCheckScreen(
    onBack: () -> Unit,
    viewModel: SensorCheckViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { name -> SensorKind.entries.firstOrNull { it.name == name } }

    if (selected != null) {
        val next = selected.nextInStory()
        SensorTestScreen(
            kind = selected,
            onBack = { selectedName = null },
            onNext = { selectedName = next?.name },   // after the last sensor: back to the list
            nextTitle = next?.story()?.stageTitle,
            viewModel = viewModel
        )
    } else {
        SensorList(onBack = onBack, onOpen = { selectedName = it.name }, viewModel = viewModel)
    }
}

@Composable
private fun SensorList(onBack: () -> Unit, onOpen: (SensorKind) -> Unit, viewModel: SensorCheckViewModel) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val care by viewModel.screenCare.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Light + proximity sensors in action: dim at night, black out in a pocket.
    ScreenCareEffect(care, settings.autoBrightness, settings.pauseScreenInPocket)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Column {
                    PosterHeading("Sensor check", fontSize = 24.sp)
                    Text(
                        "${rows.count { it.available }} of ${rows.size} sensors working. Tap one to try it live.",
                        style = MaterialTheme.typography.bodyMedium, color = PosterGrey
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // In demo order, with a heading each time the group changes.
                var lastGroup = ""
                rows.sortedBy { it.kind.story().order }.forEach { row ->
                    val group = row.kind.story().group
                    if (group != lastGroup) {
                        lastGroup = group
                        item(key = "group-$group") { GroupHeader(group) }
                    }
                    item(key = row.kind.name) { SensorRowCard(row, onClick = { onOpen(row.kind) }) }
                }
            }
        }
        PocketOverlay(visible = care.inPocket && settings.pauseScreenInPocket)
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(top = 6.dp)
            .drawBehind { drawRect(PosterRed, size = Size(6.dp.toPx(), size.height)) }
            .padding(start = 12.dp),
        style = PosterDisplay.copy(fontSize = 18.sp, letterSpacing = 2.sp),
        color = Ink
    )
}

@Composable
private fun SensorRowCard(row: SensorRow, onClick: () -> Unit) {
    val story = row.kind.story()
    val testable = row.available && row.kind in testableSensors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .then(if (testable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(row.kind),
            contentDescription = null,
            tint = PosterWhite,
            modifier = Modifier.size(42.dp).background(Ink).padding(9.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${story.order}  ${story.stageTitle}".uppercase(),
                style = PosterDisplay.copy(fontSize = 18.sp), color = Ink
            )
            Text(story.pitch, style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
            Text(row.kind.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = PosterRed)
            if (row.available && row.reading != null) {
                Text(row.reading, style = MaterialTheme.typography.labelLarge, color = Ink)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (testable) {
            Text("TEST", color = PosterRed, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
        }
        // Square status light: ink = working, red = missing.
        Box(Modifier.size(14.dp).background(if (row.available) Ink else PosterRed))
    }
}

// One look for every sensor: ink tile, white icon. Only the icon tells them apart.
private fun iconFor(kind: SensorKind): ImageVector = when (kind) {
    SensorKind.ACCELEROMETER -> Icons.Filled.Vibration
    SensorKind.GYROSCOPE -> Icons.Filled.ScreenRotation
    SensorKind.MAGNETOMETER -> Icons.Filled.Explore
    SensorKind.GPS -> Icons.Filled.MyLocation
    SensorKind.BAROMETER -> Icons.Filled.Speed
    SensorKind.MICROPHONE -> Icons.Filled.Mic
    SensorKind.CAMERA -> Icons.Filled.CameraAlt
    SensorKind.AMBIENT_LIGHT -> Icons.Filled.LightMode
    SensorKind.PROXIMITY -> Icons.Filled.Sensors
    SensorKind.STEP_COUNTER -> Icons.AutoMirrored.Filled.DirectionsWalk
    SensorKind.FINGERPRINT -> Icons.Filled.Fingerprint
}
