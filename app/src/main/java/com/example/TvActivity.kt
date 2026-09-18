package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import com.example.data.SettingsManager
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.tv.TvAppNavigation

/**
 * Dedicated TV Activity for Android TV / Leanback launcher sessions.
 * Completely isolates D-Pad focus controller and TV Compose NavHost from Mobile Scaffold.
 */
class TvActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OtakuHubApp

        setContent {
            val settingsManager = remember { SettingsManager(app) }
            val dynamicColor = settingsManager.isDynamicColor
            val darkModeSetting = settingsManager.darkMode

            val isDarkTheme = when (darkModeSetting) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
                // TV-exclusive Leanback Navigation Host without any mobile Scaffold, NavigationRail or BottomBar
                TvAppNavigation(app = app)
            }
        }
    }
}
