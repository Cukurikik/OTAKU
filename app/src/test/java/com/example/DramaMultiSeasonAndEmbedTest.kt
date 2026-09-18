package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.base.DramaEpisode
import com.example.data.sources.base.DramaSeason
import com.example.data.sources.jucelebrity.JuCelebritySource
import com.example.data.sources.licil.LicilSource
import com.example.data.sources.lokalfilm.LokalFilmSource
import com.example.data.sources.viki.VikiSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DramaMultiSeasonAndEmbedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testDramaSourcesHierarchy() {
        val licil = LicilSource(context)
        val juCelebrity = JuCelebritySource(context)
        val lokalFilm = LokalFilmSource(context)
        val viki = VikiSource(context)

        assertEquals(ContentType.DRAMA, licil.contentType)
        assertEquals(ContentType.DRAMA, juCelebrity.contentType)
        assertEquals(ContentType.DRAMA, lokalFilm.contentType)
        assertEquals(ContentType.DRAMA, viki.contentType)

        assertTrue("LicilSource should inherit BaseDramaSource", licil is BaseDramaSource)
        assertTrue("JuCelebritySource should inherit BaseDramaSource", juCelebrity is BaseDramaSource)
        assertTrue("LokalFilmSource should inherit BaseDramaSource", lokalFilm is BaseDramaSource)
        assertTrue("VikiSource should inherit BaseDramaSource", viki is BaseDramaSource)
    }

    @Test
    fun testMultiSeasonHierarchyParsing() = runBlocking {
        val fakeMultiSeasonHtml = """
            <html>
            <body>
                <div class="season-list">
                    <div class="season-item" data-season="1">
                        <h3>Season 1</h3>
                        <ul class="episodelist">
                            <li><a href="https://licil.org/drama-s1-ep1/"><span class="num">Episode 1</span></a></li>
                            <li><a href="https://licil.org/drama-s1-ep2/"><span class="num">Episode 2</span></a></li>
                        </ul>
                    </div>
                    <div class="season-item" data-season="2">
                        <h3>Season 2</h3>
                        <ul class="episodelist">
                            <li><a href="https://licil.org/drama-s2-ep1/"><span class="num">Episode 1</span></a></li>
                            <li><a href="https://licil.org/drama-s2-ep2/"><span class="num">Episode 2</span></a></li>
                        </ul>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val customDramaSource = object : BaseDramaSource(context) {
            override val sourceId = "test-drama"
            override val sourceName = "Test Drama"
            override val baseUrl = "https://licil.org"
            override val contentType = ContentType.DRAMA

            override suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
                return Jsoup.parse(fakeMultiSeasonHtml, baseUrl)
            }

            override fun getRecommendations(page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override fun search(query: String, page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override suspend fun getDetail(itemId: String): com.example.data.models.ContentItem? = null
        }

        val seasons = customDramaSource.fetchSeasonsAndEpisodes("https://licil.org/drama-detail/")
        assertEquals(2, seasons.size)

        assertEquals(1, seasons[0].seasonNumber)
        assertEquals(2, seasons[0].episodes.size)
        assertEquals("Episode 1", seasons[0].episodes[0].title)
        assertEquals("https://licil.org/drama-s1-ep1/", seasons[0].episodes[0].url)

        assertEquals(2, seasons[1].seasonNumber)
        assertEquals(2, seasons[1].episodes.size)
        assertEquals("https://licil.org/drama-s2-ep1/", seasons[1].episodes[0].url)

        val chapters = customDramaSource.getChapters("https://licil.org/drama-detail/").first()
        assertEquals(4, chapters.size)
        assertEquals("S1 Episode 1", chapters[0].title)
        assertEquals("S1 Episode 2", chapters[1].title)
        assertEquals("S2 Episode 1", chapters[2].title)
        assertEquals("S2 Episode 2", chapters[3].title)
    }

    @Test
    fun testLokalFilmStyleDataVideoSourceParsing() = runBlocking {
        val fakeLokalFilmHtml = """
            <html>
            <body>
                <ul class="episodes">
                    <li data-video-source="https://video.provider.com/embed/ep1.m3u8">
                        <span class="fwduvp-thumbnail-title">Episode 1: The Beginning</span>
                    </li>
                    <li data-video-source="https://video.provider.com/embed/ep2.m3u8">
                        <span class="fwduvp-thumbnail-title">Episode 2: The Journey</span>
                    </li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val lokalFilmSource = object : BaseDramaSource(context) {
            override val sourceId = "test-lokalfilm"
            override val sourceName = "Test LokalFilm"
            override val baseUrl = "https://lokalfilm.id"
            override val contentType = ContentType.DRAMA

            override suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
                return Jsoup.parse(fakeLokalFilmHtml, baseUrl)
            }

            override fun getRecommendations(page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override fun search(query: String, page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override suspend fun getDetail(itemId: String): com.example.data.models.ContentItem? = null
        }

        val chapters = lokalFilmSource.getChapters("https://lokalfilm.id/film/series-detail").first()
        assertEquals(2, chapters.size)
        assertEquals("Episode 1: The Beginning", chapters[0].title)
        assertEquals("https://video.provider.com/embed/ep1.m3u8", chapters[0].url)
    }
}
