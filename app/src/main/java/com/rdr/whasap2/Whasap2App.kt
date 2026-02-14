package com.rdr.whasap2

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class Whasap2App : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Required on pre-L devices so classes in secondary dex files are available at startup.
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        LocaleManager.applySavedLocale(this)
    }
}
