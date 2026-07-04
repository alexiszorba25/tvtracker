package com.alexis.tvtracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode {
    System,
    Light,
    Dark,
}

class UiSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(currentThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun currentThemeMode(): ThemeMode {
        return runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.System.name).orEmpty())
        }.getOrDefault(ThemeMode.System)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
