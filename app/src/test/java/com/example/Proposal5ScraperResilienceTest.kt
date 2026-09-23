package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.NetworkModule
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.anime.extractors.FallbackExtractorChain
import com.example.data.sources.anime.extractors.StreamWishExtractor
import com.example.network.interceptor.CloudflareWorkerProxyInterceptor
import com.example.network.rotator.DynamicDomainRotator
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Proposal5ScraperResilienceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        SourceBaseUrlStore.clear(context, "kuramanime")
        SourceBaseUrlStore.clear(context, "animasu")
    }

    @Test
    fun testDynamicDomainRotatorActiveBaseUrlAndRotation() = runBlocking {
        val defaultUrl = "https://v19.kuramanime.ing/"
        val active = DynamicDomainRotator.getActiveDomain(context, "kuramanime", defaultUrl)
        assertEquals("https://v19.kuramanime.ing", active)

        // Simulate rotation to healthy mirror
        val mirror = "https://v9.kuramanime.blog"
        SourceBaseUrlStore.save(context, "kuramanime", mirror)

        val updated = DynamicDomainRotator.getActiveDomain(context, "kuramanime", defaultUrl)
        assertEquals(mirror, updated)

        // URL Rewriting
        val original = "https://v19.kuramanime.ing/anime/naruto-shippuden/episode-500?filter=hd#player"
        val rewritten = DynamicDomainRotator.rewriteUrl(original, mirror)
        assertEquals("https://v9.kuramanime.blog/anime/naruto-shippuden/episode-500?filter=hd#player", rewritten)
    }

    @Test
    fun testCloudflareWorkerProxyInterceptorBypasses403() {
        var interceptedRequestUrl: String? = null
        var bypassHeader: String? = null
        var authHeader: String? = null

        val testClient = OkHttpClient.Builder()
            .addInterceptor(CloudflareWorkerProxyInterceptor())
            .addInterceptor { chain ->
                val req = chain.request()
                interceptedRequestUrl = req.url.toString()
                bypassHeader = req.header("X-Bypass-Engine")
                authHeader = req.header("Authorization")

                if (req.url.host.contains("workers.dev")) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK via Worker")
                        .body("<html><body>Bypassed Anime Content</body></html>".toResponseBody("text/html".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden - Cloudflare Turnstile")
                        .body("<html><title>Just a moment...</title><body>Turnstile challenge</body></html>".toResponseBody("text/html".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val response = testClient.newCall(
            Request.Builder()
                .url("https://v19.kuramanime.ing/anime/one-piece")
                .build()
        ).execute()

        assertEquals(200, response.code)
        val body = response.body?.string().orEmpty()
        assertTrue("Body should contain bypassed content", body.contains("Bypassed Anime Content"))
        assertNotNull("Should route through workers.dev", interceptedRequestUrl)
        assertTrue("URL should be worker proxy", interceptedRequestUrl!!.contains("anime-aggregator.njirlah.workers.dev"))
        assertEquals("HeadlessWebView", bypassHeader)
        assertEquals("Bearer ${NetworkModule.WORKER_AUTH_TOKEN}", authHeader)
    }

    @Test
    fun testFallbackExtractorChainPackerUnpacking() {
        val fallbackChain = FallbackExtractorChain()

        // Dean Edwards packed script containing .m3u8 stream
        val packedJs = """
            <script>
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p;}('0.1={2:"3://4.5.6/7/8.9"};',10,10,'window|player|file|https|cdn|stream|com|hls|master|m3u8'.split('|')))
            </script>
        """.trimIndent()

        val directStream = fallbackChain.extractDirectStream(packedJs)
        assertNotNull("Direct stream should be unpacked from Packer JS", directStream)
        assertEquals("https://cdn.stream.com/hls/master.m3u8", directStream)
    }

    @Test
    fun testFallbackExtractorChainStreamWish() {
        val streamWish = StreamWishExtractor()
        val wishHtml = """
            <div id="player">
                <script type="text/javascript">
                    sources: [{"file":"https://swishsrv.com/hls/ep1_720p.m3u8","label":"720p"}]
                </script>
            </div>
        """.trimIndent()

        assertTrue("Should detect StreamWish payload", streamWish.canExtract("https://streamwish.to/e/abc123xyz"))
        val extracted = streamWish.extractFromHtml(wishHtml, "streamwish.to")
        assertTrue("Should extract stream qualities", extracted.isNotEmpty())
        assertEquals("https://swishsrv.com/hls/ep1_720p.m3u8", extracted.first().url)
        assertTrue(extracted.first().isM3U8)
    }

    @Test
    fun testFallbackExtractorChainDirectStreamResolution() {
        val fallbackChain = FallbackExtractorChain()
        val directHtml = """
            <html>
                <body>
                    <video src="https://storage.cdn.com/videos/naruto_ep01.mp4"></video>
                </body>
            </html>
        """.trimIndent()

        val stream = fallbackChain.extractDirectStream(directHtml)
        assertNotNull(stream)
        assertEquals("https://storage.cdn.com/videos/naruto_ep01.mp4", stream)
    }
}
