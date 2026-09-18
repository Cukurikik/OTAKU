package com.example

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.example.media.PersistentHttpHeaderResolver
import com.example.media.PersistentResolvingDataSourceFactory
import com.example.media.withAdditionalHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@UnstableApi
class ResolvingDataSourceTest {

    @Test
    fun testWithAdditionalHeadersExtension() {
        val originalUri = Uri.parse("https://dood.to/pass_md5/xyz?token=123")
        val dataSpec = DataSpec(originalUri)
        val additional = mapOf(
            "Cookie" to "session_token=dood_sess_12345; cf_clearance=cf987",
            "Origin" to "https://dood.to",
            "Referer" to "https://dood.to/e/xyz"
        )

        val resolved = dataSpec.withAdditionalHeaders(additional)

        assertEquals("session_token=dood_sess_12345; cf_clearance=cf987", resolved.httpRequestHeaders["Cookie"])
        assertEquals("https://dood.to", resolved.httpRequestHeaders["Origin"])
        assertEquals("https://dood.to/e/xyz", resolved.httpRequestHeaders["Referer"])
    }

    @Test
    fun testDoodStreamPersistentHeaderResolution() {
        val segmentUri = Uri.parse("https://ds2play.com/video/chunk_001.ts")
        val customHeaders = mapOf(
            "Cookie" to "token=abc123dood",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        )

        val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = segmentUri,
            customHeaders = customHeaders
        )

        assertEquals("https://ds2play.com", resolvedHeaders["Origin"])
        assertEquals("https://ds2play.com/", resolvedHeaders["Referer"])
        assertEquals("token=abc123dood", resolvedHeaders["Cookie"])
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", resolvedHeaders["User-Agent"])
    }

    @Test
    fun testMp4UploadPersistentHeaderResolution() {
        val segmentUri = Uri.parse("https://www.mp4upload.com/d/xyz/video.mp4")
        val customHeaders = mapOf(
            "Cookie" to "aff=1234; session=mp4sess99",
            "Referer" to "https://www.mp4upload.com/embed-xyz.html"
        )

        val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = segmentUri,
            customHeaders = customHeaders
        )

        assertEquals("https://www.mp4upload.com", resolvedHeaders["Origin"])
        assertEquals("https://www.mp4upload.com/embed-xyz.html", resolvedHeaders["Referer"])
        assertEquals("aff=1234; session=mp4sess99", resolvedHeaders["Cookie"])
    }

    @Test
    fun testFileLionsAndVidhidePersistentHeaderResolution() {
        val segmentUri = Uri.parse("https://filelions.to/hls/master.m3u8")
        val customHeaders = mapOf(
            "Cookie" to "filelions_token=fl_token_777",
            "X-Custom-Auth" to "bearer_token_xyz"
        )

        val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = segmentUri,
            customHeaders = customHeaders
        )

        assertEquals("https://filelions.to", resolvedHeaders["Origin"])
        assertEquals("https://filelions.to/", resolvedHeaders["Referer"])
        assertEquals("filelions_token=fl_token_777", resolvedHeaders["Cookie"])
        assertEquals("bearer_token_xyz", resolvedHeaders["X-Custom-Auth"])
    }

    @Test
    fun testResolvingDataSourceFactoryEndToEnd() {
        var currentDynamicHeaders = mapOf(
            "Cookie" to "sess=initial_sess",
            "Referer" to "https://upstream.video/watch/1"
        )
        var currentStreamUrl = "https://upstream.video/watch/1"

        val upstreamFactory = DataSource.Factory { ByteArrayDataSource(byteArrayOf(1, 2, 3)) }
        val resolvingFactory = PersistentResolvingDataSourceFactory.create(
            upstreamFactory = upstreamFactory,
            headersProvider = { currentDynamicHeaders },
            streamUrlProvider = { currentStreamUrl }
        )

        val ds = resolvingFactory.createDataSource()
        assertNotNull(ds)

        val testSpec = DataSpec(Uri.parse("https://cdn.upstream.video/segment1.ts"))
        val resolver = PersistentHttpHeaderResolver(
            headersProvider = { currentDynamicHeaders },
            streamUrlProvider = { currentStreamUrl }
        )

        val resolvedSpec1 = resolver.resolveDataSpec(testSpec)
        assertEquals("https://upstream.video/watch/1", resolvedSpec1.httpRequestHeaders["Referer"])
        assertEquals("sess=initial_sess", resolvedSpec1.httpRequestHeaders["Cookie"])

        // Dynamic change of session cookie / referer during stream switch
        currentDynamicHeaders = mapOf(
            "Cookie" to "sess=updated_sess_999",
            "Referer" to "https://upstream.video/watch/2",
            "Origin" to "https://upstream.video"
        )
        currentStreamUrl = "https://upstream.video/watch/2"

        val resolvedSpec2 = resolver.resolveDataSpec(testSpec)
        assertEquals("https://upstream.video/watch/2", resolvedSpec2.httpRequestHeaders["Referer"])
        assertEquals("https://upstream.video", resolvedSpec2.httpRequestHeaders["Origin"])
        assertEquals("sess=updated_sess_999", resolvedSpec2.httpRequestHeaders["Cookie"])
    }
}
