package com.example.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.anime.extractors.ApiResponseParser
import com.example.network.interceptor.VideoUrlInterceptor
import com.example.network.interceptor.WebAppInterface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoUrlInterceptorTest {

    private lateinit var context: Context
    private lateinit var videoInterceptor: VideoUrlInterceptor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        videoInterceptor = VideoUrlInterceptor(context)
    }

    @Test
    fun testDirectM3u8StreamInterception_returnsStreamImmediately() = runBlocking {
        val target = "https://cdn.example.com/hls/master.m3u8"
        val result = videoInterceptor.interceptStreamUrl(target)
        assertNotNull(result)
        assertEquals(target, result)
    }

    @Test
    fun testDirectMp4StreamInterception_returnsStreamImmediately() = runBlocking {
        val target = "https://cdn.example.com/videos/episode_1080p.mp4"
        val result = videoInterceptor.interceptStreamUrl(target)
        assertNotNull(result)
        assertEquals(target, result)
    }

    @Test
    fun testXhrApiResponseSniffing_extractsStreamUrlWithoutEncryptionKey() {
        val jsonPayload = """
            {
              "status": "success",
              "stream_url": "https://stream.kuramanime.net/hls/video.m3u8",
              "format": "application/json"
            }
        """.trimIndent()

        val streams = ApiResponseParser.extractStreamUrls("https://kuramanime.example/ajax/get_video", jsonPayload)
        assertEquals(1, streams.size)
        assertEquals("https://stream.kuramanime.net/hls/video.m3u8", streams[0])
    }

    @Test
    fun testWebAppInterface_notifiesStreamDiscovery() {
        var discoveredStream: String? = null
        val bridge = WebAppInterface { streamUrl, _ ->
            discoveredStream = streamUrl
        }

        val jsonPayload = """
            {
              "file": "https://cdn.animasu.org/hls/ep1.m3u8",
              "type": "hls",
              "label": "1080p"
            }
        """.trimIndent()

        bridge.processApiResponse("https://animasu.example/api/source", jsonPayload)

        assertNotNull(discoveredStream)
        assertEquals("https://cdn.animasu.org/hls/ep1.m3u8", discoveredStream)
    }

    @Test
    fun testWebAppInterface_processesBlobUrl() {
        var discoveredStream: String? = null
        val bridge = WebAppInterface { streamUrl, _ ->
            discoveredStream = streamUrl
        }

        bridge.processBlobUrl("blob:https://player.example.com/1234-5678", "video/mp4")

        assertNotNull(discoveredStream)
        assertEquals("blob:https://player.example.com/1234-5678", discoveredStream)
    }
}
