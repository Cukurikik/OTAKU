package com.example.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavigationResolverTest {

    private lateinit var context: Context
    private lateinit var resolver: AppNavigationResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        resolver = AppNavigationResolver(context)
    }

    @Test
    fun testDefaultHandheldMode_resolvesFalse() {
        val isTv = resolver.isTvMode()
        assertFalse(isTv)
    }

    @Test
    fun testTelevisionFeatureWithoutTouchscreen_resolvesTrue() {
        val shadowPm = Shadows.shadowOf(context.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        shadowPm.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)

        val isTv = resolver.isTvMode()
        assertTrue(isTv)
        assertTrue(resolver.resolveNavigationGraph())
    }
}
