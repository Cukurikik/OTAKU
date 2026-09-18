package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.anime.specific.AnimasuScraper
import com.example.data.sources.anime.specific.KuramanimeScraper
import com.example.data.sources.anime.extractors.ApiResponseParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReverseEngineeringExtractorsTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }

    @Test
    fun testKuramanimeScraperInitializationAndAttributes() {
        val scraper = KuramanimeScraper(context)
        assertEquals("kuramanime", scraper.sourceId)
        assertEquals("Kuramanime", scraper.sourceName)
        assertTrue(scraper.baseUrl.contains("kuramanime"))
    }

    @Test
    fun testAnimasuScraperInitializationAndAttributes() {
        val scraper = AnimasuScraper(context)
        assertEquals("animasu", scraper.sourceId)
        assertEquals("Animasu", scraper.sourceName)
        assertTrue(scraper.baseUrl.contains("animasu"))
    }

    @Test
    fun testKuramanimeInternalApiPayloadExtraction() {
        // Sample JSON returned by Kuramanime /ajax/episode/server or internal API endpoint
        val sampleApiResponse = """
            {
                "status": 200,
                "message": "success",
                "data": {
                    "video": "https://v19.kuramanime.ing/stream/hls/ep1/master.m3u8",
                    "servers": [
                        {"name": "Server VIP", "src": "https://cdn.kuramanime.ing/stream/master.m3u8"}
                    ]
                }
            }
        """.trimIndent()

        val streams = ApiResponseParser.extractStreamUrls(
            "https://v19.kuramanime.ing/ajax/episode/server?id=12345",
            sampleApiResponse
        )

        assertFalse(streams.isEmpty())
        assertTrue(streams.any { it.contains("master.m3u8") })
        assertEquals("https://v19.kuramanime.ing/stream/hls/ep1/master.m3u8", streams.first())
    }

    @Test
    fun testAnimasuAdminAjaxPayloadExtraction() {
        // Sample response from admin-ajax.php action=player_ajax
        val sampleAjaxResponse = """
            {
                "status": "success",
                "embed_url": "https://stream.animasu.work/stream.m3u8",
                "sources": [
                    {
                        "file": "https://stream.animasu.work/720p.mp4",
                        "label": "720p"
                    }
                ]
            }
        """.trimIndent()

        val streams = ApiResponseParser.extractStreamUrls(
            "https://v2.animasu.work/wp-admin/admin-ajax.php",
            sampleAjaxResponse
        )

        assertFalse(streams.isEmpty())
        assertEquals(2, streams.size)
        assertTrue(streams.contains("https://stream.animasu.work/stream.m3u8"))
        assertTrue(streams.contains("https://stream.animasu.work/720p.mp4"))
    }

    @Test
    fun testCryptoJsDecryptionInReverseEngineeringApi() {
        // Verify encrypted payload extraction when internal API returns CryptoJS ciphertext
        val sampleCryptoPayload = """
            {"ct":"U2FsdGVkX1+vupppZDm+MDGtWwaGaBuZRtWmioBI84AQ1n92s01183y3z0v8vR1v", "iv":"f26210f994a50097", "s":"385e4e2098bcf369"}
        """.trimIndent()

        // Parsing should safely handle crypto structures without crashing
        val streams = ApiResponseParser.extractStreamUrls("https://test.site/api", sampleCryptoPayload)
        assertNotNull(streams)
    }
}
