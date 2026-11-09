package com.biyahe.companion

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Demo mode keeps sample data completely apart from the real data:
 *
 *   OFF (default) -> biyahe.db, your real trips and hazards, cloud sync works as usual
 *   ON            -> biyahe_demo.db, filled with sample trips and hazards, cloud sync is OFF
 *
 * The choice is stored in SharedPreferences (not Room) because it decides WHICH Room file opens.
 * Switching restarts the app so every screen, ViewModel and service picks up the other database.
 */
object DemoMode {
    private const val PREFS = "biyahe_mode"
    private const val KEY_DEMO = "demo_mode"
    private const val KEY_TOUR_PENDING = "tour_pending"
    private const val KEY_UNLOCK_ONCE = "unlock_once"
    private const val KEY_SKIP_WELCOME = "skip_welcome"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DEMO, false)

    /** True once after demo mode was switched on; reading it clears it. */
    fun consumeTourPending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_TOUR_PENDING, false)
        if (pending) prefs.edit().putBoolean(KEY_TOUR_PENDING, false).apply()
        return pending
    }

    /** Saves the choice and restarts the app into it. Do not call while a trip is recording. */
    fun switchAndRestart(activity: Activity, on: Boolean) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEMO, on)
            .putBoolean(KEY_TOUR_PENDING, on) // demo: Chanelly starts the full tour on the log-in screen
            .putBoolean(KEY_SKIP_WELCOME, true) // the choice was just made: go straight to log-in
            .commit() // commit, not apply: the process ends right after
        restart(activity, unlockAfter = false)
    }

    /**
     * Restarts the app (needed to open another database file). [unlockAfter] skips the fingerprint
     * once, for a restart that comes straight after typing the password.
     */
    fun restart(activity: Activity, unlockAfter: Boolean) {
        if (unlockAfter) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_UNLOCK_ONCE, true).commit()
        }
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        activity.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    /** True once right after switching mode from the welcome screen; reading it clears it. */
    fun consumeSkipWelcome(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getBoolean(KEY_SKIP_WELCOME, false)
        if (v) prefs.edit().putBoolean(KEY_SKIP_WELCOME, false).apply()
        return v
    }

    /** True once after [restart] with unlockAfter; reading it clears it. */
    fun consumeUnlockOnce(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getBoolean(KEY_UNLOCK_ONCE, false)
        if (v) prefs.edit().putBoolean(KEY_UNLOCK_ONCE, false).apply()
        return v
    }
}
