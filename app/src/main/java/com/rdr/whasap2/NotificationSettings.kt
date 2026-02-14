package com.rdr.whasap2

import android.content.Context

object NotificationSettings {
    const val PREFS_NAME = "whasap_prefs"
    const val KEY_INTRO_SEEN = "INTRO_SEEN"
    const val KEY_APP_LANGUAGE = "APP_LANGUAGE"
    const val KEY_NOTIFICATIONS_ENABLED = "NOTIFICATIONS_ENABLED"
    const val KEY_BASELINE_ON_NEXT_START = "BASELINE_ON_NEXT_START"
    private const val LEGACY_KEY_NOTIFICATIONS_MUTED = "NOTIFICATIONS_MUTED"

    fun areNotificationsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_NOTIFICATIONS_ENABLED)) {
            return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }
        // Backward compatibility: old builds only had a mute flag.
        return !prefs.getBoolean(LEGACY_KEY_NOTIFICATIONS_MUTED, false)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            // Keep legacy key in sync for older code paths.
            .putBoolean(LEGACY_KEY_NOTIFICATIONS_MUTED, !enabled)
            .apply()
    }

    fun markBaselineOnNextStart(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BASELINE_ON_NEXT_START, true).apply()
    }
}
