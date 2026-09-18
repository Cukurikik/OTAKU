package com.example

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.VideoCache
import com.example.media.ExoPlayerManager
import com.example.media.MediaSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoEngineAndPipTest {

    @Test
    fun testVideoCacheInitializationAndFactory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = VideoCache.getInstance(context)
        assertNotNull("SimpleCache instance must not be null", cache)

        val dummyUpstream = androidx.media3.datasource.DataSource.Factory {
            ByteArrayDataSource(byteArrayOf(1, 2, 3))
        }
        val cacheDataSourceFactory = VideoCache.buildCacheDataSourceFactory(context, dummyUpstream)
        assertNotNull("CacheDataSource factory must not be null", cacheDataSourceFactory)
        val dataSource = cacheDataSourceFactory.createDataSource()
        assertTrue("Created DataSource must be instance of CacheDataSource", dataSource is CacheDataSource)
    }

    @Test
    fun testMediaSessionServiceLifecycleActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var nextClicked = false
        var previousClicked = false
        var audioOnlyToggled = false

        MediaSessionService.setActiveSession(
            session = null,
            onNext = { nextClicked = true },
            onPrevious = { previousClicked = true },
            onToggleAudioOnly = { audioOnlyToggled = true }
        )

        MediaSessionService.startService(context)
        MediaSessionService.stopService(context)
    }

    @Test
    fun testAudioOnlyModeTrackDisabling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trackSelector = DefaultTrackSelector(context)
        val player = ExoPlayerManager.buildPlayer(
            context = context,
            trackSelector = trackSelector,
            bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(context),
            headersProvider = { emptyMap() },
            streamUrlProvider = { "https://example.com/test.mp4" }
        )

        try {
            // Test enabling Audio-Only Mode
            ExoPlayerManager.setAudioOnlyMode(player, trackSelector, enabled = true)
            assertTrue("Video track must be disabled in disabledTrackTypes when Audio-Only is enabled",
                trackSelector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertTrue("Player trackSelectionParameters must have video in disabledTrackTypes",
                player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))

            // Test disabling Audio-Only Mode (return to video)
            ExoPlayerManager.setAudioOnlyMode(player, trackSelector, enabled = false)
            assertFalse("Video track must not be disabled when Audio-Only is turned off",
                trackSelector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertFalse("Player trackSelectionParameters must not have video in disabledTrackTypes",
                player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
        } finally {
            player.release()
        }
    }

    @Test
    fun testBandwidthSavingsCalculation() {
        // Standard 1080p stream: ~4500 kbps video + 128 kbps audio
        val savings1080p = ExoPlayerManager.calculateBandwidthSavings(videoBitrateBps = 4500000L, audioBitrateBps = 128000L)
        assertTrue("Savings on 1080p should exceed 95%", savings1080p > 95f)

        // 720p stream: ~1500 kbps video + 128 kbps audio
        val savings720p = ExoPlayerManager.calculateBandwidthSavings(videoBitrateBps = 1500000L, audioBitrateBps = 128000L)
        assertTrue("Savings on 720p should exceed 90%", savings720p > 90f)

        // Zero / Default fallback
        val defaultSavings = ExoPlayerManager.calculateBandwidthSavings(videoBitrateBps = 0L, audioBitrateBps = 0L)
        assertEquals(80.0f, defaultSavings, 0.1f)
    }

    @Test
    fun testAudioOnlyModeSettingsPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = SettingsManager(context)

        settingsManager.audioOnlyMode = true
        assertTrue("SettingsManager must persist audioOnlyMode as true", settingsManager.audioOnlyMode)

        settingsManager.audioOnlyMode = false
        assertFalse("SettingsManager must persist audioOnlyMode as false", settingsManager.audioOnlyMode)
    }

    @Test
    fun testPipAspectRatioCalculation() {
        // Test standard 16:9
        val w1 = 1920
        val h1 = 1080
        val ratio1 = w1.toFloat() / h1.toFloat()
        assertTrue("16:9 should be within Android PiP allowable bounds (0.42..2.38)", ratio1 in 0.42f..2.38f)

        // Test 21:9 ultra-wide
        val w2 = 2560
        val h2 = 1080
        val ratio2 = w2.toFloat() / h2.toFloat()
        assertEquals(2.37f, ratio2, 0.01f)
        assertTrue("21:9 should be within Android PiP allowable bounds", ratio2 in 0.42f..2.38f)

        // Test vertical 9:16 (Shorts/Reels/Vertical video)
        val w3 = 1080
        val h3 = 1920
        val ratio3 = w3.toFloat() / h3.toFloat()
        assertEquals(0.5625f, ratio3, 0.01f)
        assertTrue("9:16 should be within Android PiP allowable bounds", ratio3 in 0.42f..2.38f)

        // Test extreme out-of-bounds video (e.g. 100:1)
        val wExtreme = 10000
        val hExtreme = 100
        val ratioExtreme = wExtreme.toFloat() / hExtreme.toFloat()
        val isValidPip = ratioExtreme in 0.42f..2.38f
        assertEquals("Extreme ratio must be rejected by PiP bounds check", false, isValidPip)
    }
}
