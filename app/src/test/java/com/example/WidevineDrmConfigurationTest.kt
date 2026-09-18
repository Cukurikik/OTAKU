package com.example

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WidevineDrmConfigurationTest {

    @Test
    fun testVideoStreamInfoDrmProperties() {
        val streamInfo = VideoStreamInfo(
            episodeId = "ep-101",
            title = "Premium Drama Episode 1",
            embedUrl = "https://example.com/manifest.mpd",
            drmLicenseUrl = "https://license.example.com/widevine",
            drmHeaders = mapOf("X-Custom-Auth" to "Bearer token123")
        )

        assertEquals("https://license.example.com/widevine", streamInfo.drmLicenseUrl)
        assertEquals("Bearer token123", streamInfo.drmHeaders["X-Custom-Auth"])
    }

    @Test
    fun testVideoQualityAndServerDrmProperties() {
        val quality = VideoQuality(
            qualityLabel = "1080p",
            url = "https://example.com/stream.mpd",
            drmLicenseUrl = "https://license.example.com/widevine/1080",
            drmHeaders = mapOf("Authorization" to "Bearer 456")
        )
        val server = VideoServer(
            name = "VIP Server",
            embedUrl = "https://example.com/embed",
            qualities = listOf(quality),
            drmLicenseUrl = "https://license.example.com/widevine/server"
        )

        assertEquals("https://license.example.com/widevine/1080", quality.drmLicenseUrl)
        assertEquals("Bearer 456", quality.drmHeaders["Authorization"])
        assertEquals("https://license.example.com/widevine/server", server.drmLicenseUrl)
    }

    @Test
    fun testMediaItemDrmConfigurationBuilding() {
        val streamUrl = "https://example.com/premium_video.mpd"
        val licenseUrl = "https://drm.example.com/license"
        val headers = mapOf("X-License-Token" to "abc-xyz-789")

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))

        val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(licenseUrl)
            .setLicenseRequestHeaders(headers)

        mediaItemBuilder.setDrmConfiguration(drmConfigBuilder.build())
        val mediaItem = mediaItemBuilder.build()

        val drmConfig = mediaItem.localConfiguration?.drmConfiguration
        assertNotNull(drmConfig)
        assertEquals(C.WIDEVINE_UUID, drmConfig?.scheme)
        assertEquals(Uri.parse(licenseUrl), drmConfig?.licenseUri)
        assertEquals("abc-xyz-789", drmConfig?.licenseRequestHeaders?.get("X-License-Token"))
    }
}
