package com.biyahe.companion.ui.hazards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.data.local.entity.Hazard
import com.biyahe.companion.data.model.HazardType
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.posterCard
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.sp
import com.biyahe.companion.ui.theme.BiyaheGreen
import com.biyahe.companion.ui.theme.BiyaheNavy
import com.biyahe.companion.ui.util.formatDate
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import java.util.Locale

@Composable
fun HazardsScreen(
    onReport: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HazardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val hazards by viewModel.hazards.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<Hazard?>(null) }

    val waiting = hazards.count { !it.cloudSynced }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterHeading("Hazards", fontSize = 30.sp)
                PosterButton("Report a hazard", onReport, Modifier.fillMaxWidth().tourTarget(TourTargets.HAZARDS_REPORT))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                viewModel.isDemo -> "Demo mode: reports stay on this phone"
                                waiting == 0 -> "All reports uploaded"
                                else -> "$waiting waiting to upload"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (sync.message.isNotBlank()) {
                            Text(sync.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = viewModel::syncNow, enabled = !sync.running) {
                        Text(if (sync.running) "Syncing..." else "Sync now")
                    }
                }
            }
        }

        if (hazards.isEmpty()) {
            item {
                Text(
                    "No hazard reports yet. Tap \"Report a hazard\" to photograph a pothole, flood or other road problem.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        items(hazards, key = { it.id }) { hazard ->
            HazardCard(hazard, isDemo = viewModel.isDemo, onEdit = { onEdit(hazard.id) }, onDelete = { toDelete = hazard })
        }
    }

    toDelete?.let { h ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this report?") },
            text = { Text("It's removed from this phone and from your account in the cloud. (Offline? Then only the phone copy is removed.)") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(h); toDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HazardCard(hazard: Hazard, isDemo: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 4.dp, bottom = 4.dp)
            .posterCard(shadowOffset = 4.dp, borderWidth = 2.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HazardPhoto(
            path = hazard.photoPath,
            modifier = Modifier.size(84.dp).border(2.dp, Ink, RectangleShape),
            maxSidePx = 300
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                HazardType.fromName(hazard.type).label.uppercase(),
                style = PosterDisplay.copy(fontSize = 22.sp),
                color = Ink
            )
            if (hazard.note.isNotBlank()) {
                Text(hazard.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Text(formatDate(hazard.timestamp), style = MaterialTheme.typography.bodySmall, color = PosterGrey)
            Text(
                String.format(Locale.US, "%.5f, %.5f", hazard.latitude, hazard.longitude),
                style = MaterialTheme.typography.bodySmall, color = PosterGrey
            )
            Text(
                when {
                    isDemo -> "DEMO, NOT UPLOADED"
                    hazard.cloudSynced -> "UPLOADED"
                    else -> "WAITING TO UPLOAD"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isDemo) PosterGrey else if (hazard.cloudSynced) Ink else PosterRed
            )
        }
        Column {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit report", tint = Ink)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete report", tint = Ink)
            }
        }
    }
}
