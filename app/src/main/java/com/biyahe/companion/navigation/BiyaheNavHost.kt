package com.biyahe.companion.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextAlign
import com.biyahe.companion.util.findActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.biyahe.companion.ui.safety.CrashOverlay
import com.biyahe.companion.ui.safety.EyeWatchHost
import com.biyahe.companion.auth.Role
import com.biyahe.companion.auth.AccountDb
import com.biyahe.companion.ui.admin.AdminApp
import com.biyahe.companion.ui.auth.LoginScreen
import com.biyahe.companion.ui.settings.SettingsScreen
import com.biyahe.companion.ui.settings.TuningScreen
import com.biyahe.companion.ui.safety.DriftDemoScreen
import com.biyahe.companion.ui.safety.MorningCheckDialog
import com.biyahe.companion.ui.safety.ReadMeFirstScreen
import com.biyahe.companion.ui.safety.SafetyScreen
import com.biyahe.companion.DemoMode
import com.biyahe.companion.ui.tour.ChanellyOverlay
import com.biyahe.companion.ui.tour.RULES_SCREEN
import com.biyahe.companion.ui.tour.TourAction
import com.biyahe.companion.ui.auth.WelcomeScreen
import com.biyahe.companion.ui.tour.LocalGuideTour
import com.biyahe.companion.ui.tour.TourTargets
import com.biyahe.companion.ui.tour.tourTarget
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.biyahe.companion.ui.theme.Ink
import com.biyahe.companion.ui.theme.PosterRed
import com.biyahe.companion.ui.theme.PosterWhite
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biyahe.companion.BiyaheApp
import com.biyahe.companion.permissions.AppPermission
import com.biyahe.companion.ui.components.DemoBanner
import com.biyahe.companion.ui.hazards.HazardsScreen
import com.biyahe.companion.ui.hazards.ReportHazardScreen
import com.biyahe.companion.ui.history.HistoryScreen
import com.biyahe.companion.ui.home.HomeScreen
import com.biyahe.companion.ui.lock.LockScreen
import com.biyahe.companion.ui.screens.PermissionsScreen
import com.biyahe.companion.ui.recorder.RecorderCheckScreen
import com.biyahe.companion.ui.summary.SummaryScreen
import com.biyahe.companion.ui.trip.TripScreen
import com.biyahe.companion.ui.sensorcheck.SensorCheckScreen

/**
 * App navigation graph.
 *   LOCK, PERMISSIONS -> Section 3, SENSORS -> Section 4, RECORDER -> Section 5, TRIP -> Section 6 (basic)
 *   HOME/TRIP/SUMMARY/HISTORY -> Section 7, HAZARDS + hazard report camera -> Section 8
 */
private const val ROUTE_HAZARD_REPORT = "hazard_report"

@Composable
fun BiyaheNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as BiyaheApp).container
    val session = container.authSession
    val unlocked by session.isUnlocked.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = BottomTab.entries.any { it.route == currentRoute }

    // Section 8: upload anything waiting each time the app is unlocked.
    LaunchedEffect(unlocked) {
        if (unlocked) container.cloudSync.requestSync()
    }

    val account by container.accounts.account.collectAsStateWithLifecycle()

    // The gate: signed out -> log-in screen. Signed in but locked -> fingerprint.
    // Unlocked -> riders go to the app, admins to the separate admin dashboard.
    LaunchedEffect(unlocked, currentRoute, account) {
        if (currentRoute == null) return@LaunchedEffect
        val acct = account
        if (acct == null) {
            // Signed out: the welcome screen (or straight to log-in while Chanelly's tour runs).
            if (currentRoute != Routes.LOGIN && currentRoute != Routes.WELCOME) {
                val to = if (container.guideTour.active) Routes.LOGIN else Routes.WELCOME
                navController.navigate(to) { popUpTo(navController.graph.id) { inclusive = true } }
            }
            return@LaunchedEffect
        }
        val home = when {
            acct.role == Role.ADMIN -> Routes.ADMIN
            AppPermission.anyRequiredMissing(context) -> Routes.PERMISSIONS
            else -> Routes.HOME
        }
        if (unlocked) {
            if (acct.role == Role.USER) container.startStepLogging() // no-op until the activity permission is granted
            if (currentRoute == Routes.LOGIN) {
                navController.navigate(home) { popUpTo(navController.graph.id) { inclusive = true } }
            } else if (currentRoute == Routes.LOCK) {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    navController.navigate(home) { popUpTo(Routes.LOCK) { inclusive = true } }
                }
            }
        } else if (currentRoute != Routes.LOCK) {
            navController.navigate(Routes.LOCK) { launchSingleTop = true }
        }
    }

    val tour = container.guideTour
    val crashState by container.crashMonitor.state.collectAsStateWithLifecycle()
    val nightMode by container.safetyGuards.nightMode.collectAsStateWithLifecycle()

    // Tunnel & night guard: dim the whole app so the screen doesn't glare in the dark.
    DisposableEffect(nightMode) {
        val window = context.findActivity()?.window
        window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = if (nightMode) 0.12f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        onDispose {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
    val safety by container.safetyStore.settings.collectAsStateWithLifecycle()
    var showMorningCheck by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    // Morning sleep check: once a day when Home opens (real mode; in demo mode use "Ask me now").
    LaunchedEffect(unlocked, currentRoute, safety.rulesAccepted) {
        if (!container.isDemo && unlocked && safety.rulesAccepted && currentRoute == Routes.HOME && container.safetyStore.shouldAskNow()) {
            showMorningCheck = true
        }
    }

    // Chanelly: move on when the user taps the spotlighted item, and greet once after demo mode is switched on.
    // Chanelly's actions: she types the logins and logs out by herself.
    tour.actions = { action ->
        when (action) {
            TourAction.FILL_RIDER_LOGIN -> tour.fillRequest = "user" to "user123"
            TourAction.FILL_ADMIN_LOGIN -> tour.fillRequest = "admin" to "admin123"
            TourAction.LOG_OUT -> container.accounts.logOut()
        }
    }
    // Switched into demo mode from the welcome screen: the full tour starts on the log-in screen.
    LaunchedEffect(currentRoute) {
        if (container.isDemo && currentRoute == Routes.LOGIN && DemoMode.consumeTourPending(context)) tour.start()
    }

    CompositionLocalProvider(LocalGuideTour provides tour) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            val showDemoBar = container.isDemo && currentRoute != Routes.LOCK && currentRoute != Routes.LOGIN &&
                currentRoute != Routes.ADMIN && currentRoute != Routes.WELCOME
            val showNightBar = nightMode && currentRoute != Routes.LOCK && currentRoute != Routes.ADMIN
            if (showDemoBar || showNightBar) Column {
            if (showDemoBar) {
                DemoBanner(onAskChanelly = {
                    if (currentRoute != Routes.HOME) navController.openTab(Routes.HOME)
                    tour.startFrom(Routes.HOME)
                })
            }
            if (showNightBar) {
                Text(
                    "TUNNEL / NIGHT MODE · SCREEN DIMMED",
                    color = PosterWhite,
                    modifier = Modifier.fillMaxWidth().background(Ink)
                        .then(if (showDemoBar) Modifier else Modifier.statusBarsPadding())
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
            }
        },
        bottomBar = {
            if (showBottomBar) BiyaheBottomBar(currentRoute, navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            // Signed out: start on the log-in screen (no fingerprint prompt before an account exists).
            startDestination = remember {
                when {
                    container.accounts.account.value != null -> Routes.LOCK
                    DemoMode.consumeSkipWelcome(context) -> Routes.LOGIN
                    else -> Routes.WELCOME
                }
            },
            // The lock screen paints edge-to-edge and handles its own insets.
            // Pad for the bars / camera hole AND mark those insets as used, so screens inside don't pad twice.
            modifier = if (currentRoute == Routes.LOCK) Modifier else Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onContinue = { demoTour ->
                    navController.navigate(Routes.LOGIN)
                    if (demoTour) tour.start()
                })
            }
            composable(Routes.LOGIN) {
                LoginScreen(onBack = { navController.navigate(Routes.WELCOME) { popUpTo(navController.graph.id) { inclusive = true } } },
                    onLoggedIn = { acct ->
                tour.signal("logged_in")
                // A rider whose data lives in another database file: restart into it (no fingerprint
                // right after typing the password). Otherwise just go in.
                val activity = context.findActivity()
                if (acct.role == Role.USER && AccountDb.nameFor(acct.username, container.isDemo) != container.databaseName && activity != null) {
                    DemoMode.restart(activity, unlockAfter = true)
                } else {
                    session.unlock()
                    container.cloudSync.restoreThenSync()
                }
            })
            }
            composable(Routes.ADMIN) {
                if (account?.role == Role.ADMIN) {
                    AdminApp(onLogout = { container.accounts.logOut() })
                } else {
                    Text("Admins only.", Modifier.padding(24.dp))
                }
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSafety = { navController.openTab(Routes.SAFETY) },
                    onReadRules = { showRules = true },
                    onReplayTour = { navController.openTab(Routes.HOME); tour.startFrom(Routes.HOME) },
                    onSensorCheck = { navController.navigate(Routes.SENSORS) },
                    onRecorderTest = { navController.navigate(Routes.RECORDER) },
                    onTuning = { navController.navigate(Routes.TUNING) }
                )
            }
            composable(Routes.TUNING) { TuningScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.LOCK) { LockScreen() }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onContinue = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.PERMISSIONS) { inclusive = true } }
                })
            }
            composable(Routes.SENSORS) {
                SensorCheckScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.RECORDER) {
                RecorderCheckScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onStartTrip = { navController.openTab(Routes.TRIP) },
                    onOpenTrip = { id -> navController.navigate(Routes.summary(id)) },
                    onSeeHistory = { navController.openTab(Routes.HISTORY) },
                    onSensorCheck = { navController.navigate(Routes.SENSORS) },
                    onRecorderTest = { navController.navigate(Routes.RECORDER) },
                    onOpenSafety = { navController.openTab(Routes.SAFETY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.TRIP) {
                TripScreen(onTripSaved = { id ->
                    container.cloudSync.requestSync()
                    navController.navigate(Routes.summary(id))
                })
            }
            composable(
                route = Routes.SUMMARY,
                arguments = listOf(navArgument(Routes.ARG_TRIP_ID) { type = NavType.LongType })
            ) { entry ->
                SummaryScreen(
                    tripId = entry.arguments?.getLong(Routes.ARG_TRIP_ID) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onReportHazard = { navController.openTab(Routes.HAZARDS) }
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenTrip = { id -> navController.navigate(Routes.summary(id)) })
            }
            composable(Routes.HAZARDS) {
                HazardsScreen(
                    onReport = { navController.navigate(ROUTE_HAZARD_REPORT) },
                    onEdit = { id -> navController.navigate("$ROUTE_HAZARD_REPORT?edit=$id") }
                )
            }
            composable(Routes.SAFETY) {
                SafetyScreen(
                    onMorningCheck = { showMorningCheck = true },
                    onDriftDemo = { navController.navigate(Routes.DRIFT_DEMO) },
                    onReadRules = { showRules = true }
                )
            }
            composable(Routes.DRIFT_DEMO) {
                DriftDemoScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "$ROUTE_HAZARD_REPORT?edit={edit}",
                arguments = listOf(navArgument("edit") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val editId = entry.arguments?.getLong("edit")?.takeIf { it > 0 }
                ReportHazardScreen(onClose = { navController.popBackStack() }, editId = editId)
            }
        }
    }

    if (showMorningCheck) MorningCheckDialog(container.safetyStore, onDone = { showMorningCheck = false })
    // "Read me first": before anything else the first time (after the fingerprint), and on request.
    val firstTimeRules = unlocked && !safety.rulesAccepted && account?.role == Role.USER && currentRoute != null &&
        currentRoute !in listOf(Routes.LOCK, Routes.LOGIN, Routes.ADMIN, Routes.WELCOME)
    if (firstTimeRules || showRules) {
        ReadMeFirstScreen(firstTime = firstTimeRules) {
            container.safetyStore.update { it.copy(rulesAccepted = true) }
            showRules = false
        }
    }
    // Chanelly is drawn above the rules screen so she can explain it too.
    val tourScreen = if (firstTimeRules || showRules) RULES_SCREEN else currentRoute
    LaunchedEffect(tourScreen) { tour.onScreenChanged(tourScreen) }
    if (container.isDemo) ChanellyOverlay(tour, tourScreen)
    // Eye watch: front camera during a driving trip (paused while the hazard camera is open) + WAKE UP screen.
    EyeWatchHost(paused = currentRoute?.startsWith(ROUTE_HAZARD_REPORT) == true)
    CrashOverlay(crashState, container.crashMonitor, safety.contacts, container.isDemo)
    }
    }
}

@Composable
private fun BiyaheBottomBar(currentRoute: String?, navController: NavController) {
    // Ink bar with a thick red line on top, like the poster footer.
    NavigationBar(
        modifier = Modifier.drawBehind { drawRect(PosterRed, size = Size(size.width, 4.dp.toPx())) },
        containerColor = Ink,
        contentColor = PosterWhite
    ) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { navController.openTab(tab.route) },
                modifier = Modifier.tourTarget(TourTargets.tab(tab.route)),
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink,
                    selectedTextColor = PosterWhite,
                    indicatorColor = PosterWhite,
                    unselectedIconColor = Color(0xFFBBBBBB),
                    unselectedTextColor = Color(0xFFBBBBBB)
                )
            )
        }
    }
}

/** Switch bottom-bar tab (same behaviour as tapping it). */
private fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
