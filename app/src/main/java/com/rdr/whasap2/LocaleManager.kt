package com.rdr.whasap2

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(NotificationSettings.KEY_APP_LANGUAGE, "es") ?: "es"
    }

    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(NotificationSettings.KEY_APP_LANGUAGE, languageCode).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }

    fun applySavedLocale(context: Context) {
        val languageCode = getSavedLanguage(context)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }
}
