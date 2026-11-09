package com.biyahe.companion.tour

import com.biyahe.companion.navigation.Routes
import com.biyahe.companion.ui.tour.GuideTour
import com.biyahe.companion.ui.tour.RULES_SCREEN
import com.biyahe.companion.ui.tour.TourAction
import com.biyahe.companion.ui.tour.chanellyTour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTourTest {

    private fun tour(): Pair<GuideTour, MutableList<TourAction>> {
        val done = mutableListOf<TourAction>()
        val t = GuideTour()
        t.actions = { done += it }
        t.onScreenChanged(Routes.LOGIN)
        t.start()
        return t to done
    }

    @Test fun chanellyTypesTheRiderLogin() {
        val (t, done) = tour()
        t.next()
        assertEquals(listOf(TourAction.FILL_RIDER_LOGIN), done)
        assertEquals(Routes.LOGIN, t.step?.screen)
    }

    @Test fun rulesAreExplainedWhenTheyShow() {
        val (t, _) = tour()
        t.next(); t.signal("logged_in")
        t.onScreenChanged(RULES_SCREEN)
        assertEquals(RULES_SCREEN, t.step?.screen)
    }

    @Test fun rulesAndPermissionsAreSkippedWhenNotNeeded() {
        val (t, _) = tour()
        t.next(); t.signal("logged_in")
        t.onScreenChanged(Routes.HOME)          // rules already read, permissions already given
        assertEquals(Routes.HOME, t.step?.screen)
        assertFalse(t.step!!.optional)
    }

    @Test fun riderPartEndsByLoggingOutThenTheAdminLogsIn() {
        val (t, done) = tour()
        val logoutIndex = chanellyTour.indexOfFirst { it.onNext == TourAction.LOG_OUT }
        while (t.stepIndex < logoutIndex) {
            val s = t.step!!
            when {
                s.advanceOnSignal != null -> t.signal(s.advanceOnSignal!!)
                s.advanceOnRoute != null -> t.onScreenChanged(s.advanceOnRoute)
                s.optional -> t.onScreenChanged(Routes.HOME)
                else -> t.next()
            }
        }
        t.next()
        assertTrue(TourAction.LOG_OUT in done)
        assertEquals(TourAction.FILL_ADMIN_LOGIN, done.last())
        assertEquals(Routes.LOGIN, t.step?.screen)
    }

    @Test fun theTourEndsOnTheAdminDashboard() {
        assertEquals(Routes.ADMIN, chanellyTour.last().screen)
        assertTrue(chanellyTour.none { it.text.length > 200 }) // short enough to read aloud and follow
    }
}
