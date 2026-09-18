package com.example.download

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import com.example.data.downloads.ExoDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HlsDownloadEngineCorruptionFixTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testIsHlsStreamDetection() {
        val hlsUrl1 = "https://cdn.example.com/stream/index.m3u8"
        val hlsUrl2 = "https://stream.anime.com/master.m3u8?token=xyz123&expiry=9999"
        val hlsUrl3 = "https://gateway.com/video?format=application/x-mpegurl"
        val hlsUrl4 = "https://video.host/playlist.M3U8"
        val progressiveMp4 = "https://cdn.example.com/video/episode_01.mp4"
        val progressiveMkv = "https://cdn.example.com/video/movie.mkv?download=true"

        assertTrue("HLS index.m3u8 should be recognized as HLS", ExoDownloadManager.isHlsStream(hlsUrl1))
        assertTrue("HLS master.m3u8 with query params should be recognized as HLS", ExoDownloadManager.isHlsStream(hlsUrl2))
        assertTrue("MPEG-URL format parameter should be recognized as HLS", ExoDownloadManager.isHlsStream(hlsUrl3))
        assertTrue("Uppercase .M3U8 extension should be recognized as HLS", ExoDownloadManager.isHlsStream(hlsUrl4))

        assertFalse("Progressive MP4 must NOT be recognized as HLS", ExoDownloadManager.isHlsStream(progressiveMp4))
        assertFalse("Progressive MKV must NOT be recognized as HLS", ExoDownloadManager.isHlsStream(progressiveMkv))
    }

    @Test
    fun testExoDownloadManagerInitialization() {
        val manager = ExoDownloadManager.getManager(context)
        assertNotNull("ExoDownloadManager instance must not be null", manager)
        val cache = ExoDownloadManager.getCache(context)
        assertNotNull("SimpleCache instance must not be null", cache)
    }

    @Test
    fun testHlsMimeTypeMapping() {
        val m3u8Mime = MimeTypes.APPLICATION_M3U8
        assertEquals("application/x-mpegURL", m3u8Mime)
    }
}
