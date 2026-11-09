package com.biyahe.companion.ui.tour

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.biyahe.companion.navigation.Routes

/** Things Chanelly does by herself, so nobody has to type or know what to do. */
enum class TourAction { FILL_RIDER_LOGIN, FILL_ADMIN_LOGIN, LOG_OUT }

/**
 * One stop on Chanelly's tour.
 *
 * @param screen          route this step belongs to ("rules" = the safety-rules screen)
 * @param target          [tourTarget] id to spotlight, or null for a plain message
 * @param advanceOnRoute  moves on when this route opens (the user tapped the spotlight)
 * @param advanceOnSignal moves on when the app sends this signal (e.g. "logged_in", "admin_tab_2")
 * @param optional        skipped automatically when its screen doesn't come up (rules already read,
 *                        permissions already given)
 * @param blocking        false = just a speech bubble, the screen stays fully usable
 * @param onEnter/onNext  what Chanelly does herself when the step starts / when Next is tapped
 */
data class TourStep(
    val screen: String,
    val target: String?,
    val text: String,
    val advanceOnRoute: String? = null,
    val advanceOnSignal: String? = null,
    val optional: Boolean = false,
    val blocking: Boolean = true,
    val onEnter: TourAction? = null,
    val onNext: TourAction? = null
) {
    /** True when the user moves on by doing something, not by tapping Next. */
    val waitsForUser: Boolean get() = advanceOnRoute != null || advanceOnSignal != null || optional
}

/** Ids used with Modifier.tourTarget(...) on the screens. */
object TourTargets {
    const val LOGIN_BUTTON = "login_button"
    const val RULES_ACCEPT = "rules_accept"
    const val HOME_SAFETY = "home_safety"
    const val HOME_ACCOUNT = "home_account"
    const val HOME_STATS = "home_stats"
    const val HOME_FIRST_TRIP = "home_first_trip"
    const val HOME_SENSOR_CHECK = "home_sensor_check"
    const val SUMMARY_SCORE = "summary_score"
    const val SUMMARY_BREAKDOWN = "summary_breakdown"
    const val SUMMARY_ROUTE = "summary_route"
    const val SUMMARY_DETAILS = "summary_details"
    const val SUMMARY_BACK = "summary_back"
    const val TRIP_START = "trip_start"
    const val HAZARDS_REPORT = "hazards_report"
    const val SAFETY_SPEED = "safety_speed"
    const val SAFETY_SLEEP = "safety_sleep"
    const val SAFETY_CRASH = "safety_crash"
    const val SAFETY_DRIFT = "safety_drift"
    const val SAFETY_MORE = "safety_more"
    const val ADMIN_KPIS = "admin_kpis"
    const val ADMIN_SEND = "admin_send"
    const val ADMIN_SUMMARY = "admin_summary"
    fun tab(route: String) = "tab_$route"
    fun adminTab(i: Int) = "admin_tab_$i"
}

/** The rules screen is an overlay, not a route; the tour calls it this. */
const val RULES_SCREEN = "rules"

/**
 * The whole demo, start to finish: rider log-in, the rider app, then the admin side, then the big
 * picture. Short sentences for children, adults and older people alike; Chanelly reads every line aloud.
 */
val chanellyTour: List<TourStep> = listOf(
    // ---- Log in as a rider ----
    TourStep(Routes.LOGIN, null,
        "Hi! I'm Chanelly, your travel buddy. I'll show you everything, one step at a time. Just tap where I point!"),
    TourStep(Routes.LOGIN, TourTargets.LOGIN_BUTTON,
        "First, let's log in as a rider. I already typed the name and password for you. Tap the red Log in button!",
        advanceOnSignal = "logged_in", onEnter = TourAction.FILL_RIDER_LOGIN),
    TourStep(RULES_SCREEN, TourTargets.RULES_ACCEPT,
        "These are the safety rules. The most important one: never use your phone while driving. Tick the box, then tap the button.",
        optional = true),
    TourStep(Routes.PERMISSIONS, null,
        "Your phone will ask if I may use its sensors. Tap Allow each time, then Continue.",
        advanceOnRoute = Routes.HOME, optional = true, blocking = false),

    // ---- Safety first ----
    TourStep(Routes.HOME, null,
        "Welcome home! Everything starts here."),
    TourStep(Routes.HOME, TourTargets.HOME_SAFETY,
        "These are your safety helpers. Green means they are on and watching over you."),
    TourStep(Routes.HOME, TourTargets.tab(Routes.SAFETY),
        "Let's look closer. Tap Safety.", advanceOnRoute = Routes.SAFETY),
    TourStep(Routes.SAFETY, TourTargets.SAFETY_SPEED,
        "Speed limit. If you go too fast, I say it out loud, so your eyes stay on the road."),
    TourStep(Routes.SAFETY, TourTargets.SAFETY_SLEEP,
        "Sleep check. Every morning I ask: did you sleep well? If you're tired, I remind you to be careful."),
    TourStep(Routes.SAFETY, TourTargets.SAFETY_DRIFT,
        "Lane weaving. Sleepy drivers drift from side to side. I notice it, and ask if you need a rest."),
    TourStep(Routes.SAFETY, TourTargets.SAFETY_CRASH,
        "Crash alert. If I feel a crash, I count down. If you don't answer, I tell your family where you are."),
    TourStep(Routes.SAFETY, TourTargets.SAFETY_MORE,
        "And more helpers: tunnels, rain, potholes ahead, rest breaks, loud noise, and an SOS button."),

    // ---- Comfort ----
    TourStep(Routes.SAFETY, TourTargets.tab(Routes.HOME),
        "Now let's look at your trips. Tap Home.", advanceOnRoute = Routes.HOME),
    TourStep(Routes.HOME, TourTargets.HOME_FIRST_TRIP,
        "Tap this trip to see how the ride felt.", advanceOnRoute = Routes.SUMMARY),
    TourStep(Routes.SUMMARY, TourTargets.SUMMARY_SCORE,
        "This is the Comfort Score. Green is smooth. Yellow is okay. Red is bumpy."),
    TourStep(Routes.SUMMARY, TourTargets.SUMMARY_BREAKDOWN,
        "And here is why. Bumps, sharp turns and noise each take away points."),
    TourStep(Routes.SUMMARY, TourTargets.SUMMARY_ROUTE,
        "The map shows the road you took. Red parts were rough. Red dots are big bumps."),
    TourStep(Routes.SUMMARY, TourTargets.SUMMARY_BACK,
        "Tap here to go back.", advanceOnRoute = Routes.HOME),
    TourStep(Routes.HOME, TourTargets.tab(Routes.TRIP),
        "Ready to go? Tap Trip.", advanceOnRoute = Routes.TRIP),
    TourStep(Routes.TRIP, TourTargets.TRIP_START,
        "Put your phone in a holder first. Then tap Start Trip before you move. I'll ask if you're driving or riding."),
    TourStep(Routes.TRIP, TourTargets.tab(Routes.HAZARDS),
        "See a pothole or a flood? Tap Hazards.", advanceOnRoute = Routes.HAZARDS),
    TourStep(Routes.HAZARDS, TourTargets.HAZARDS_REPORT,
        "Take a photo here. I add the place and the time for you."),
    TourStep(Routes.HAZARDS, TourTargets.tab(Routes.HOME),
        "Let's go back home.", advanceOnRoute = Routes.HOME),

    // ---- Switch to the admin ----
    TourStep(Routes.HOME, TourTargets.HOME_ACCOUNT,
        "That's the rider side! Now let's see what the admin sees. Tap Next, and I'll log you out.",
        onNext = TourAction.LOG_OUT),
    TourStep(Routes.LOGIN, TourTargets.LOGIN_BUTTON,
        "The admin looks after reports from everyone. I typed the admin login for you. Tap Log in!",
        advanceOnSignal = "logged_in", onEnter = TourAction.FILL_ADMIN_LOGIN),
    TourStep(Routes.ADMIN, TourTargets.ADMIN_KPIS,
        "This is the admin dashboard. It counts all the riders, trips and reports."),
    TourStep(Routes.ADMIN, TourTargets.adminTab(1),
        "Tap Riders to see each person.", advanceOnSignal = "admin_tab_1"),
    TourStep(Routes.ADMIN, null,
        "Every rider is here, with their trips and how safely they drive."),
    TourStep(Routes.ADMIN, TourTargets.adminTab(2),
        "Now tap Hazards.", advanceOnSignal = "admin_tab_2"),
    TourStep(Routes.ADMIN, TourTargets.ADMIN_SEND,
        "Every pothole and flood photo from the riders arrives here. One tap sends it to the right office, like DPWH or MMDA."),
    TourStep(Routes.ADMIN, TourTargets.adminTab(3),
        "Last one. Tap Safety.", advanceOnSignal = "admin_tab_3"),
    TourStep(Routes.ADMIN, TourTargets.ADMIN_SUMMARY,
        "Speeding totals can go to LTO and the Highway Patrol. No names are shared, to keep everyone private."),

    // ---- The big picture ----
    TourStep(Routes.ADMIN, null,
        "So here's the whole idea: riders stay safe and report road problems. The admin sends them to the government. " +
            "Together, we make our roads better. Salamat, and safe travels!")
)

/** Tour state. Lives in AppContainer so it survives screen changes; plain Compose state inside. */
class GuideTour(private val steps: List<TourStep> = chanellyTour) {

    var stepIndex by mutableStateOf(-1)
        private set

    /** Read every line out loud (on by default; the speaker button in the bubble mutes it). */
    var voiceOn by mutableStateOf(true)

    /** Username + password Chanelly is typing into the log-in screen (the screen clears it). */
    var fillRequest by mutableStateOf<Pair<String, String>?>(null)

    /** Set by the app so Chanelly's actions (type a login, log out) actually happen. */
    var actions: (TourAction) -> Unit = {}

    val step: TourStep? get() = steps.getOrNull(stepIndex)
    val stepCount: Int get() = steps.size
    val isLast: Boolean get() = stepIndex == steps.lastIndex
    val active: Boolean get() = stepIndex >= 0

    /** Where each target currently sits on screen (root coordinates). */
    internal val targets = mutableStateMapOf<String, Rect>()

    private var screenNow: String? = null

    /** From the very beginning (the log-in screen). */
    fun start() = goTo(0)

    /** From the first step on [screen], e.g. "Ask Chanelly" while already logged in. */
    fun startFrom(screen: String) = goTo(steps.indexOfFirst { it.screen == screen }.coerceAtLeast(0))

    fun end() { stepIndex = -1 }

    /** Next button: Chanelly's own action first, then the following step. */
    fun next() {
        val s = step ?: return
        s.onNext?.let { actions(it) }
        advance()
    }

    /** Back button (only between plain "Next" steps). */
    fun back() {
        var i = stepIndex - 1
        while (i >= 0 && steps[i].waitsForUser) i--
        if (i >= 0) stepIndex = i
    }

    val canGoBack: Boolean get() = (stepIndex - 1 downTo 0).any { !steps[it].waitsForUser }

    /** Called whenever the visible screen changes. */
    fun onScreenChanged(screen: String?) {
        screenNow = screen
        val s = step ?: return
        if (s.advanceOnRoute != null && s.advanceOnRoute == screen) advance() else skipOptional()
    }

    /** Called by screens when something happens ("logged_in", "admin_tab_2", ...). */
    fun signal(name: String) {
        if (step?.advanceOnSignal == name) advance()
    }

    private fun advance() = goTo(stepIndex + 1)

    private fun goTo(i: Int) {
        if (i !in steps.indices) { end(); return }
        stepIndex = i
        // Optional steps are skipped from onScreenChanged, once the next screen has really appeared.
        steps[stepIndex].onEnter?.let { actions(it) }
    }

    /** Skips optional steps whose screen isn't showing. Returns true if it moved. */
    private fun skipOptional(): Boolean {
        val s = step ?: return false
        if (!s.optional || s.screen == screenNow) return false
        // An optional step whose screen isn't up: if a later step's screen is, jump there.
        var i = stepIndex
        while (i < steps.size && steps[i].optional && steps[i].screen != screenNow) i++
        if (i == stepIndex) return false
        goTo(i)
        return true
    }
}

/** Null outside the app's nav host (previews), which turns tourTarget into a no-op. */
val LocalGuideTour = staticCompositionLocalOf<GuideTour?> { null }

/**
 * Marks this element as a tour stop. Its position is reported to the tour, and when it becomes
 * the current stop it is scrolled into view.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(id: String): Modifier = composed {
    val tour = LocalGuideTour.current ?: return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    val isCurrent = tour.step?.target == id

    LaunchedEffect(isCurrent) { if (isCurrent) requester.bringIntoView() }
    DisposableEffect(id) { onDispose { tour.targets.remove(id) } }

    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coords ->
            val r = coords.boundsInRoot()
            if (tour.targets[id] != r) tour.targets[id] = r
        }
}
