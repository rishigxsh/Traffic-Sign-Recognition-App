package com.example.tsrapp

import android.app.Application
import com.example.tsrapp.util.SettingsManager

class TSRApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply persisted theme at startup
        val mode = SettingsManager.getThemeMode(this)
        SettingsManager.applyTheme(mode)
    }
}

