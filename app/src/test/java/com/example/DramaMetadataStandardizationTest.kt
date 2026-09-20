package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.direct.AnimeDramaScraperEngine
import com.example.data.sources.direct.ScraperSourceConfig
import com.example.data.sources.direct.Selectors
import com.example.data.sources.drama.GenericDramaScraper
import com.example.data.sources.drama.base.WpDramaThemeScraper
import com.example.data.sources.drama.specific.DrakorIdDramaScraper
import com.example.data.sources.drama.specific.LicilScraper
import com.example.data.sources.drakor.DrakorSource
import com.example.data.sources.licil.LicilSource
import com.example.data.sources.lokalfilm.LokalFilmSource
import com.example.data.sources.templates.DramaScraperTemplate
import com.example.data.sources.viki.VikiSource
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DramaMetadataStandardizationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private class TestDramaSource(context: Context) : BaseDramaSource(context) {
        override val sourceId: String = "test-drama"
        override val sourceName: String = "Test Drama"
        override val baseUrl: String = "https://example.com"
        override fun getRecommendations(page: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.models.ContentItem>>()
        override fun search(query: String, page: Int) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.models.ContentItem>>()
        override suspend fun getDetail(itemId: String) = null
        override fun getChapters(itemId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.models.Chapter>>()
    }

    @Test
    fun testCastListExtractionMultiSelector() {
        val source = TestDramaSource(context)

        // 1. GMR / WP Drama theme style
        val gmrHtml = """
            <html>
            <body>
                <div class="gmr-moviedata"><span class="gmr-movie-info">Cast:</span> <a href="/cast/song-joong-ki">Song Joong-ki</a>, <a href="/cast/lee-sung-min">Lee Sung-min</a>, <a href="/cast/shin-hyun-bin">Shin Hyun-bin</a></div>
            </body>
            </html>
        """.trimIndent()
        val docGmr = Jsoup.parse(gmrHtml)
        val castsGmr = source.extractCastList(docGmr)
        assertEquals(3, castsGmr.size)
        assertTrue(castsGmr.contains("Song Joong-ki"))
        assertTrue(castsGmr.contains("Lee Sung-min"))
        assertTrue(castsGmr.contains("Shin Hyun-bin"))

        // 2. Schema.org / itemprop style
        val schemaHtml = """
            <html>
            <body>
                <div class="cast-list">
                    <span itemprop="actor"><a href="/actor/1">Park Seo-joon</a></span>
                    <span itemprop="actor"><a href="/actor/2">Kim Da-mi</a></span>
                    <span itemprop="actor"><a href="/actor/3">Kwon Na-ra</a></span>
                </div>
            </body>
            </html>
        """.trimIndent()
        val docSchema = Jsoup.parse(schemaHtml)
        val castsSchema = source.extractCastList(docSchema)
        assertEquals(3, castsSchema.size)
        assertTrue(castsSchema.contains("Park Seo-joon"))
        assertTrue(castsSchema.contains("Kim Da-mi"))

        // 3. Meta tag actor style
        val metaHtml = """
            <html>
            <head>
                <meta property="video:actor" content="Hyun Bin" />
                <meta property="video:actor" content="Son Ye-jin" />
            </head>
            <body></body>
            </html>
        """.trimIndent()
        val docMeta = Jsoup.parse(metaHtml)
        val castsMeta = source.extractCastList(docMeta)
        assertEquals(2, castsMeta.size)
        assertTrue(castsMeta.contains("Hyun Bin"))
        assertTrue(castsMeta.contains("Son Ye-jin"))

        // 4. Plain table row with Pemeran / Bintang
        val tableHtml = """
            <html>
            <body>
                <table>
                    <tr><th>Pemeran:</th><td>Gong Yoo, Kim Go-eun, Lee Dong-wook</td></tr>
                </table>
            </body>
            </html>
        """.trimIndent()
        val docTable = Jsoup.parse(tableHtml)
        val castsTable = source.extractCastList(docTable)
        assertEquals(3, castsTable.size)
        assertTrue(castsTable.contains("Gong Yoo"))
        assertTrue(castsTable.contains("Kim Go-eun"))
        assertTrue(castsTable.contains("Lee Dong-wook"))
    }

    @Test
    fun testSubtitleStatusExtraction() {
        val source = TestDramaSource(context)

        // Sub Indo badge
        val doc1 = Jsoup.parse("""<html><body><span class="sub-status">Sub Indo</span></body></html>""")
        assertEquals("Sub Indo", source.extractSubtitleStatus(doc1))

        // Eng Sub badge
        val doc2 = Jsoup.parse("""<html><body><div class="badge-sub">English Sub</div></body></html>""")
        assertEquals("Eng Sub", source.extractSubtitleStatus(doc2))

        // RAW badge
        val doc3 = Jsoup.parse("""<html><body><span class="quality">RAW</span></body></html>""")
        assertEquals("Raw", source.extractSubtitleStatus(doc3))

        // Hardsub badge
        val doc4 = Jsoup.parse("""<html><body><span class="gmr-sub">Hardsub Indo</span></body></html>""")
        assertEquals("Hardsub", source.extractSubtitleStatus(doc4))

        // Fallback to title
        val doc5 = Jsoup.parse("""<html><head><title>Moving Episode 1 EngSub</title></head><body></body></html>""")
        assertEquals("Eng Sub", source.extractSubtitleStatus(doc5, "Moving Episode 1 EngSub"))

        val docDefault = Jsoup.parse("""<html><head><title>Weak Hero Class 1</title></head><body></body></html>""")
        assertEquals("Sub Indo", source.extractSubtitleStatus(docDefault, "Weak Hero Class 1"))
    }

    @Test
    fun testSeasonNumberParsing() {
        val source = TestDramaSource(context)

        // Meta tag season
        val docMeta = Jsoup.parse("""<html><head><meta name="season" content="2" /></head><body></body></html>""")
        assertEquals(2, source.parseSeasonNumber("Taxi Driver", docMeta))

        // DOM tag season
        val docTag = Jsoup.parse("""<html><body><div class="season" data-season="3">Season 3</div></body></html>""")
        assertEquals(3, source.parseSeasonNumber("Sweet Home", docTag))

        // Title regex
        assertEquals(2, source.parseSeasonNumber("Taxi Driver Season 2"))
        assertEquals(4, source.parseSeasonNumber("Stranger Things S04"))
        assertEquals(1, source.parseSeasonNumber("Single's Inferno Season 1"))
        assertNull(source.parseSeasonNumber("Queen of Tears"))
    }

    @Test
    fun testBaseDramaExtractDramaMetadata() {
        val source = TestDramaSource(context)
        val html = """
            <html>
            <head><title>Alchemy of Souls Season 2 Ep 1 Sub Indo</title></head>
            <body>
                <span class="sub-status">Sub Indo</span>
                <div class="gmr-moviedata"><span class="label">Cast:</span> <a href="#">Lee Jae-wook</a>, <a href="#">Go Youn-jung</a></div>
            </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val metadata = source.extractDramaMetadata(doc, "Alchemy of Souls Season 2")

        assertEquals(2, metadata.seasonNumber)
        assertEquals("Sub Indo", metadata.subtitleStatus)
        assertEquals(2, metadata.castList.size)
        assertTrue(metadata.castList.contains("Lee Jae-wook"))
        assertTrue(metadata.castList.contains("Go Youn-jung"))
    }

    @Test
    fun testVikiSourceJsonMetadataParsing() {
        val viki = VikiSource(context)
        val containerJson = JSONObject("""
            {
                "id": "12345v",
                "title": "Hospital Playlist Season 2",
                "descriptions": {"id": "Kisah persahabatan 5 dokter spesialis."},
                "images": {"poster": {"url": "https://img.viki.com/poster.jpg"}},
                "genres": ["Medical", "Romance"],
                "origin": {"country": "KR"},
                "cast": [
                    {"name": "Jo Jung-suk"},
                    {"name": "Yoo Yeon-seok"},
                    {"name": "Jung Kyung-ho"},
                    {"name": "Kim Dae-myung"},
                    {"name": "Jeon Mi-do"}
                ],
                "subtitles": {"id": true, "en": true},
                "created_at": "2021-06-17"
            }
        """.trimIndent())

        val containers = viki.parseContainersJson(JSONObject("""{"response": [$containerJson]}"""))
        assertEquals(1, containers.size)
        val item = containers.first()
        assertEquals("12345v", item.id)
        assertEquals("Hospital Playlist Season 2", item.title)
    }
}
