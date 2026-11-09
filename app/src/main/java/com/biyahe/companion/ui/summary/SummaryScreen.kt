package com.biyahe.companion.ui.summary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biyahe.companion.trip.CompletedTrip
import com.biyahe.companion.ui.components.FastRunTag
import com.biyahe.companion.ui.components.PosterButton
import com.biyahe.companion.ui.components.PosterHeading
import com.biyahe.companion.ui.components.PosterRow
import com.biyahe.companion.ui.components.RideKindCard
import com.biyahe.companion.ui.components.RouteMap
import com.biyahe.companion.ui.components.ScoreRing
import com.biyahe.companion.ui.components.labelColor
import com.biyahe.companion.ui.components.rideKindFor
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterDisplay
import com.biyahe.companion.ui.theme.PosterGrey
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.posterCard
import com.biyahe.companion.ui.util.formatDate
import com.biyahe.companion.ui.util.formatDuration
import com.biyahe.companion.ui.util.formatKm
import java.util.Locale

/** The poster's Trip Summary card, the result card for the ride, then the route and details. */
@Composable
fun SummaryScreen(
    tripId: Long,
    onBack: () -> Unit,
    onReportHazard: () -> Unit,
    viewModel: SummaryViewModel = viewModel(key = "summary-$tripId", factory = SummaryViewModel.factory(tripId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trip = state.stored?.trip

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.tourTarget(TourTargets.SUMMARY_BACK)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Column {
                PosterHeading("Trip summary", fontSize = 24.sp)
                if (trip != null) Text(formatDate(trip.startedAtMs), style = MaterialTheme.typography.bodyMedium, color = PosterGrey)
            }
        }

        when {
            !state.loaded -> Unit
            trip == null -> Text("This trip could not be found.", Modifier.padding(16.dp))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ScoreCard(trip, onReportHazard)

                RateRideCard(trip.riderRating, onRate = viewModel::rate)

                ScoreBreakdownCard(trip, Modifier.tourTarget(TourTargets.SUMMARY_BREAKDOWN))

                rideKindFor(trip.comfortLabel, trip.isFastRun)?.let { RideKindCard(it) }

                Column(Modifier.tourTarget(TourTargets.SUMMARY_ROUTE).padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp).fillMaxWidth()) {
                    RouteMap(
                        points = state.route,
                        roughRoute = state.roughRoute,
                        interactive = false,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clipToBounds()
                    )
                    if (state.roughRoute.any { it.roughness != null }) RoughnessLegend()
                    if (state.route.isEmpty()) {
                        Text("No route was recorded for this trip.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(Modifier.padding(14.dp)) {
                        PosterRow("Distance", formatKm(trip.distanceKm))
                        PosterRow("Duration", formatDuration(trip.durationMs))
                        PosterRow("Average speed", String.format(Locale.US, "%.0f km/h", trip.avgSpeedKmh))
                        PosterRow("Top speed", String.format(Locale.US, "%.0f km/h", trip.maxSpeedKmh))
                    }
                }

                Column(Modifier.tourTarget(TourTargets.SUMMARY_DETAILS).padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp).fillMaxWidth().padding(14.dp)) {
                    PosterHeading("Ride details", fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    PosterRow("Trip type", if (trip.tripMode == "RIDING") "Riding (passenger)" else "Driving")
                    PosterRow("Big bumps", trip.bumpCount.toString())
                    PosterRow("Sharp turns", trip.sharpTurnCount.toString())
                    PosterRow("Speed limit warnings", trip.speedWarnings.toString())
                    PosterRow("Time over the limit", formatDuration(trip.overspeedSec * 1000))
                    PosterRow("Lane changes / swerves", trip.weavingCount.toString())
                    PosterRow("Average noise", "${trip.avgNoiseDb.toInt()} dB (approx.)")
                    PosterRow("Steps before the ride", trip.stepsBefore.toString())
                    PosterRow("Steps after the ride", trip.stepsAfter.toString())
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ScoreCard(trip: CompletedTrip, onReportHazard: () -> Unit) {
    Column(
        Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .posterCard(shadow = PosterRed, shadowOffset = 8.dp, borderWidth = 3.dp)
            .fillMaxWidth()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TRIP SUMMARY", color = PosterGrey, style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp))
        Column(Modifier.tourTarget(TourTargets.SUMMARY_SCORE), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(trip.comfortScore, size = 170.dp)
            Text("${trip.comfortLabel} ride".uppercase(), color = labelColor(trip.comfortLabel), style = PosterDisplay.copy(fontSize = 28.sp))
        }
        if (trip.isFastRun) FastRunTag()
        Spacer(Modifier.height(2.dp))
        PosterRow("Bumpiness", trip.bumpLevel)
        PosterRow("Noise level", trip.noiseLevel)
        PosterRow("Rain warning", trip.rainRisk)
        PosterRow("Walking steps", if (trip.stepsBefore + trip.stepsAfter > 0) "Logged" else "None")
        PosterButton("Report a hazard", onReportHazard, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

/**
 * "How did the ride feel?" 1-5 stars. Stored with the trip; comparing it with the Comfort Score
 * across many rides shows whether the score matches what people actually feel.
 */
@Composable
private fun RateRideCard(current: Int, onRate: (Int) -> Unit) {
    var stars by androidx.compose.runtime.remember(current) { androidx.compose.runtime.mutableIntStateOf(current) }
    Column(
        Modifier.padding(end = 4.dp, bottom = 4.dp).posterCard(shadowOffset = 4.dp, borderWidth = 2.dp).fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PosterHeading("How did the ride feel?", fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { n ->
                Text(
                    if (n <= stars) "★" else "☆",
                    fontSize = 40.sp,
                    color = if (n <= stars) PosterRed else PosterGrey,
                    modifier = Modifier.clickable { stars = n; onRate(n) }.padding(horizontal = 2.dp)
                )
            }
        }
        Text(
            if (stars == 0) "Tap a star. Your answer helps check that the Comfort Score matches how rides really feel."
            else "Thanks! Saved with this trip.",
            style = MaterialTheme.typography.bodySmall, color = PosterGrey
        )
    }
}
