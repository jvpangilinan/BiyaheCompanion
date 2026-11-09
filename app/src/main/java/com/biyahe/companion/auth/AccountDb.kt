package com.biyahe.companion.auth

import android.content.Context

/**
 * Each rider account gets its own database file on the phone, so two people sharing a phone never
 * see each other's trips:
 *
 *   demo mode           biyahe_demo.db
 *   rider "juan"        biyahe_u_juan.db
 *   admin / signed out  biyahe.db        (the admin dashboard reads the cloud, not this file)
 *
 * The phone's trips from before log-in existed (biyahe.db) are handed to the FIRST rider account
 * that logs in, so nothing recorded earlier is lost.
 */
object AccountDb {
    private const val LEGACY = "biyahe.db"
    private const val KEY_CLAIMED = "legacy_db_claimed"

    fun nameFor(username: String?, isDemo: Boolean): String = when {
        isDemo -> "biyahe_demo.db"
        username == null || username == AccountService.ADMIN_USERNAME -> LEGACY
        else -> "biyahe_u_$username.db"
    }

    /** Picks the file for whoever is signed in, moving the old shared file over first if due. Call before Room opens. */
    fun prepare(context: Context, isDemo: Boolean): String {
        val name = nameFor(AccountService.savedUsername(context, isDemo), isDemo)
        if (!isDemo && name != LEGACY) claimLegacy(context, name)
        return name
    }

    private fun claimLegacy(context: Context, target: String) {
        val prefs = context.getSharedPreferences(AccountService.PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_CLAIMED, false)) return
        val legacy = context.getDatabasePath(LEGACY)
        val dest = context.getDatabasePath(target)
        if (legacy.exists() && !dest.exists()) {
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                val from = java.io.File(legacy.path + suffix)
                if (from.exists()) from.renameTo(java.io.File(dest.path + suffix))
            }
        }
        prefs.edit().putBoolean(KEY_CLAIMED, true).apply()
    }
}
