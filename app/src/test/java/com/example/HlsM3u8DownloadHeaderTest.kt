package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import com.example.data.downloads.ExoDownloadManager
import com.example.media.DownloadHeaderRegistry
import com.example.media.PersistentHttpHeaderResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HlsM3u8DownloadHeaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testIsHlsStreamDetection() {
        assertTrue(ExoDownloadManager.isHlsStream("https://stream.example.com/playlist.m3u8"))
        assertTrue(ExoDownloadManager.isHlsStream("https://server.domain/live?token=abc&m3u8=true"))
        assertTrue(ExoDownloadManager.isHlsStream("https://cdn.example.org/master.m3u8?expires=12345"))
    }

    @Test
    fun testHeaderSerializationAndDeserialization() {
        val headers = mapOf(
            "Referer" to "https://licil.org/drama-series/ep-1",
            "User-Agent" to "Mozilla/5.0 Custom Agent",
            "Cookie" to "session_id=abcdef12345; auth=ok"
        )
        val serialized = ExoDownloadManager.serializeHeaders(headers)
        assertTrue(serialized.isNotEmpty())

        val deserialized = ExoDownloadManager.deserializeHeaders(serialized)
        assertEquals("https://licil.org/drama-series/ep-1", deserialized["Referer"])
        assertEquals("Mozilla/5.0 Custom Agent", deserialized["User-Agent"])
        assertEquals("session_id=abcdef12345; auth=ok", deserialized["Cookie"])
    }

    @Test
    fun testBuildDownloadRequestWithHeaderInjection() {
        val streamUri = Uri.parse("https://stream.jucelebrity.com/hls/master.m3u8")
        val customHeaders = mapOf(
            "Referer" to "https://jucelebrity.com/",
            "Origin" to "https://jucelebrity.com",
            "Cookie" to "cf_clearance=token123"
        )

        val downloadRequest = ExoDownloadManager.buildDownloadRequest(
            mediaUri = streamUri,
            headers = customHeaders,
            contentId = "jucelebrity||the_glory||ep_1"
        )

        assertEquals("jucelebrity||the_glory||ep_1", downloadRequest.id)
        assertEquals(MimeTypes.APPLICATION_M3U8, downloadRequest.mimeType)
        assertNotNull(downloadRequest.data)

        // Verify headers registered in DownloadHeaderRegistry
        val registryHeaders = DownloadHeaderRegistry.getHeadersForUri(Uri.parse("https://stream.jucelebrity.com/hls/segment_001.ts"))
        assertEquals("https://jucelebrity.com/", registryHeaders["Referer"])
        assertEquals("https://jucelebrity.com", registryHeaders["Origin"])
    }

    @Test
    fun testResolvingDataSourceMergesRegistryHeaders() {
        val testUri = Uri.parse("https://protected.stream.tv/video/master.m3u8")
        DownloadHeaderRegistry.register(
            downloadId = "protected_stream_123",
            headers = mapOf(
                "Referer" to "https://protected.stream.tv/",
                "X-Auth-Token" to "secure_token_abc"
            ),
            url = testUri.toString()
        )

        val resolved = PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = Uri.parse("https://protected.stream.tv/video/key.key"),
            customHeaders = emptyMap()
        )

        assertEquals("https://protected.stream.tv/", resolved["Referer"])
        assertEquals("secure_token_abc", resolved["X-Auth-Token"])
    }
}
