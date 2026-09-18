package com.example

import android.content.Context
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AdaptiveBitrateLoadControlTest {

    @Test
    fun testDefaultLoadControlCustomDurations() {
        val minBufferMs = 15000
        val maxBufferMs = 50000
        val bufferForPlaybackMs = 2500
        val bufferForPlaybackAfterRebufferMs = 5000

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        assertNotNull("LoadControl should be created successfully", loadControl)
        assertNotNull("LoadControl allocator should be initialized", loadControl.allocator)
    }

    @Test
    fun testBandwidthMeterAndAdaptiveTrackSelectorInstantiation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        assertNotNull("BandwidthMeter should be instantiated", bandwidthMeter)

        val trackSelector = DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory()
        )
        assertNotNull("DefaultTrackSelector with AdaptiveTrackSelection.Factory should be instantiated", trackSelector)
    }

    @Test
    fun testExoPlayerBuilderWithAbrConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())

        val player = ExoPlayer.Builder(context)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()

        try {
            assertNotNull("ExoPlayer should build with custom ABR components", player)
        } finally {
            player.release()
        }
    }
}
