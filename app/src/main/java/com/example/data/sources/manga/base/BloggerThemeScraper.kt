package com.example.data.sources.manga.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.base.BaseMangaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

abstract class BloggerThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : BaseMangaSource(context) {

    private fun getDomain(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            baseUrl.trimEnd('/')
        }
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        val items = mutableListOf<ContentItem>()
        val domain = baseUrl.trimEnd('/')

        // 1. Try Blogger JSON Feed first
        try {
            val startIndex = (page - 1) * 20 + 1
            val feedUrl = "$domain/feeds/posts/default?alt=json&start-index=$startIndex&max-results=20"
            val doc = fetchDocument(feedUrl)
            val jsonStr = doc.body().text()
            if (jsonStr.startsWith("{")) {
                val root = JSONObject(jsonStr)
                val entries = root.optJSONObject("feed")?.optJSONArray("entry")
                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val entry = entries.getJSONObject(i)
                        val title = entry.optJSONObject("title")?.optString("\$t")?.trim() ?: "Unknown"
                        val links = entry.optJSONArray("link")
                        var alternateLink = ""
                        if (links != null) {
                            for (j in 0 until links.length()) {
                                val linkObj = links.getJSONObject(j)
                                if (linkObj.optString("rel") == "alternate") {
                                    alternateLink = linkObj.optString("href")
                                    break
                                }
                            }
                        }
                        val mediaThumb = entry.optJSONObject("media\$thumbnail")?.optString("url") ?: ""
                        val contentHtml = entry.optJSONObject("content")?.optString("\$t") ?: ""
                        val imgMatch = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']").matcher(contentHtml)
                        val contentImg = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""
                        val finalImg = UniversalMangaDomExtractor.transformBloggerUrl(mediaThumb.ifBlank { contentImg })

                        if (alternateLink.isNotBlank()) {
                            items.add(
                                ContentItem(
                                    id = alternateLink,
                                    title = title,
                                    source = sourceName,
                                    imageUrl = finalImg,
                                    type = ContentType.COMIC,
                                    sourceId = sourceId
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to HTML DOM scraping
        if (items.isEmpty()) {
            try {
                val url = if (page == 1) baseUrl else "$baseUrl/search?max-results=20"
                val doc = fetchDocument(url)
                val elements = doc.select(".post-outer, .post, article, .hentry, .bsx, .item, .listupd .bs, .grid-posts .post, .blog-post, .hfeed > div, .b-img")
                for (it in elements) {
                    val link = it.selectFirst("a")?.attr("href") ?: continue
                    val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
                    val title = it.selectFirst(".post-title, h3, h2, .entry-title, .title, a[title]")?.text()?.trim() 
                        ?: it.selectFirst("a")?.attr("title")?.trim() 
                        ?: "Unknown"
                    if (link.isNotBlank() && title != "Unknown") {
                        items.add(
                            ContentItem(
                                id = link,
                                title = title,
                                source = sourceName,
                                imageUrl = img,
                                type = ContentType.COMIC,
                                sourceId = sourceId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                SourceStatusBus.reportFromException(sourceId, sourceName, e)
                throw e
            }
        }

        val distinctItems = items.distinctBy { it.id }
        if (distinctItems.isEmpty()) {
            throw java.io.IOException("Tidak ada konten ditemukan untuk sumber $sourceName")
        }
        emit(distinctItems)
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        val items = mutableListOf<ContentItem>()
        val domain = baseUrl.trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        // 1. Try Blogger JSON search feed
        try {
            val startIndex = (page - 1) * 20 + 1
            val feedUrl = "$domain/feeds/posts/default?q=$encodedQuery&alt=json&start-index=$startIndex&max-results=20"
            val doc = fetchDocument(feedUrl)
            val jsonStr = doc.body().text()
            if (jsonStr.startsWith("{")) {
                val root = JSONObject(jsonStr)
                val entries = root.optJSONObject("feed")?.optJSONArray("entry")
                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val entry = entries.getJSONObject(i)
                        val title = entry.optJSONObject("title")?.optString("\$t")?.trim() ?: "Unknown"
                        val links = entry.optJSONArray("link")
                        var alternateLink = ""
                        if (links != null) {
                            for (j in 0 until links.length()) {
                                val linkObj = links.getJSONObject(j)
                                if (linkObj.optString("rel") == "alternate") {
                                    alternateLink = linkObj.optString("href")
                                    break
                                }
                            }
                        }
                        val mediaThumb = entry.optJSONObject("media\$thumbnail")?.optString("url") ?: ""
                        val contentHtml = entry.optJSONObject("content")?.optString("\$t") ?: ""
                        val imgMatch = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']").matcher(contentHtml)
                        val contentImg = if (imgMatch.find()) imgMatch.group(1) ?: "" else ""
                        val finalImg = UniversalMangaDomExtractor.transformBloggerUrl(mediaThumb.ifBlank { contentImg })

                        if (alternateLink.isNotBlank()) {
                            items.add(
                                ContentItem(
                                    id = alternateLink,
                                    title = title,
                                    source = sourceName,
                                    imageUrl = finalImg,
                                    type = ContentType.COMIC,
                                    sourceId = sourceId
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to HTML Search
        if (items.isEmpty()) {
            try {
                val doc = fetchDocument("$baseUrl/search?q=$encodedQuery")
                val elements = doc.select(".post-outer, .post, article, .hentry, .bsx, .item, .listupd .bs, .grid-posts .post, .blog-post, .hfeed > div")
                for (it in elements) {
                    val link = it.selectFirst("a")?.attr("href") ?: continue
                    val img = UniversalMangaDomExtractor.extractImageUrl(it, baseUrl)
                    val title = it.selectFirst(".post-title, h3, h2, .entry-title, .title")?.text()?.trim() ?: "Unknown"
                    items.add(
                        ContentItem(
                            id = link,
                            title = title,
                            source = sourceName,
                            imageUrl = img,
                            type = ContentType.COMIC,
                            sourceId = sourceId
                        )
                    )
                }
            } catch (e: Exception) {
                SourceStatusBus.reportFromException(sourceId, sourceName, e)
                throw e
            }
        }

        emit(items.distinctBy { it.id })
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst("h1.post-title, h1.entry-title, h1, .post-title, .entry-title, h2.title, .title")?.text()?.trim() 
                ?: doc.title().replace(" - Lightsaber Komik", "").replace(" - ZhuxiManhwa", "").trim()
            val img = UniversalMangaDomExtractor.extractImageUrl(
                doc.selectFirst("meta[property=og:image]") ?: doc.selectFirst(".post-body img, .separator img, .reader-area img, .entry-content img, .b-img img, .post img") ?: doc,
                baseUrl
            )
            val desc = doc.selectFirst(".post-body p, #synopsis, .synopsis, .description, .post-body, .entry-content, .post-snippet")?.text()?.trim() ?: ""
            val genres = doc.select(".labels a, .tags a, .post-labels a, a[rel=tag], a[href*='/search/label/']").mapNotNull {
                val t = it.text().trim()
                if (t.length > 1 && !t.equals("Series", true) && !t.equals("Home", true) && !t.equals("Search", true)) t else null
            }.distinct()

            return ContentItem(
                id = itemId,
                title = title.ifBlank { "Manga Detail" },
                source = sourceName,
                imageUrl = img,
                description = desc,
                genres = genres,
                type = ContentType.COMIC,
                sourceId = sourceId
            )
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val chapters = mutableListOf<Chapter>()

            // 1. Direct DOM chapter link search
            val domLinks = doc.select(".post-body a:contains(Chapter), .post-body a:contains(Ch), .chapter-list a, .list-chapter a, #cl_list a, #listItem a, .eplister a, .clist a, #cl a, .chapters a, #latest a")
            for (it in domLinks) {
                val href = it.attr("href")
                if (href.isNotBlank() && href != itemId) {
                    val title = it.text().trim().ifBlank { "Chapter" }
                    chapters.add(Chapter(id = href, title = title, url = href, date = ""))
                }
            }

            // 2. If DOM links are empty, extract dynamic Blogger label / feed
            if (chapters.isEmpty()) {
                val html = doc.html()
                val domain = getDomain(itemId)
                
                val labels = mutableListOf<String>()
                
                // Check feed script matches like /feeds/posts/default/-/label
                val feedMatcher = Pattern.compile("/feeds/posts/default/-/([^?'\"&]+)").matcher(html)
                while (feedMatcher.find()) {
                    val raw = feedMatcher.group(1)?.trim() ?: ""
                    val decoded = java.net.URLDecoder.decode(raw, "UTF-8").trim()
                    if (decoded.isNotBlank() && !isIgnoredLabel(decoded)) {
                        labels.add(decoded)
                    }
                }

                // Check label = '...' in script blocks
                val scriptLabelMatcher = Pattern.compile("label\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html)
                while (scriptLabelMatcher.find()) {
                    val raw = scriptLabelMatcher.group(1)?.trim() ?: ""
                    val decoded = java.net.URLDecoder.decode(raw, "UTF-8").trim()
                    if (decoded.isNotBlank() && !isIgnoredLabel(decoded)) {
                        labels.add(decoded)
                    }
                }

                // Check labels from metadata or title
                val pageTitle = doc.selectFirst("h1.post-title, h1.entry-title, h1, .title")?.text()?.trim() ?: ""
                if (pageTitle.isNotBlank()) {
                    labels.add(pageTitle)
                }

                for (label in labels.distinct()) {
                    try {
                        val encodedLabel = URLEncoder.encode(label, "UTF-8")
                        val feedUrl = "$domain/feeds/posts/default/-/$encodedLabel?alt=json&max-results=500"
                        val feedDoc = fetchDocument(feedUrl)
                        val jsonStr = feedDoc.body().text()
                        if (jsonStr.startsWith("{")) {
                            val root = JSONObject(jsonStr)
                            val entries = root.optJSONObject("feed")?.optJSONArray("entry")
                            if (entries != null && entries.length() > 0) {
                                for (i in 0 until entries.length()) {
                                    val entry = entries.getJSONObject(i)
                                    val title = entry.optJSONObject("title")?.optString("\$t")?.trim() ?: "Chapter"
                                    val published = entry.optJSONObject("published")?.optString("\$t")?.take(10) ?: ""
                                    val links = entry.optJSONArray("link")
                                    var altLink = ""
                                    if (links != null) {
                                        for (j in 0 until links.length()) {
                                            val linkObj = links.getJSONObject(j)
                                            if (linkObj.optString("rel") == "alternate") {
                                                altLink = linkObj.optString("href")
                                                break
                                            }
                                        }
                                    }
                                    if (altLink.isNotBlank() && altLink != itemId) {
                                        chapters.add(Chapter(id = altLink, title = title, url = altLink, date = published))
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (chapters.isNotEmpty()) break
                }
            }

            if (chapters.isEmpty()) {
                throw java.io.IOException("Tidak ada chapter ditemukan untuk komik ini pada $sourceName")
            }
            emit(chapters.distinctBy { it.id })
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    private fun isIgnoredLabel(label: String): Boolean {
        val lower = label.lowercase()
        return lower in listOf(
            "series", "manga", "manhwa", "manhua", "ongoing", "completed",
            "cari blog ini", "berbagi", "dapatkan link", "bagikan ke facebook",
            "email", "home", "+t+", "'+t+'"
        )
    }

    override suspend fun getPagesList(chapterId: String): List<String> {
        try {
            val doc = fetchDocument(chapterId)
            val pages = UniversalMangaDomExtractor.extractChapterPages(doc, chapterId)

            if (pages.isEmpty()) {
                throw java.io.IOException("Tidak ditemukan halaman gambar pada chapter $chapterId")
            }

            return pages
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    private fun isValidMangaImage(src: String): Boolean {
        val lower = src.lowercase()
        val isNotIcon = !lower.contains("avatar") &&
                !lower.contains("icon") &&
                !lower.contains("logo") &&
                !lower.contains("favicon") &&
                !lower.contains("widgets") &&
                !lower.contains("blank.gif") &&
                !lower.contains("emoji") &&
                !lower.contains("profile")
        val isAllowedHost = lower.contains("blogger.googleusercontent.com") ||
                lower.contains("bp.blogspot.com") ||
                lower.contains(".jpg") ||
                lower.contains(".jpeg") ||
                lower.contains(".png") ||
                lower.contains(".webp")
        return isNotIcon && isAllowedHost
    }
}
