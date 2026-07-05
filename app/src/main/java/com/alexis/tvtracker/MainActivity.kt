package com.alexis.tvtracker

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.alexis.tvtracker.data.ThemeMode
import com.alexis.tvtracker.ui.TvTrackerApp

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        requestNotificationPermission()
        val container = (application as TvTrackerApplication).container
        setContent {
            val themeMode by container.uiSettingsRepository.themeMode.collectAsState()
            val darkMode = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            MaterialTheme(
                colorScheme = if (darkMode) {
                    darkColorScheme(
                        primary = Color(0xFF3BCBFF),
                        secondary = Color(0xFF8BD8FF),
                        tertiary = Color(0xFFB4E7FF),
                        surface = Color(0xFF0D141B),
                        background = Color(0xFF091018),
                        surfaceVariant = Color(0xFF172634),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF005EAD),
                        secondary = Color(0xFF007FA8),
                        tertiary = Color(0xFF003F7A),
                        surface = Color(0xFFF4FAFF),
                        background = Color(0xFFE9F5FF),
                        surfaceVariant = Color(0xFFC7E4F7),
                        primaryContainer = Color(0xFFA9D9FA),
                        secondaryContainer = Color(0xFFB6E8F7),
                    )
                },
            ) {
                TvTrackerApp(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as TvTrackerApplication).prefetchSearchDiscovery()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
