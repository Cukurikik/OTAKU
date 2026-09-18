package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.VideoExtractor
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.anime.extractors.JsPackerUnpacker
import kotlinx.coroutines.runBlocking
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
class VideoHeaderAndExtractorTest {

    @Test
    fun testCreateHeaders() {
        val url = "https://streamwish.to/e/abcdef123456"
        val referer = "https://otakudesu.cloud/episode/one-piece-episode-1100"
        
        val headers = VideoExtractor.createHeaders(url, referer)
        assertEquals(referer, headers["Referer"])
        assertEquals("https://otakudesu.cloud", headers["Origin"])
        assertNotNull(headers["User-Agent"])
        assertTrue(headers["User-Agent"]!!.contains("Mozilla"))

        // When referer is empty, should default to embed host
        val emptyRefHeaders = VideoExtractor.createHeaders(url, "")
        assertEquals("https://streamwish.to/", emptyRefHeaders["Referer"])
        assertEquals("https://streamwish.to", emptyRefHeaders["Origin"])
    }

    @Test
    fun testJsPackerRecursiveUnpacking() {
        // Obfuscated code mimicking Dean Edwards packer
        val packedScript = """
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c+'\\b','g'),k[c]);return p;}('1 0="3://2.4/5.6";',7,7,'videoSource|var|cdn|https|example|stream|m3u8'.split('|')))
        """.trimIndent()

        val unpacked = JsPackerUnpacker.unpack(packedScript)
        assertNotNull(unpacked)
        assertTrue(unpacked!!.contains("https://cdn.example/stream.m3u8"))
    }

    @Test
    fun testVideoExtractorEngineDirectMp4AndM3u8() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = VideoExtractorEngine(context)

        // Direct MP4
        val mp4Url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        val mp4Info = engine.extractVideo(mp4Url, "https://anime-site.test/ep1")
        assertNotNull(mp4Info)
        assertEquals(1, mp4Info!!.qualities.size)
        assertEquals(mp4Url, mp4Info.qualities[0].url)
        assertEquals("https://anime-site.test/ep1", mp4Info.headers["Referer"])
        assertEquals("https://anime-site.test", mp4Info.headers["Origin"])
        assertEquals("https://anime-site.test/ep1", mp4Info.qualities[0].headers["Referer"])
    }

    @Test
    fun testPlayerScreenDynamicHeaderResolverLogic() {
        var currentUrl = "https://vidhide.com/stream/chunk.m3u8"
        var currentHeaders = mapOf(
            "Referer" to "https://nimegami.id/anime/naruto-shippuden-episode-1/",
            "Origin" to "https://nimegami.id",
            "Custom-Auth" to "bearer-xyz-token"
        )

        val resolver = object : androidx.media3.datasource.ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val requestUri = dataSpec.uri
                val custom = currentHeaders
                val streamUrl = currentUrl
                
                val merged = HashMap(dataSpec.httpRequestHeaders)
                merged["User-Agent"] = custom["User-Agent"] ?: "Test-User-Agent"
                
                val referer = custom["Referer"] 
                    ?: custom["referer"]
                    ?: if (streamUrl.isNotBlank() && !streamUrl.startsWith("file://")) streamUrl
                    else requestUri.toString()
                merged["Referer"] = referer
                
                val customOrigin = custom["Origin"] ?: custom["origin"]
                val origin = if (!customOrigin.isNullOrBlank()) {
                    customOrigin
                } else {
                    val refUri = Uri.parse(referer)
                    if (!refUri.scheme.isNullOrBlank() && !refUri.host.isNullOrBlank()) {
                        "${refUri.scheme}://${refUri.host}"
                    } else ""
                }
                if (origin.isNotBlank()) {
                    merged["Origin"] = origin
                }
                
                for ((key, value) in custom) {
                    if (!key.equals("Referer", ignoreCase = true) && 
                        !key.equals("Origin", ignoreCase = true) && 
                        !key.equals("User-Agent", ignoreCase = true)) {
                        merged[key] = value
                    }
                }
                
                return dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
            }
        }

        val requestSpec = DataSpec(Uri.parse("https://cdn.vidhide.com/hls/1080/index.m3u8"))
        val resolvedSpec = resolver.resolveDataSpec(requestSpec)

        assertEquals("https://nimegami.id/anime/naruto-shippuden-episode-1/", resolvedSpec.httpRequestHeaders["Referer"])
        assertEquals("https://nimegami.id", resolvedSpec.httpRequestHeaders["Origin"])
        assertEquals("bearer-xyz-token", resolvedSpec.httpRequestHeaders["Custom-Auth"])

        // Test server switch to Filemoon with new Referer
        currentUrl = "https://filemoon.sx/e/abcd1234"
        currentHeaders = mapOf(
            "Referer" to "https://otakudesu.cloud/anime/one-piece-sub-indo/",
            "Origin" to "https://otakudesu.cloud"
        )

        val switchSpec = resolver.resolveDataSpec(requestSpec)
        assertEquals("https://otakudesu.cloud/anime/one-piece-sub-indo/", switchSpec.httpRequestHeaders["Referer"])
        assertEquals("https://otakudesu.cloud", switchSpec.httpRequestHeaders["Origin"])
    }
}
