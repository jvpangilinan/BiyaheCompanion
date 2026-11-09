package com.biyahe.companion.ui.admin

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.admin.AdminHazard
import com.biyahe.companion.admin.AdminSnapshot
import com.biyahe.companion.admin.Agencies
import com.biyahe.companion.admin.Agency
import com.biyahe.companion.ui.tour.LocalGuideTour
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- A look of its own: dark "command center", rounded cards, the default system font -----------------
private val Bg = Color(0xFF0F172A)
private val Card1 = Color(0xFF1E293B)
private val Line = Color(0xFF334155)
private val TextMain = Color(0xFFE2E8F0)
private val TextDim = Color(0xFF94A3B8)
private val Accent = Color(0xFF38BDF8)
private val Good = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val Bad = Color(0xFFF87171)

private val AdminColors = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF082F49), secondary = Good,
    background = Bg, surface = Card1, onSurface = TextMain, onBackground = TextMain,
    surfaceVariant = Card1, onSurfaceVariant = TextDim, outline = Line
)

/**
 * The administrator app: a completely separate UI from the rider app. Admins see every rider's
 * trips and hazard reports, and route hazard reports to the right government office.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApp(onLogout: () -> Unit) {
    val container = (LocalContext.current.applicationContext as BiyaheApp).container
    val vm: AdminViewModel = viewModel(factory = viewModelFactory { initializer { AdminViewModel(container) } })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    MaterialTheme(colorScheme = AdminColors, typography = Typography()) {
        Scaffold(
            containerColor = Bg,
            contentWindowInsets = WindowInsets.safeDrawing,   // whatever the outer layout hasn't covered yet
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    modifier = Modifier.padding(top = 6.dp),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain),
                    title = {
                        Column {
                            Text("Biyahe Admin", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                (if (vm.isDemo) "DEMO DATA · " else "") + "Signed in as ${vm.adminName}",
                                color = if (vm.isDemo) Warn else TextDim, fontSize = 12.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = TextMain) }
                        IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, "Log out", tint = TextMain) }
                    }
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad).padding(bottom = 8.dp).fillMaxSize().background(Bg)) {
                TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent) {
                    val tour = LocalGuideTour.current
                    listOf("Overview", "Riders", "Hazards", "Safety").forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i; tour?.signal("admin_tab_$i") },
                            text = { Text(t) },
                            modifier = Modifier.tourTarget(TourTargets.adminTab(i))
                        )
                    }
                }
                when {
                    ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ui.error != null -> ErrorBox(ui.error!!, vm::refresh)
                    else -> when (tab) {
                        0 -> Overview(ui.data, vm) { tab = 2 }
                        1 -> Riders(ui.data)
                        2 -> Hazards(ui.data, vm)
                        else -> SafetyTab(ui.data, vm)
                    }
                }
            }
        }
    }
}

// ---- Overview -----------------------------------------------------------------------------------------

@Composable
private fun Overview(d: AdminSnapshot, vm: AdminViewModel, openHazards: () -> Unit) {
    val trips = d.trips
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Community overview", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        item {
            KpiGrid(
                modifier = Modifier.tourTarget(TourTargets.ADMIN_KPIS),
                items = listOf(
                    "Riders" to d.users.size.toString(),
                    "Trips" to trips.size.toString(),
                    "Distance" to "%.0f km".format(Locale.US, trips.sumOf { it.distanceKm }),
                    "Avg comfort" to (if (trips.isEmpty()) "-" else trips.map { it.score }.average().toInt().toString()),
                    "Hazard reports" to d.hazards.size.toString(),
                    "Waiting to send" to d.pendingHazards.size.toString(),
                    "Speed warnings" to trips.sumOf { it.speedWarnings }.toString(),
                    "Lane changes" to trips.sumOf { it.weaving }.toString()
                )
            )
        }
        item {
            Panel("Comfort mix") {
                val s = trips.count { it.score >= 80 }; val f = trips.count { it.score in 60..79 }; val r = trips.count { it.score < 60 }
                StackBar(listOf(s to Good, f to Warn, r to Bad))
                Text("Smooth $s · Fair $f · Rough $r", color = TextDim, fontSize = 13.sp)
            }
        }
        item {
            Panel("Comfort Score vs how riders felt") {
                val rated = trips.filter { it.rating in 1..5 }
                if (rated.isEmpty()) {
                    Text("No ratings yet. Riders rate each trip with 1 to 5 stars on the Trip Summary.", color = TextDim, fontSize = 13.sp)
                } else {
                    (5 downTo 1).forEach { stars ->
                        val group = rated.filter { it.rating == stars }
                        val avg = if (group.isEmpty()) null else group.map { it.score }.average()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★".repeat(stars), color = Warn, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Line)) {
                                if (avg != null) Box(Modifier.fillMaxWidth((avg / 100).toFloat()).fillMaxHeight().background(scoreColor(avg.toInt())))
                            }
                            Text(if (avg == null) "  -" else "  %.0f (%d)".format(Locale.US, avg, group.size), color = TextDim, fontSize = 13.sp,
                                modifier = Modifier.width(72.dp))
                        }
                    }
                    Text("Average Comfort Score for each star rating (number of trips). If the score is right, more stars should mean a higher score.",
                        color = TextDim, fontSize = 12.sp)
                }
            }
        }
        item {
            Panel("Hazards by type") {
                val byType = d.hazards.groupBy { it.typeLabel }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
                val max = byType.maxOfOrNull { it.second } ?: 1
                if (byType.isEmpty()) Text("No reports yet.", color = TextDim)
                byType.forEach { (t, n) -> BarRow(t, n, max) }
            }
        }
        if (!vm.isDemo) item {
            val context = LocalContext.current
            Panel("Sample data (for demos and testing)") {
                val samples = d.users.count { it.uid.startsWith("sample-") }
                Text(
                    if (samples > 0) "$samples sample riders are in the database (names end in \"(sample)\")."
                    else "Fill the dashboard with 5 made-up riders, their trips, ratings and hazard reports. Real riders are never touched.",
                    color = TextDim, fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { vm.addSampleRiders(context) }, enabled = samples == 0) { Text("Add 5 sample riders") }
                    OutlinedButton(onClick = vm::removeSampleRiders, enabled = samples > 0) { Text("Remove them") }
                }
            }
        }
        item {
            Panel("Needs action") {
                Text("${d.pendingHazards.size} hazard report(s) have not been sent to an agency yet.", color = TextMain)
                Button(onClick = openHazards, modifier = Modifier.padding(top = 8.dp)) { Text("Review hazards") }
            }
        }
    }
}

// ---- Riders ---------------------------------------------------------------------------------------------

@Composable
private fun Riders(d: AdminSnapshot) {
    var open by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("${d.users.size} riders", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        items(d.users, key = { it.uid }) { u ->
            val trips = d.tripsOf(u.uid).sortedByDescending { it.startTime }
            Card(
                colors = CardDefaults.cardColors(containerColor = Card1),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { open = if (open == u.uid) null else u.uid }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.name, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text(u.email, color = TextDim, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("${trips.size} trips")
                        Pill("avg ${if (trips.isEmpty()) "-" else trips.map { it.score }.average().toInt()}")
                        Pill("${trips.sumOf { it.speedWarnings }} speed", if (trips.sumOf { it.speedWarnings } > 3) Bad else null)
                        Pill("${d.hazardsOf(u.uid).size} hazards")
                    }
                    if (open == u.uid) {
                        Spacer(Modifier.height(4.dp))
                        if (trips.isEmpty()) Text("No trips synced.", color = TextDim)
                        trips.take(8).forEach { t ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(day(t.startTime), color = TextDim, fontSize = 13.sp, modifier = Modifier.width(92.dp))
                                Text("%.1f km".format(Locale.US, t.distanceKm), color = TextMain, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                                Text("${t.score} ${t.label}", color = scoreColor(t.score), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("⚠ ${t.speedWarnings}  ⇄ ${t.weaving}", color = TextDim, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Hazards ---------------------------------------------------------------------------------------------

@Composable
private fun Hazards(d: AdminSnapshot, vm: AdminViewModel) {
    var filter by remember { mutableIntStateOf(1) } // 0 all, 1 waiting, 2 sent
    var sending by remember { mutableStateOf<AdminHazard?>(null) }
    val context = LocalContext.current
    val list = when (filter) {
        1 -> d.hazards.filter { d.reportFor(it.key) == null }
        2 -> d.hazards.filter { d.reportFor(it.key) != null }
        else -> d.hazards
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Waiting", "Sent").forEachIndexed { i, t ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t) })
                }
            }
        }
        if (list.isEmpty()) item { Text("Nothing here.", color = TextDim) }
        items(list, key = { it.key }) { h ->
            val first = h.key == list.firstOrNull()?.key
            val report = d.reportFor(h.key)
            Card(colors = CardDefaults.cardColors(containerColor = Card1), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Photo(h, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(h.typeLabel, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        if (h.note.isNotBlank()) Text(h.note, color = TextMain, fontSize = 13.sp)
                        Text("${day(h.timestamp)} · by ${d.userName(h.uid)}", color = TextDim, fontSize = 12.sp)
                        Text(
                            "%.5f, %.5f  ·  open map".format(Locale.US, h.lat, h.lon), color = Accent, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${h.lat},${h.lon}?q=${h.lat},${h.lon}(${Uri.encode(h.typeLabel)})"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        )
                        Text("Recommended: " + Agencies.forHazard(h).joinToString(" or ") { it.short }, color = TextDim, fontSize = 12.sp)
                        if (report != null) {
                            Pill("Sent to ${report.agency} · ${day(report.sentAtMs)}", Good)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("Waiting", Warn)
                                Button(
                                    onClick = { sending = h }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = if (first) Modifier.tourTarget(TourTargets.ADMIN_SEND) else Modifier
                                ) {
                                    Text("Send report")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sending?.let { h ->
        val recommended = Agencies.forHazard(h)
        AlertDialog(
            onDismissRequest = { sending = null },
            containerColor = Card1,
            title = { Text("Send ${h.typeLabel.lowercase()} report to...", color = TextMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (recommended + Agency.entries.filter { it !in recommended }).forEach { a ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Bg)
                                .clickable { vm.sendHazardReport(context, h, a); sending = null }
                                .padding(10.dp)
                        ) {
                            Text(a.short + if (a in recommended) "  · recommended" else "", color = if (a in recommended) Accent else TextMain,
                                fontWeight = FontWeight.SemiBold)
                            Text(a.handles, color = TextDim, fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Your email or messaging app opens with the report and photo ready. Add the office's official address there. " +
                            "The rider's name is never included.",
                        color = TextDim, fontSize = 12.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { sending = null }) { Text("Cancel") } }
        )
    }
}

// ---- Safety ------------------------------------------------------------------------------------------------

@Composable
private fun SafetyTab(d: AdminSnapshot, vm: AdminViewModel) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Road safety", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            KpiGrid(
                listOf(
                    "Speed warnings" to d.trips.sumOf { it.speedWarnings }.toString(),
                    "Minutes over limit" to (d.trips.sumOf { it.overspeedSec } / 60).toString(),
                    "Lane changes" to d.trips.sumOf { it.weaving }.toString(),
                    "Big bumps" to d.trips.sumOf { it.bumps }.toString()
                )
            )
        }
        item {
            Panel("Riders to coach (most speed warnings per trip)") {
                val ranked = d.users.map { u ->
                    val t = d.tripsOf(u.uid)
                    Triple(u.name, if (t.isEmpty()) 0.0 else t.sumOf { it.speedWarnings }.toDouble() / t.size, t.sumOf { it.weaving })
                }.sortedByDescending { it.second }
                val max = ranked.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
                ranked.forEach { (name, perTrip, weave) ->
                    Column {
                        Row { Text(name, color = TextMain, modifier = Modifier.weight(1f)); Text("%.1f / trip · ⇄ %d".format(Locale.US, perTrip, weave), color = TextDim, fontSize = 13.sp) }
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line)) {
                            Box(Modifier.fillMaxWidth((perTrip / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(if (perTrip >= 1) Bad else Warn))
                        }
                    }
                }
            }
        }
        item {
            Panel("Send a summary", Modifier.tourTarget(TourTargets.ADMIN_SUMMARY)) {
                Text("Totals only, no rider names. Recommended: LTO and PNP-HPG.", color = TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Agencies.forSafetyTrends.forEach { a ->
                        OutlinedButton(onClick = { vm.sendSafetySummary(context, a) }) { Text("To ${a.short}") }
                    }
                }
            }
        }
    }
}

// ---- Building blocks -----------------------------------------------------------------------------------------

@Composable
private fun KpiGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value) ->
                    Card(colors = CardDefaults.cardColors(containerColor = Card1), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(value, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(label, color = TextDim, fontSize = 13.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Card1), shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun StackBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Line)) {
        parts.filter { it.first > 0 }.forEach { (n, c) -> Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().background(c)) }
    }
}

@Composable
private fun BarRow(label: String, n: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, fontSize = 14.sp, modifier = Modifier.width(110.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Line)) {
            Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().background(Accent))
        }
        Text("  $n", color = TextDim, fontSize = 14.sp)
    }
}

@Composable
private fun Pill(text: String, color: Color? = null) {
    Text(
        text, color = color ?: TextMain, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background((color ?: TextDim).copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun Photo(h: AdminHazard, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, h.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                when {
                    h.photoBase64 != null -> Base64.decode(h.photoBase64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size, opts) }
                    h.photoPath != null && File(h.photoPath).exists() -> BitmapFactory.decodeFile(h.photoPath, opts)
                    else -> null
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) Image(b, contentDescription = "Hazard photo", modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(Line), contentAlignment = Alignment.Center) { Text("No photo", color = TextDim, fontSize = 11.sp) }
}

@Composable
private fun ErrorBox(msg: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(msg, color = Bad)
        Button(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
    }
}

private fun day(ms: Long) = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ms))
private fun scoreColor(s: Int) = when { s >= 80 -> Good; s >= 60 -> Warn; else -> Bad }
