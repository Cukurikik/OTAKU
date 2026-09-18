package com.example.data.sources.tachiyomi.multisrc.mangastream

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

open class MangaStream(
    override val sourceName: String,
    override val baseUrl: String,
    override val sourceId: String
) : ParsedHttpSource() {

    override val lang: String = "id"

    // Common MangaStream Selectors
    override fun popularMangaSelector() = ".bsx a, .bs a, .cpp a"
    override fun popularMangaNextPageSelector(): String? = ".pagination .next, .hpage .r"
    override fun searchMangaSelector() = ".bsx a, .bs a, .cpp a"
    override fun searchMangaNextPageSelector(): String? = ".pagination .next, .hpage .r"
    override fun chapterListSelector() = "#chapterlist li"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=popular")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.attr("href")
        manga.title = element.attr("title").ifEmpty { element.text() }
        
        val imgEl = element.select("img").first()
        var img = imgEl?.attr("src") ?: ""
        if (img.isEmpty() || img.contains("data:image")) {
            img = imgEl?.attr("data-src") ?: ""
        }
        if (img.isEmpty() || img.contains("data:image")) {
            img = imgEl?.attr("data-lazy-src") ?: ""
        }
        manga.thumbnail_url = img
        manga.initialized = true
        return manga
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query")
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(manga.url)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".infox h1, .entry-title").text()
        
        val imgEl = document.select(".thumb img, .infox img").first()
        var img = imgEl?.attr("src") ?: ""
        if (img.isEmpty() || img.contains("data:image")) {
            img = imgEl?.attr("data-src") ?: ""
        }
        if (img.isEmpty() || img.contains("data:image")) {
            img = imgEl?.attr("data-lazy-src") ?: ""
        }
        manga.thumbnail_url = img
        manga.description = document.select(".entry-content, .infox .desc").text()
        manga.initialized = true
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val a = element.select("a").first()!!
        chapter.url = a.attr("href")
        val num = element.select(".chapternum").text()
        chapter.name = num.ifEmpty { "Chapter" }
        // Attempt parsing date
        val date = element.select(".chapterdate").text()
        chapter.scanlator = date
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url)
    }

    override fun pageListParse(document: Document): List<Page> {
        val elements = document.select("#readerarea img")
        val pages = mutableListOf<Page>()

        elements.forEachIndexed { index, el ->
            var src = el.attr("src")
            if (src.isEmpty() || src.contains("data:image")) {
                src = el.attr("data-src")
            }
            if (src.isEmpty() || src.contains("data:image")) {
                src = el.attr("data-lazy-src")
            }
            if (src.isNotEmpty()) {
                pages.add(Page(index, imageUrl = src))
            }
        }
        return pages
    }
}
