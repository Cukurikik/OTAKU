package com.example.data.sources.manga.specific

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ContentSource
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.WebViewFetcher
import com.example.data.sources.base.BaseMangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.nodes.Document

class AsuraScansScraper(context: Context) : BaseMangaSource(context) {
    override val sourceId: String = "asurascans"
    override val sourceName: String = "AsuraScans"
    override val baseUrl = "https://asurascans.com"

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "${currentBaseUrl()}/comics?page=$page&name=$q"
            val doc = fetchDocument(url) 
            emit(parseList(doc))
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = "${currentBaseUrl()}/comics?page=$page&order=update"
            val doc = fetchDocument(url)
            emit(parseList(doc))
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    private fun parseList(doc: Document): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        // Astro theme selectors
        doc.select("a[href^=/comics/], a[href^=/series/]").forEach { el ->
            val href = el.attr("href")
            if (href.isBlank()) return@forEach
            val titleEl = el.selectFirst(".text-sm, h3, .title, [class*=title]")
            val title = titleEl?.text()?.trim() ?: el.text().trim()
            if (title.isBlank()) return@forEach
            
            val img = com.example.data.sources.manga.base.UniversalMangaDomExtractor.extractImageUrl(el, currentBaseUrl())
            
            val absoluteUrl = if (href.startsWith("http")) href else "${currentBaseUrl()}$href"
            
            items.add(
                ContentItem(
                    id = absoluteUrl,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = "",
                    genres = emptyList()
                )
            )
        }
        return items.distinctBy { it.id }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst("h1, .title, [class*=title]")?.text()?.trim() ?: "Unknown"
            val img = com.example.data.sources.manga.base.UniversalMangaDomExtractor.extractImageUrl(
                doc.selectFirst("img[src*=cover], img[alt*=cover], img") ?: doc,
                currentBaseUrl()
            )
            val desc = doc.selectFirst(".description, .synopsis, [class*=desc]")?.text()?.trim() ?: ""
            
            ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = img,
                type = ContentType.COMIC,
                sourceId = sourceId,
                description = desc,
                genres = emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val chapters = mutableListOf<Chapter>()
            // Asura chapter list
            doc.select("a[href*=/chapter/], a[href*=-chapter-]").forEach { el ->
                val href = el.attr("href")
                val absoluteUrl = if (href.startsWith("http")) href else "${currentBaseUrl()}$href"
                val title = el.selectFirst(".text-sm, h3, [class*=title]")?.text()?.trim() ?: el.text().trim()
                
                chapters.add(
                    Chapter(
                        id = absoluteUrl,
                        title = title,
                        url = absoluteUrl,
                        date = ""
                    )
                )
            }
            emit(chapters.distinctBy { it.id }.filter { it.title.isNotBlank() })
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override suspend fun getPagesList(chapterId: String): List<String> {
        try {
            // Because AsuraScans protects pages, we inject JS to extract them
            val extractorScript = """
                (function() {
                    var urls = [];
                    var imgs = document.querySelectorAll('img[src*="asura"], img[alt*="chapter"]');
                    for (var i = 0; i < imgs.length; i++) {
                        var src = imgs[i].src;
                        if (src && !src.startsWith('data:image')) {
                            urls.push(src);
                        }
                    }
                    if (urls.length === 0) {
                        // Fallback generic
                        var allImgs = document.querySelectorAll('img');
                        for (var i = 0; i < allImgs.length; i++) {
                            var src = allImgs[i].src || allImgs[i].getAttribute('data-src');
                            if (src && src.includes('wp-content/uploads') && !src.startsWith('data:image')) {
                                urls.push(src);
                            }
                        }
                    }
                    return JSON.stringify(urls);
                })();
            """.trimIndent()
            
            val jsonResult = fetcher.executeJsExtractor(chapterId, extractorScript, 6000L)
            if (jsonResult != null && jsonResult != "null" && jsonResult.isNotBlank()) {
                val array = org.json.JSONArray(jsonResult)
                val pages = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val src = array.getString(i).replace("\\/", "/")
                    pages.add(src)
                }
                if (pages.isNotEmpty()) {
                    return pages.distinct()
                }
            }
            throw java.io.IOException("Tidak dapat mengekstrak halaman gambar dari chapter $chapterId")
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
