package com.biyahe.companion.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.ui.AppViewModelProvider
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.StatCard
import com.biyahe.companion.ui.components.TripRowCard
import com.biyahe.companion.ui.theme.BiyaheNavy
import com.biyahe.companion.ui.util.formatKm

@Composable
fun HistoryScreen(
    onOpenTrip: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterHeading("History", fontSize = 30.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Walking steps", state.totalSteps.toString(), Modifier.weight(1f))
                    StatCard("Total distance", formatKm(state.totalKm), Modifier.weight(1f))
                }
            }
        }
        if (state.loaded && state.trips.isEmpty()) {
            item { Text("No trips yet. Record one from the Trip tab.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        }
        items(state.trips, key = { it.id }) { stored ->
            TripRowCard(stored, onClick = { onOpenTrip(stored.id) })
        }
    }
}
