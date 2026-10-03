package com.example

import com.example.data.sources.manga.base.UniversalMangaDomExtractor
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OtakuHubApp::class)
class UniversalMangaDomExtractorTest {

    @Test
    fun testMultiAttributeFallbackPriorityChain() {
        // Priority 1: data-original takes priority over data-src, data-lazy-src, src
        val html1 = """
            <div class="page-break">
                <img src="https://cdn.example.com/src.jpg"
                     data-lazy-src="https://cdn.example.com/lazy.jpg"
                     data-src="https://cdn.example.com/data-src.jpg"
                     data-original="https://cdn.example.com/original-high-res.jpg" />
            </div>
        """.trimIndent()
        val doc1 = Jsoup.parse(html1, "https://example.com")
        val img1 = doc1.selectFirst("img")
        val result1 = UniversalMangaDomExtractor.extractImageUrl(img1, "https://example.com")
        assertEquals("https://cdn.example.com/original-high-res.jpg", result1)

        // Priority 2: data-src takes priority when data-original is missing
        val html2 = """
            <div class="page-break">
                <img src="https://cdn.example.com/src.jpg"
                     data-lazy-src="https://cdn.example.com/lazy.jpg"
                     data-src="https://cdn.example.com/data-src.jpg" />
            </div>
        """.trimIndent()
        val doc2 = Jsoup.parse(html2, "https://example.com")
        val img2 = doc2.selectFirst("img")
        val result2 = UniversalMangaDomExtractor.extractImageUrl(img2, "https://example.com")
        assertEquals("https://cdn.example.com/data-src.jpg", result2)

        // Priority 3: data-lazy-src (Jetpack/WP-Manga) takes priority when data-original and data-src are missing
        val html3 = """
            <div class="page-break">
                <img src="data:image/svg+xml;base64,PHN2Zz4="
                     data-lazy-src="https://cdn.example.com/wp-manga-page1.jpg" />
            </div>
        """.trimIndent()
        val doc3 = Jsoup.parse(html3, "https://example.com")
        val img3 = doc3.selectFirst("img")
        val result3 = UniversalMangaDomExtractor.extractImageUrl(img3, "https://example.com")
        assertEquals("https://cdn.example.com/wp-manga-page1.jpg", result3)

        // Priority 4: data-srcset takes priority when previous are missing or placeholder
        val html4 = """
            <div class="page-break">
                <img src="data:image/svg+xml;base64,PHN2Zz4="
                     data-srcset="https://cdn.example.com/page-small.jpg 400w, https://cdn.example.com/page-large.jpg 1200w" />
            </div>
        """.trimIndent()
        val doc4 = Jsoup.parse(html4, "https://example.com")
        val img4 = doc4.selectFirst("img")
        val result4 = UniversalMangaDomExtractor.extractImageUrl(img4, "https://example.com")
        assertEquals("https://cdn.example.com/page-large.jpg", result4)

        // Priority 5: src fallback
        val html5 = """
            <div class="page-break">
                <img src="https://cdn.example.com/standard-src.jpg" />
            </div>
        """.trimIndent()
        val doc5 = Jsoup.parse(html5, "https://example.com")
        val img5 = doc5.selectFirst("img")
        val result5 = UniversalMangaDomExtractor.extractImageUrl(img5, "https://example.com")
        assertEquals("https://cdn.example.com/standard-src.jpg", result5)
    }

    @Test
    fun testNoscriptFallbackExtraction() {
        // Jetpack lazy-loader wraps true img inside <noscript> while main img has blank base64
        val html = """
            <div id="readerarea">
                <div class="page-break">
                    <img src="data:image/svg+xml;base64,PHN2Zz4=" class="lazy-hidden" />
                    <noscript>
                        <img src="https://wp.example.com/uploads/2026/09/chapter-01.jpg" />
                    </noscript>
                </div>
                <div class="page-break">
                    <img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" class="lazy-hidden" />
                    <noscript>
                        <img data-src="https://wp.example.com/uploads/2026/09/chapter-02.jpg" />
                    </noscript>
                </div>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://wp.example.com")
        val pages = UniversalMangaDomExtractor.extractChapterPages(doc, "https://wp.example.com")

        assertEquals(2, pages.size)
        assertEquals("https://wp.example.com/uploads/2026/09/chapter-01.jpg", pages[0])
        assertEquals("https://wp.example.com/uploads/2026/09/chapter-02.jpg", pages[1])
    }

    @Test
    fun testBloggerHighResolutionTransformer() {
        // Path /s72-c/ thumbnail
        val thumb1 = "https://1.bp.blogspot.com/-abc/XYZ/s72-c/01.jpg"
        assertEquals("https://1.bp.blogspot.com/-abc/XYZ/s0/01.jpg", UniversalMangaDomExtractor.transformBloggerUrl(thumb1))

        // Path /w140-h200/ thumbnail
        val thumb2 = "https://2.bp.blogspot.com/-abc/XYZ/w140-h200/02.png"
        assertEquals("https://2.bp.blogspot.com/-abc/XYZ/s0/02.png", UniversalMangaDomExtractor.transformBloggerUrl(thumb2))

        // Path /s1600/ compressed
        val thumb3 = "https://3.bp.blogspot.com/-abc/XYZ/s1600/03.webp"
        assertEquals("https://3.bp.blogspot.com/-abc/XYZ/s0/03.webp", UniversalMangaDomExtractor.transformBloggerUrl(thumb3))

        // Param =w140-h200 (modern blogger googleusercontent)
        val param1 = "https://blogger.googleusercontent.com/img/a/AVvXsEj=w140-h200"
        assertEquals("https://blogger.googleusercontent.com/img/a/AVvXsEj=s0", UniversalMangaDomExtractor.transformBloggerUrl(param1))

        // Param =s72-c
        val param2 = "https://blogger.googleusercontent.com/img/a/AVvXsEj=s72-c"
        assertEquals("https://blogger.googleusercontent.com/img/a/AVvXsEj=s0", UniversalMangaDomExtractor.transformBloggerUrl(param2))

        // Non-blogger URLs should remain untouched
        val other = "https://cdn.mangastream.com/images/chapter1/01.jpg"
        assertEquals(other, UniversalMangaDomExtractor.transformBloggerUrl(other))
    }

    @Test
    fun testStrictSanitizationFilter() {
        // Discard 1x1 SVGs and base64
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("data:image/svg+xml;base64,PHN2Zz4="))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"))

        // Discard blank/pixel placeholders
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/assets/blank.gif"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/assets/pixel.gif"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/assets/1x1.png"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/assets/transparent.png"))

        // Discard ads, banners, and sponsor watermarks
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/ads/banner-sponsor.jpg"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/images/iklan-judi.jpg"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/promo/trakteer-donasi.png"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/badges/join-discord.png"))
        assertFalse(UniversalMangaDomExtractor.isValidMangaImage("https://example.com/static/patreon-badge.jpg"))

        // Valid manga page URLs must be allowed
        assertTrue(UniversalMangaDomExtractor.isValidMangaImage("https://cdn.example.com/manga/solo-leveling/chapter-100/01.webp"))
        assertTrue(UniversalMangaDomExtractor.isValidMangaImage("https://1.bp.blogspot.com/-abc/XYZ/s0/01.jpg"))
    }

    @Test
    fun testExtractChapterPagesMangaStreamTheme() {
        val html = """
            <html>
                <body>
                    <div id="readerarea">
                        <img data-lazy-src="https://cdn.themesia.com/ch1/01.jpg" src="data:image/svg+xml;base64,..." />
                        <img data-lazy-src="https://cdn.themesia.com/ch1/02.jpg" src="data:image/svg+xml;base64,..." />
                        <img src="https://cdn.themesia.com/ads/banner.jpg" />
                        <img data-lazy-src="https://cdn.themesia.com/ch1/03.jpg" src="data:image/svg+xml;base64,..." />
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://themesia.com")
        val pages = UniversalMangaDomExtractor.extractChapterPages(doc, "https://themesia.com")

        assertEquals(3, pages.size)
        assertEquals("https://cdn.themesia.com/ch1/01.jpg", pages[0])
        assertEquals("https://cdn.themesia.com/ch1/02.jpg", pages[1])
        assertEquals("https://cdn.themesia.com/ch1/03.jpg", pages[2])
    }

    @Test
    fun testExtractChapterPagesBloggerThemeWithRegexUpgrades() {
        val html = """
            <html>
                <body>
                    <div class="separator">
                        <img src="https://1.bp.blogspot.com/-abc/XYZ/s72-c/page1.jpg" />
                    </div>
                    <div class="separator">
                        <img src="https://2.bp.blogspot.com/-abc/XYZ/w140-h200/page2.jpg" />
                    </div>
                    <div class="separator">
                        <img src="https://blogger.googleusercontent.com/img/a/AVvXsEj=w140-h200" />
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://zhuximanhwa.blogspot.com")
        val pages = UniversalMangaDomExtractor.extractChapterPages(doc, "https://zhuximanhwa.blogspot.com")

        assertEquals(3, pages.size)
        assertEquals("https://1.bp.blogspot.com/-abc/XYZ/s0/page1.jpg", pages[0])
        assertEquals("https://2.bp.blogspot.com/-abc/XYZ/s0/page2.jpg", pages[1])
        assertEquals("https://blogger.googleusercontent.com/img/a/AVvXsEj=s0", pages[2])
    }

    @Test
    fun testExtractChapterPagesScriptFallback() {
        val html = """
            <html>
                <body>
                    <div id="readerarea"></div>
                    <script type="text/javascript">
                        ts_reader.run({
                            "prevUrl": "/ch-1/",
                            "nextUrl": "/ch-3/",
                            "images": [
                                "https:\/\/cdn.script.com\/manga\/01.jpg",
                                "https:\/\/cdn.script.com\/manga\/02.jpg",
                                "https:\/\/cdn.script.com\/manga\/03.jpg"
                            ]
                        });
                    </script>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://script.com")
        val pages = UniversalMangaDomExtractor.extractChapterPages(doc, "https://script.com")

        assertEquals(3, pages.size)
        assertEquals("https://cdn.script.com/manga/01.jpg", pages[0])
        assertEquals("https://cdn.script.com/manga/02.jpg", pages[1])
        assertEquals("https://cdn.script.com/manga/03.jpg", pages[2])
    }
}
