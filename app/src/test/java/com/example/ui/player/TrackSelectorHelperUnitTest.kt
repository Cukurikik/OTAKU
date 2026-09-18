package com.example.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(UnstableApi::class)
class TrackSelectorHelperUnitTest {

    @Test
    fun testSelectVideoQualityMaxDimensions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val trackSelector = DefaultTrackSelector(context)

        // Select 720p HD (1280x720)
        TrackSelectorHelper.selectVideoQuality(trackSelector, 1280, 720)
        val params720 = trackSelector.parameters
        assertEquals(1280, params720.maxVideoWidth)
        assertEquals(720, params720.maxVideoHeight)
        assertFalse(params720.forceHighestSupportedBitrate)

        // Select 1080p Full HD (1920x1080)
        TrackSelectorHelper.selectVideoQuality(trackSelector, 1920, 1080)
        val params1080 = trackSelector.parameters
        assertEquals(1920, params1080.maxVideoWidth)
        assertEquals(1080, params1080.maxVideoHeight)

        // Select 360p (640x360)
        TrackSelectorHelper.selectVideoQuality(trackSelector, 640, 360)
        val params360 = trackSelector.parameters
        assertEquals(640, params360.maxVideoWidth)
        assertEquals(360, params360.maxVideoHeight)
    }

    @Test
    fun testSelectAutoVideoQualityClearsOverrides() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val trackSelector = DefaultTrackSelector(context)

        // Set an override first
        TrackSelectorHelper.selectVideoQuality(trackSelector, 640, 360)
        TrackSelectorHelper.selectAutoVideoQuality(trackSelector)

        val params = trackSelector.parameters
        val overrides = params.overrides
        assertTrue(overrides.isEmpty())
    }

    @Test
    fun testDisableSubtitles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val trackSelector = DefaultTrackSelector(context)

        TrackSelectorHelper.disableSubtitles(trackSelector)
        val params = trackSelector.parameters
        assertTrue(params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }
}
