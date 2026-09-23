package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.ContentType
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.drama.DramaEpisode
import com.example.data.sources.drama.DramaSeason
import com.example.data.sources.drama.DramaSeasonResolver
import com.example.data.sources.drama.DramaSeries
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Proposal1DramaSeasonResolverTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testMultiSeasonTabExtractionAndAbsoluteIndexing() {
        val multiSeasonHtml = """
            <html>
            <head><title>Kingdom Series - Licil</title></head>
            <body>
                <h1 class="entry-title">Kingdom</h1>
                <div class="season-list">
                    <div class="season-item" data-season="1">
                        <h3>Season 1</h3>
                        <ul class="episodelist">
                            <li><a href="https://digitalepiphanies.org/kingdom-s1-ep1/"><span class="num">Episode 1</span></a></li>
                            <li><a href="https://digitalepiphanies.org/kingdom-s1-ep2/"><span class="num">Episode 2</span></a></li>
                            <li><a href="https://digitalepiphanies.org/kingdom-s1-ep3/"><span class="num">Episode 3</span></a></li>
                        </ul>
                    </div>
                    <div class="season-item" data-season="2">
                        <h3>Season 2</h3>
                        <ul class="episodelist">
                            <li><a href="https://digitalepiphanies.org/kingdom-s2-ep1/"><span class="num">Episode 1</span></a></li>
                            <li><a href="https://digitalepiphanies.org/kingdom-s2-ep2/"><span class="num">Episode 2</span></a></li>
                        </ul>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val series = DramaSeasonResolver.parseSeasonsFromHtml(multiSeasonHtml, "https://digitalepiphanies.org")
        assertEquals("Kingdom", series.title)
        assertEquals(2, series.seasons.size)
        assertEquals(5, series.totalEpisodes)

        // Verify Season 1
        val s1 = series.seasons[0]
        assertEquals(1, s1.seasonNumber)
        assertEquals(3, s1.episodes.size)
        assertEquals(1, s1.episodes[0].episodeNumberInSeason)
        assertEquals(1, s1.episodes[0].absoluteEpisodeNumber)
        assertEquals("https://digitalepiphanies.org/kingdom-s1-ep1/", s1.episodes[0].streamUrl)

        assertEquals(3, s1.episodes[2].episodeNumberInSeason)
        assertEquals(3, s1.episodes[2].absoluteEpisodeNumber)

        // Verify Season 2 (Absolute indexing must continue: 3 + 1 = 4, 3 + 2 = 5)
        val s2 = series.seasons[1]
        assertEquals(2, s2.seasonNumber)
        assertEquals(2, s2.episodes.size)
        assertEquals(1, s2.episodes[0].episodeNumberInSeason)
        assertEquals(4, s2.episodes[0].absoluteEpisodeNumber)
        assertEquals("https://digitalepiphanies.org/kingdom-s2-ep1/", s2.episodes[0].streamUrl)

        assertEquals(2, s2.episodes[1].episodeNumberInSeason)
        assertEquals(5, s2.episodes[1].absoluteEpisodeNumber)
    }

    @Test
    fun testUnrollRangeResolverBatchEpisodes() {
        val rangeHtml = """
            <html>
            <body>
                <h1 class="entry-title">Squid Game Complete</h1>
                <div class="episodelist">
                    <li>
                        <a href="https://drakorid.net/squid-game-batch/">
                            <span class="num">Episode 1 - 9 End</span>
                            <span class="date">2023-01-01</span>
                        </a>
                    </li>
                </div>
            </body>
            </html>
        """.trimIndent()

        val series = DramaSeasonResolver.parseSeasonsFromHtml(rangeHtml, "https://drakorid.net")
        assertEquals(1, series.seasons.size)
        val episodes = series.seasons[0].episodes

        // Should unroll 1-9 into 9 distinct episodes!
        assertEquals(9, episodes.size)
        for (i in 1..9) {
            val ep = episodes[i - 1]
            assertEquals(i, ep.episodeNumberInSeason)
            assertEquals(i, ep.absoluteEpisodeNumber)
            assertEquals("Episode $i", ep.title)
            assertTrue("Stream URL should contain anchor for ep $i", ep.streamUrl.contains("ep=$i"))
        }
    }

    @Test
    fun testConversionToChaptersAndRoomEntities() {
        val series = DramaSeries(
            title = "Moving",
            seasons = listOf(
                DramaSeason(
                    seasonNumber = 1,
                    seasonName = "Season 1",
                    episodes = listOf(
                        DramaEpisode("https://stream.com/moving-s1-e1", 1, 1, 1, "Episode 1", "https://stream.com/moving-s1-e1"),
                        DramaEpisode("https://stream.com/moving-s1-e2", 1, 2, 2, "Episode 2", "https://stream.com/moving-s1-e2")
                    )
                ),
                DramaSeason(
                    seasonNumber = 2,
                    seasonName = "Season 2",
                    episodes = listOf(
                        DramaEpisode("https://stream.com/moving-s2-e1", 2, 1, 3, "Episode 1", "https://stream.com/moving-s2-e1")
                    )
                )
            )
        )

        // Test Chapter UI models
        val chapters = DramaSeasonResolver.toChapters(series)
        assertEquals(3, chapters.size)
        assertEquals("S1 Episode 1", chapters[0].title)
        assertEquals("S1 Episode 2", chapters[1].title)
        assertEquals("S2 Episode 1", chapters[2].title)

        // Test Room Entity conversion with triple-index float encoding
        val entries = DramaSeasonResolver.toChapterEntries(
            series = series,
            profileId = 1L,
            sourceId = "viki",
            mangaId = "moving-id"
        )
        assertEquals(3, entries.size)
        assertEquals(1L, entries[0].profileId)
        assertEquals("viki", entries[0].sourceId)
        assertEquals("moving-id", entries[0].mangaId)
        assertEquals("S1 Episode 1", entries[0].name)
        assertEquals(1.001f, entries[0].number!!, 0.0001f) // S1 Ep 1 -> 1.001
        assertEquals(2.001f, entries[2].number!!, 0.0001f) // S2 Ep 1 -> 2.001
    }

    @Test
    fun testBaseDramaSourceIntegrationWithResolver() = runBlocking {
        val dramaHtml = """
            <html>
            <body>
                <div class="gmr-season" data-season="1">
                    <h3>Musim 1</h3>
                    <div class="gmr-listseries">
                        <a href="https://digitalepiphanies.org/vincenzo-ep-1/"><span class="num">Episode 1</span></a>
                        <a href="https://digitalepiphanies.org/vincenzo-ep-2/"><span class="num">Episode 2</span></a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val dramaSource = object : BaseDramaSource(context) {
            override val sourceId: String = "test-drama"
            override val sourceName: String = "Test Drama"
            override val baseUrl: String = "https://digitalepiphanies.org"
            override val contentType = ContentType.DRAMA

            override suspend fun fetchDocumentInternal(url: String): org.jsoup.nodes.Document {
                return Jsoup.parse(dramaHtml, baseUrl)
            }

            override fun getRecommendations(page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override fun search(query: String, page: Int) = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.models.ContentItem>())
            override suspend fun getDetail(itemId: String): com.example.data.models.ContentItem? = null
        }

        val chapters = dramaSource.getChapters("https://digitalepiphanies.org/vincenzo/").first()
        assertEquals(2, chapters.size)
        assertEquals("Episode 1", chapters[0].title)
        assertEquals("https://digitalepiphanies.org/vincenzo-ep-1/", chapters[0].url)
    }
}
