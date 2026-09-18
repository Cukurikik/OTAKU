package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ParsedHttpSource : HttpSource() {

    protected fun Response.asJsoup(): Document {
        return Jsoup.parse(body?.string() ?: "", request.url.toString())
    }

    // Abstract methods matching standard Tachiyomi ParsedHttpSource
    abstract fun popularMangaSelector(): String
    abstract fun popularMangaFromElement(element: Element): SManga
    abstract fun popularMangaNextPageSelector(): String?

    abstract fun latestUpdatesSelector(): String
    abstract fun latestUpdatesFromElement(element: Element): SManga
    abstract fun latestUpdatesNextPageSelector(): String?

    abstract fun searchMangaSelector(): String
    abstract fun searchMangaFromElement(element: Element): SManga
    abstract fun searchMangaNextPageSelector(): String?

    abstract fun mangaDetailsParse(document: Document): SManga

    abstract fun chapterListSelector(): String
    abstract fun chapterFromElement(element: Element): SChapter

    abstract fun pageListParse(document: Document): List<eu.kanade.tachiyomi.source.model.Page>

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).mapNotNull { element ->
            try {
                popularMangaFromElement(element)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first() != null
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).mapNotNull { element ->
            try {
                latestUpdatesFromElement(element)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first() != null
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).mapNotNull { element ->
            try {
                searchMangaFromElement(element)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first() != null
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return mangaDetailsParse(response.asJsoup())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapNotNull { element ->
            try {
                chapterFromElement(element)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    override fun pageListParse(response: Response): List<eu.kanade.tachiyomi.source.model.Page> {
        return pageListParse(response.asJsoup())
    }
}
