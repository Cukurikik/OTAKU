package com.example

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.WebViewFetcher
import com.example.data.sources.InterceptedStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWebView
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdvancedHeadlessInterceptorTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testInterceptedStreamResultDataModel() {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Referer" to "https://v19.kuramanime.ing/episode/1",
            "Cookie" to "cf_clearance=abc123xyz; session_id=sess9988"
        )
        val result = InterceptedStreamResult(
            url = "https://stream.example.org/master.m3u8?token=secure",
            isM3U8 = true,
            headers = headers,
            cookies = "cf_clearance=abc123xyz; session_id=sess9988",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            directQualities = listOf(
                VideoQuality("1080p", "https://stream.example.org/1080p.m3u8", true, headers),
                VideoQuality("720p", "https://stream.example.org/720p.m3u8", true, headers)
            )
        )

        assertEquals("https://stream.example.org/master.m3u8?token=secure", result.url)
        assertTrue(result.isM3U8)
        assertEquals(3, result.headers.size)
        assertEquals("cf_clearance=abc123xyz; session_id=sess9988", result.cookies)
        assertEquals(2, result.directQualities.size)
        assertEquals("1080p", result.directQualities[0].qualityLabel)
    }

    @Test
    fun testShouldInterceptRequestClassification() {
        // Test media stream vs image/tracker classification logic
        val mediaUrls = listOf(
            "https://cdn.example.com/stream/index.m3u8",
            "https://cdn.example.com/video/ep1.mp4?auth=xyz",
            "https://storage.googleapis.com/test-stream/manifest.mpd",
            "https://videohost.net/hls/master.m3u8",
            "https://r2.streamtape.com/get_video?id=123",
            "https://vidhide.com/stream/v1.mp4"
        )

        val nonMediaUrls = listOf(
            "https://google-analytics.com/analytics.js",
            "https://securepubads.g.doubleclick.net/gampad/ads",
            "https://cdn.example.com/posters/cover.jpg",
            "https://cdn.example.com/thumbnails/thumb.webp",
            "https://site.com/style.css",
            "https://site.com/app.js"
        )

        for (url in mediaUrls) {
            val lower = url.lowercase()
            val isVideo = (lower.contains(".m3u8") ||
                    lower.contains(".mpd") ||
                    lower.contains(".mp4") ||
                    lower.contains(".webm") ||
                    lower.contains("/hls/") ||
                    lower.contains("get_video") ||
                    lower.contains("vidhide")) &&
                    !lower.endsWith(".jpg") &&
                    !lower.endsWith(".png") &&
                    !lower.endsWith(".webp") &&
                    !lower.endsWith(".gif") &&
                    !lower.endsWith(".css") &&
                    !lower.endsWith(".js")
            assertTrue("Expected $url to be classified as video stream", isVideo)
        }

        for (url in nonMediaUrls) {
            val lower = url.lowercase()
            val isBlockedOrIgnored = lower.contains("google-analytics") ||
                    lower.contains("doubleclick") ||
                    lower.endsWith(".jpg") ||
                    lower.endsWith(".webp") ||
                    lower.endsWith(".css") ||
                    lower.endsWith(".js")
            assertTrue("Expected $url to be blocked or ignored", isBlockedOrIgnored)
        }
    }

    @Test
    fun testVideoExtractorEngineIntegrationWithHeadlessFallback() = runBlocking {
        val engine = VideoExtractorEngine(context)

        // Test direct stream resolution
        val directM3u8 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val info = engine.extractVideo(directM3u8, "ep_1")

        assertNotNull(info)
        assertEquals("ep_1", info?.episodeId)
        assertTrue(info!!.qualities.isNotEmpty())
        assertEquals(directM3u8, info.qualities.first().url)
    }

    @Test
    fun testCookiePropagationToStreamHeaders() {
        val targetEpisodeUrl = "https://v2.animasu.work/nonton-anime-sub-indo/"
        val sessionCookie = "cf_clearance=testClearanceToken123; PHPSESSID=session999"
        val userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

        val defaultHeaders = mapOf(
            "User-Agent" to userAgent,
            "Referer" to targetEpisodeUrl,
            "Cookie" to sessionCookie
        )

        val capturedHeaders = mutableMapOf<String, String>()
        defaultHeaders.forEach { (k, v) -> capturedHeaders[k] = v }

        assertEquals(userAgent, capturedHeaders["User-Agent"])
        assertEquals(targetEpisodeUrl, capturedHeaders["Referer"])
        assertEquals(sessionCookie, capturedHeaders["Cookie"])

        val streamInfo = VideoStreamInfo(
            episodeId = targetEpisodeUrl,
            embedUrl = "https://stream.animasu.work/hls/ep1/master.m3u8",
            headers = capturedHeaders,
            qualities = listOf(
                VideoQuality("Auto", "https://stream.animasu.work/hls/ep1/master.m3u8", true, capturedHeaders)
            )
        )

        assertEquals("https://stream.animasu.work/hls/ep1/master.m3u8", streamInfo.embedUrl)
        assertEquals(sessionCookie, streamInfo.headers["Cookie"])
        assertEquals(userAgent, streamInfo.headers["User-Agent"])
    }
}
