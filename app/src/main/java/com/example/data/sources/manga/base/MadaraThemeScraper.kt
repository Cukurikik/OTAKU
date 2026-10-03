package com.example.data.sources.manga.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.base.BaseMangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class MadaraThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String,
    private val mangaPath: String = "manga"
) : BaseMangaSource(context) {

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
            val doc = fetchDocument(url)
            val items = doc.select(".page-item-detail, .item-summary, .post-title a").mapNotNull {
                var linkEl = it.selectFirst("a")
                if (it.tagName() == "a") linkEl = it
                
                val link = linkEl?.attr("href")?.takeIf { h -> h.contains("/$mangaPath/") } ?: return@mapNotNull null
                
                val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
                val title = it.selectFirst("h3 a, .post-title a")?.text()?.trim() ?: linkEl.text().trim()
                if (title.isBlank()) return@mapNotNull null

                ContentItem(
                    id = link,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId
                )
            }.distinctBy { it.id }
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page == 1) "$baseUrl/?s=$q&post_type=wp-manga" else "$baseUrl/page/$page/?s=$q&post_type=wp-manga"
            val doc = fetchDocument(url)
            val items = doc.select(".c-tabs-item__content, .search-wrap .tab-item").mapNotNull {
                val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
                val title = it.selectFirst("h3, .post-title, .title")?.text()?.trim() ?: "Unknown"

                ContentItem(
                    id = link,
                    title = title,
                    source = sourceName,
                    imageUrl = img,
                    type = ContentType.COMIC,
                    sourceId = sourceId
                )
            }
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst(".post-title h1, .post-title h3, h1.entry-title")?.text()?.trim()?.replace(Regex("HOT$"), "")?.trim() ?: "Unknown"
            val img = UniversalMangaDomExtractor.extractImageUrl(doc.selectFirst(".summary_image img, .featured-image img") ?: doc, baseUrl)
            val desc = doc.selectFirst(".summary__content, .description-summary p, .manga-excerpt")?.text()?.trim() ?: ""
            val genres = doc.select(".genres-content a, .genre-links a").map { it.text().trim() }

            return ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = img,
                description = desc,
                genres = genres,
                type = ContentType.COMIC,
                sourceId = sourceId
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            var chaptersElements = doc.select(".wp-manga-chapter a, .chapter-link a, li.listing-item a")
            
            if (chaptersElements.isEmpty()) {
                val ajaxUrl = if (itemId.endsWith("/")) "${itemId}ajax/chapters/" else "$itemId/ajax/chapters/"
                try {
                    val ajaxRequest = okhttp3.Request.Builder()
                        .url(ajaxUrl)
                        .post(okhttp3.FormBody.Builder().build())
                        .build()
                    val ajaxResponse = com.example.data.NetworkModule.okHttpClient.newCall(ajaxRequest).execute()
                    if (ajaxResponse.isSuccessful) {
                        val ajaxBody = ajaxResponse.body?.string() ?: ""
                        val ajaxDoc = org.jsoup.Jsoup.parseBodyFragment(ajaxBody)
                        chaptersElements = ajaxDoc.select(".wp-manga-chapter a, .chapter-link a, li.listing-item a")
                    }
                } catch (e: Exception) {}
            }

            val chapters = chaptersElements.mapNotNull {
                val href = it.attr("href")
                if (href.isBlank()) return@mapNotNull null
                val title = it.text().trim().ifBlank { "Chapter" }
                val date = it.parent()?.parent()?.selectFirst(".chapter-release-date i, .chapter-release-date a")?.text()?.trim() ?: ""
                Chapter(id = href, title = title, url = href, date = date)
            }.distinctBy { it.id }
            emit(chapters)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: com.example.data.sources.CloudflareException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override suspend fun getPagesList(chapterId: String): List<String> {
        try {
            val doc = fetchDocument(chapterId)
            return UniversalMangaDomExtractor.extractChapterPages(doc, chapterId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            return emptyList()
        }
    }
}
