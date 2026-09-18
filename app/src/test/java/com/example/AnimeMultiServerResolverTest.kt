package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.sources.anime.SpecificAnimeScrapersCatalog
import com.example.data.sources.anime.base.AjaxObfuscatedThemeScraper
import com.example.data.sources.anime.base.KatsuAnimeVnThemeScraper
import com.example.data.sources.anime.base.OtakuPressThemeScraper
import com.example.data.sources.base.detectProviderName
import com.example.data.sources.base.extractQualityLabel
import com.example.data.sources.base.isBase64Encoded
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AnimeMultiServerResolverTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext<Context>() }

    @Test
    fun testAll23AnimeScrapersCatalogInstantiation() {
        val scrapers = SpecificAnimeScrapersCatalog.createAllScrapers(context)
        assertEquals("Harus ada 21 scraper spesifik dalam katalog", 21, scrapers.size)
        
        val sourceIds = scrapers.map { it.sourceId }.toSet()
        val expectedIds = listOf(
            "nimegami", "stucknime", "ylnime", "wibuku",
            "myanimeku", "kuronime", "animeindo", "animasuid",
            "animemovies", "anime-kuindo", "meownime-net", "animasu-movie",
            "anime154", "nontonanimeid", "otakupoi",
            "animeku-info", "miranime", "gomunime", "alqanime", "nimeindo", "subnime"
        )
        for (id in expectedIds) {
            assertTrue("Katalog harus memuat scraper dengan ID $id", sourceIds.contains(id))
        }
    }

    @Test
    fun testKatsuAnimeVnMultiServerExtraction() = kotlinx.coroutines.runBlocking {
        val scraper = KatsuAnimeVnThemeScraper(context, "test-katsu", "Test Katsu", "https://katsu.example.com")
        val base64Embed = Base64.encodeToString("<iframe src=\"https://streamwish.to/e/katsu123\"></iframe>".toByteArray(), Base64.NO_WRAP)

        val html = """
            <html>
            <body>
                <select id="server">
                    <option value="$base64Embed">Server Wish 720p</option>
                    <option value="https://filelions.to/v/fl456">Server Lions 1080p</option>
                </select>
                <div class="player-embed">
                    <iframe src="https://vidhide.com/v/vh789"></iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        val streams = scraper.extractMultiServerStreams(html, "https://katsu.example.com/ep-1")
        assertTrue("Harus menemukan setidaknya 2 server", streams.size >= 2)

        val hasWish = streams.any { it.url.contains("streamwish") || it.embedUrl.contains("streamwish") }
        val hasLions = streams.any { it.url.contains("filelions") || it.embedUrl.contains("filelions") }
        val hasVidhide = streams.any { it.url.contains("vidhide") || it.embedUrl.contains("vidhide") }

        assertTrue("Harus mengekstrak embed streamwish dari base64", hasWish)
        assertTrue("Harus mengekstrak embed filelions", hasLions)
        assertTrue("Harus mengekstrak iframe player-embed", hasVidhide)
    }

    @Test
    fun testOtakuPressMultiServerExtraction() = kotlinx.coroutines.runBlocking {
        val scraper = OtakuPressThemeScraper(context, "test-otaku", "Test Otaku", "https://otaku.example.com")
        val base64Content = Base64.encodeToString("https://streamwish.to/e/otaku999".toByteArray(), Base64.NO_WRAP)

        val html = """
            <html>
            <body>
                <div class="mirror-stream">
                    <a data-content="$base64Content">Server 1 - 720p HD</a>
                    <a data-content="https://filelions.to/v/fl999">Server 2 - 1080p FHD</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val streams = scraper.extractMultiServerStreams(html, "https://otaku.example.com/ep-2")
        assertTrue("Harus menemukan server dari mirror-stream", streams.isNotEmpty())
        assertTrue("Stream harus mengandung streamwish", streams.any { it.url.contains("streamwish") || it.embedUrl.contains("streamwish") })
    }

    @Test
    fun testAjaxObfuscatedPackedJsExtraction() = kotlinx.coroutines.runBlocking {
        val scraper = AjaxObfuscatedThemeScraper(context, "test-ajax", "Test Ajax", "https://ajax.example.com")

        val html = """
            <html>
            <body>
                <ul class="server-list">
                    <li data-post="https://streamwish.to/e/ajax123">Server Alpha 720p</li>
                    <li data-post="https://filelions.to/v/ajax456">Server Beta 480p</li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val streams = scraper.extractMultiServerStreams(html, "https://ajax.example.com/ep-3")
        assertTrue("Harus menemukan server dari server-list", streams.size >= 2)
    }

    @Test
    fun testQualityAndProviderDetectionHelpers() {
        assertEquals("1080p", "Server Alpha 1080p FHD".extractQualityLabel())
        assertEquals("720p", "Stream HD 720p".extractQualityLabel())
        assertEquals("480p", "Mirror SD 480p".extractQualityLabel())
        assertEquals("360p", "Server Hemat 360p".extractQualityLabel())

        assertEquals("Streamwish", "https://streamwish.to/e/abc".detectProviderName())
        assertEquals("FileLions", "https://filelions.to/v/xyz".detectProviderName())
        assertEquals("VidHide", "https://vidhide.com/v/123".detectProviderName())

        val validB64 = Base64.encodeToString("Hello World".toByteArray(), Base64.NO_WRAP)
        assertTrue(validB64.isBase64Encoded())
        assertFalse("http://example.com".isBase64Encoded())
        assertFalse("short".isBase64Encoded())
    }
}
