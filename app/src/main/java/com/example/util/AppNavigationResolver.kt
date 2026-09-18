package com.example.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Resolves whether the current application environment is running in Android TV mode (Leanback)
 * or standard handheld / mobile mode.
 */
class AppNavigationResolver(private val context: Context) {

    /**
     * Returns true if the device is identified as an Android TV device based on
     * UiModeManager and Leanback system feature flags.
     */
    fun isTvMode(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanbackFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasNoTouchscreen = !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return isTelevision || (hasLeanbackFeature && hasNoTouchscreen)
    }

    /**
     * Resolves the navigation graph mode (true = TV Leanback NavHost, false = Mobile Scaffold NavHost).
     */
    fun resolveNavigationGraph(): Boolean = isTvMode()
}
