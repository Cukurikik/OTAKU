package com.example

import com.example.data.sources.anime.base.AnimeDomExtractor
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class AnimeDomExtractorTest {

    @Test
    fun testExtractCardsWithLazyLoadedImages() {
        val html = """
            <html>
                <body>
                    <div class="listupd">
                        <article class="bs">
                            <div class="bsx">
                                <a href="/anime/naruto-shippuden/">
                                    <img data-src="https://cdn.example.com/naruto.jpg" src="data:image/svg+xml;base64,PHN2Zz4=" />
                                    <div class="tt">Naruto Shippuden</div>
                                </a>
                            </div>
                        </article>
                        <article class="bs">
                            <div class="bsx">
                                <a href="/anime/one-piece/">
                                    <div class="set-bg" data-setbg="https://cdn.example.com/onepiece.jpg"></div>
                                    <h2 class="title">One Piece</h2>
                                </a>
                            </div>
                        </article>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")
        val items = AnimeDomExtractor.extractCards(doc, "test_source", "Test Source", "https://example.com")

        assertEquals(2, items.size)
        assertEquals("https://example.com/anime/naruto-shippuden/", items[0].id)
        assertEquals("Naruto Shippuden", items[0].title)
        assertEquals("https://cdn.example.com/naruto.jpg", items[0].imageUrl)

        assertEquals("https://example.com/anime/one-piece/", items[1].id)
        assertEquals("One Piece", items[1].title)
        assertEquals("https://cdn.example.com/onepiece.jpg", items[1].imageUrl)
    }

    @Test
    fun testExtractDetailAndEpisodes() {
        val html = """
            <html>
                <body>
                    <h1 class="entry-title">Bleach: Thousand-Year Blood War</h1>
                    <div class="thumb">
                        <img data-src="https://cdn.example.com/bleach.jpg" src="" />
                    </div>
                    <div class="entry-content">
                        <p>The peace is suddenly broken when warning sirens blare through Soul Society.</p>
                    </div>
                    <div class="genxed">
                        <a href="/genres/action/">Action</a>
                        <a href="/genres/supernatural/">Supernatural</a>
                    </div>
                    <ul class="episodelist">
                        <li>
                            <a href="/bleach-episode-01/">
                                <span class="epl-num">1</span>
                                <span class="epl-title">The Blood Warfare</span>
                                <span class="epl-date">Oct 11, 2022</span>
                            </a>
                        </li>
                        <li>
                            <a href="/bleach-episode-02/">
                                <span class="epl-num">2</span>
                                <span class="epl-title">Foundation Stones</span>
                                <span class="epl-date">Oct 18, 2022</span>
                            </a>
                        </li>
                    </ul>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")
        val detail = AnimeDomExtractor.extractDetail(doc, "test_source", "Test Source", "https://example.com/bleach", "https://example.com")

        assertNotNull(detail)
        assertEquals("Bleach: Thousand-Year Blood War", detail!!.title)
        assertEquals("https://cdn.example.com/bleach.jpg", detail.imageUrl)
        assertTrue(detail.description.contains("peace is suddenly broken"))
        assertTrue(detail.genres.contains("Action"))
        assertTrue(detail.genres.contains("Supernatural"))

        val episodes = AnimeDomExtractor.extractEpisodes(doc, "https://example.com")
        assertEquals(2, episodes.size)
        assertEquals("https://example.com/bleach-episode-01/", episodes[0].url)
        assertEquals("1 - The Blood Warfare", episodes[0].title)
        assertEquals("https://example.com/bleach-episode-02/", episodes[1].url)
    }

    @Test
    fun testExtractAllServersAndEmbedsWithBase64Option() = kotlinx.coroutines.runBlocking {
        // Base64 of <iframe src="https://stream.player.net/v/xyz123"></iframe>
        val base64Iframe = "PGlmcmFtZSBzcmM9Imh0dHBzOi8vc3RyZWFtLnBsYXllci5uZXQvdi94eXoxMjMiPjwvaWZyYW1lPg=="
        val html = """
            <html>
                <body>
                    <select class="mirror">
                        <option value="">Pilih Server</option>
                        <option value="$base64Iframe">Server VIP 1080p</option>
                        <option value="https://player2.example.com/embed/ep1">Server Backup 720p</option>
                    </select>
                    <div class="player-area">
                        <iframe data-src="https://embed.primary.org/watch?v=123"></iframe>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")
        val servers = AnimeDomExtractor.extractAllServersAndEmbeds(doc, "https://example.com", "https://example.com/ep1")

        assertTrue(servers.isNotEmpty())
        val urls = servers.map { it.second }
        assertTrue(urls.contains("https://stream.player.net/v/xyz123"))
        assertTrue(urls.contains("https://player2.example.com/embed/ep1"))
        assertTrue(urls.contains("https://embed.primary.org/watch?v=123"))
    }
}
