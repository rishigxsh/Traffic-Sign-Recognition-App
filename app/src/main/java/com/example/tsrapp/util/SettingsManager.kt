package com.example.tsrapp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object SettingsManager {

    private const val PREFS_NAME = "tsr_settings"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TTS_ENABLED = "tts_enabled"
    private const val KEY_SHOW_BOXES = "show_boxes"
    private const val KEY_SHOW_LABELS = "show_labels"
    private const val KEY_SHOW_CONFIDENCE = "show_confidence"
    private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
    private const val KEY_REGION = "region"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): String =
        prefs(context).getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun setThemeMode(context: Context, mode: String) {
        prefs(context).edit {
            putString(KEY_THEME_MODE, mode)
        }
    }

    fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun isTtsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TTS_ENABLED, true)

    fun setTtsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_TTS_ENABLED, enabled)
        }
    }

    fun isShowBoxes(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_BOXES, true)

    fun setShowBoxes(context: Context, show: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_SHOW_BOXES, show)
        }
    }

    fun isShowLabels(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_LABELS, true)

    fun setShowLabels(context: Context, show: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_SHOW_LABELS, show)
        }
    }

    fun isShowConfidence(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CONFIDENCE, true)

    fun setShowConfidence(context: Context, show: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_SHOW_CONFIDENCE, show)
        }
    }

    fun getConfidenceThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_CONFIDENCE_THRESHOLD, 0.5f)

    fun setConfidenceThreshold(context: Context, threshold: Float) {
        prefs(context).edit {
            putFloat(KEY_CONFIDENCE_THRESHOLD, threshold.coerceIn(0f, 1f))
        }
    }

    fun getRegion(context: Context): String =
        prefs(context).getString(KEY_REGION, "Automatic") ?: "Automatic"

    fun setRegion(context: Context, region: String) {
        prefs(context).edit {
            putString(KEY_REGION, region)
        }
    }
}

