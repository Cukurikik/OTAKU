package com.example.data.sources.mangastream

import kotlinx.coroutines.CancellationException
import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ContentSource
import com.example.data.sources.WebViewFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

open class MangaStreamSource(
    private val context: Context,
    override val sourceId: String,
    override val sourceName: String,
    protected val baseUrl: String
) : ContentSource {
    protected val fetcher = WebViewFetcher(context)

    protected fun parseMangaList(doc: org.jsoup.nodes.Document): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        val elements = doc.select(".bsx a, .bs a")
        
        for (i in 0 until elements.size) {
            val el = elements[i]
            val url = el.attr("href")
            if (url.isNullOrEmpty()) continue
            
            val _titleAttr = el.attr("title")
            val title = if (_titleAttr.isEmpty()) el.text() else _titleAttr
            var img = el.select("img").attr("src")
            if (img.isEmpty() || img.contains("data:image")) {
                img = el.select("img").attr("data-src")
            }
            if (img.isEmpty() || img.contains("data:image")) {
                img = el.select("img").attr("data-lazy-src")
            }
            
            if (title.isNotEmpty()) {
                items.add(ContentItem(
                    id = url,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC, sourceId = sourceId,
                    description = "",
                    genres = listOf()
                ))
            }
        }
        return items.distinctBy { it.id }
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page <= 1) {
                "$baseUrl/manga/?order=update"
            } else {
                "$baseUrl/manga/?page=$page&order=update"
            }
            val doc = fetcher.fetchHtml(url)
            val items = parseMangaList(doc)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fallback to baseUrl
            try {
                val doc = fetcher.fetchHtml(baseUrl)
                emit(parseMangaList(doc))
            } catch (ex: Exception) {
                emit(emptyList())
            }
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page <= 1) {
                "$baseUrl/?s=$encoded"
            } else {
                "$baseUrl/page/$page/?s=$encoded"
            }
            val doc = fetcher.fetchHtml(url)
            val items = parseMangaList(doc)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetcher.fetchHtml(itemId)
            val title = doc.select(".infox h1, .entry-title").text()
            var img = doc.select(".thumb img, .infox img").attr("src")
            if (img.isEmpty() || img.contains("data:image")) {
                img = doc.select(".thumb img, .infox img").attr("data-src")
            }
            if (img.isEmpty() || img.contains("data:image")) {
                img = doc.select(".thumb img, .infox img").attr("data-lazy-src")
            }
            val description = doc.select(".entry-content, .infox .desc").text()
            
            ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = img,
                type = ContentType.COMIC, sourceId = sourceId,
                description = description,
                genres = listOf()
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetcher.fetchHtml(itemId)
            val chapters = mutableListOf<Chapter>()
            val elements = doc.select("#chapterlist li")
            
            for (i in 0 until elements.size) {
                val el = elements[i]
                val a = el.select("a").first() ?: continue
                val url = a.attr("href")
                val num = el.select(".chapternum").text()
                val date = el.select(".chapterdate").text()
                
                chapters.add(Chapter(
                    id = url,
                    title = if (num.isEmpty()) "Chapter" else num,
                    url = url,
                    date = date
                ))
            }
            emit(chapters)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            emit(emptyList())
        }
    }

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        try {
            val doc = fetcher.fetchHtml(chapterId)
            val pages = mutableListOf<Page>()
            val elements = doc.select("#readerarea img")
            
            for (i in 0 until elements.size) {
                val el = elements[i]
                var src = el.attr("src")
                if (src.isEmpty() || src.contains("data:image")) {
                    src = el.attr("data-src")
                }
                if (src.isEmpty() || src.contains("data:image")) {
                    src = el.attr("data-lazy-src")
                }
                if (src.isNotEmpty()) {
                    pages.add(Page(src))
                }
            }
            emit(pages)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.printStackTrace()
            emit(emptyList())
        }
    }

}
