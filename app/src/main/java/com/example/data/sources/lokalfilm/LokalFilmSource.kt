package com.example.data.sources.lokalfilm

import android.content.Context
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.base.DramaEpisode
import com.example.data.sources.base.DramaSeason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jsoup.nodes.Document
import java.net.URLEncoder

open class LokalFilmSource(context: Context) : BaseDramaSource(context) {
    override val sourceId: String = "lokalfilm"
    override val sourceName: String = "LokalFilm"
    override val baseUrl: String = "https://lokalfilm.id"
    override val contentType: ContentType = ContentType.DRAMA

    val uvpExtractor = LokalFilmUvpExtractor()

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = when (page) {
                1 -> currentBaseUrl()
                2 -> "${currentBaseUrl()}/sub-category/12"
                3 -> "${currentBaseUrl()}/sub-category/2"
                4 -> "${currentBaseUrl()}/sub-category/3"
                5 -> "${currentBaseUrl()}/sub-category/7"
                6 -> "${currentBaseUrl()}/sub-category/8"
                else -> "${currentBaseUrl()}/page/$page/"
            }
            val doc = try {
                fetchDocument(url)
            } catch (e: Exception) {
                if (page > 1) {
                    emit(emptyList())
                    return@flow
                }
                throw e
            }

            val items = parseCards(doc)
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            if (page > 1) {
                emit(emptyList())
            } else {
                throw e
            }
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val targetUrl = if (page == 1) "${currentBaseUrl()}/?s=$encoded" else "${currentBaseUrl()}/page/$page/?s=$encoded"
            val doc = try {
                fetchDocument(targetUrl)
            } catch (e: Exception) {
                if (page > 1) {
                    emit(emptyList())
                    return@flow
                }
                throw e
            }

            val items = parseCards(doc)
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            if (page > 1) {
                emit(emptyList())
            } else {
                throw e
            }
        }
    }

    fun parseCards(doc: Document): List<ContentItem> {
        val elements = doc.select(".movie-item, .film-item, article, .post-item, .item")
        return elements.mapNotNull { el ->
            val a = el.selectFirst("a") ?: return@mapNotNull null
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank()) return@mapNotNull null

            val img = el.selectFirst("img")
            val src = img?.absUrl("src")?.ifEmpty { img.absUrl("data-src") } ?: ""
            var title = el.selectFirst("h3, .title, h2")?.text()?.takeIf { it.isNotBlank() }
                ?: a.attr("title").takeIf { it.isNotBlank() }
                ?: img?.attr("alt")?.takeIf { it.isNotBlank() && !it.equals("movie", ignoreCase = true) }
                ?: a.text().takeIf { it.isNotBlank() }

            if (title.isNullOrBlank()) {
                val seg = href.trimEnd('/').substringAfterLast('/')
                title = if (seg.isNotBlank()) seg.replace('-', ' ').replaceFirstChar { it.uppercase() } else "Film"
            }

            val genres = el.select(".genres a, .cat, .sub-title").map { it.text().trim() }.filter { it.isNotBlank() }

            ContentItem(
                id = href,
                title = title,
                imageUrl = src,
                sourceId = sourceId,
                source = sourceName,
                type = ContentType.DRAMA,
                genres = genres
            )
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            val title = doc.selectFirst("h1.entry-title, .s-title, h1, .movie-content h3.title, .movie-content .title, h2")?.text()
                ?: doc.selectFirst("meta[property='og:title']")?.attr("content")
                ?: "Film"
            val cleanTitle = title.replace(Regex("(?i)Lokalfilm\\s*-\\s*Movie Details"), "").trim().ifBlank { title }
            val image = doc.selectFirst(".thumb img, .poster img, .movie-thumb img, meta[property='og:image']")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.absUrl("src").ifEmpty { it.absUrl("data-src") }
            } ?: ""
            val description = doc.selectFirst(".entry-content, .desc, .sinopsis, .movie-content p, meta[name='description']")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            } ?: ""
            val genres = doc.select(".genres a, .cat, .sub-title, .tags a").map { it.text().trim() }.filter { it.isNotBlank() }
            val metadata = extractDramaMetadata(doc, cleanTitle)

            SourceStatusBus.clear(sourceId)
            ContentItem(
                id = itemId,
                title = cleanTitle,
                imageUrl = image,
                sourceId = sourceId,
                source = sourceName,
                type = ContentType.DRAMA,
                description = description,
                genres = genres,
                castList = metadata.castList,
                subtitleStatus = metadata.subtitleStatus,
                seasonNumber = metadata.seasonNumber
            )
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }

    override suspend fun fetchSeasonsAndEpisodes(dramaUrl: String): List<DramaSeason> {
        val doc = fetchDocument(dramaUrl)
        val episodes = mutableListOf<DramaEpisode>()

        // 1. Check FWD UVP items: li[data-video-source], li[data-source], .fwduvp-item
        val uvpList = doc.select("li[data-video-source], li[data-source], [data-video-source], .fwduvp-playlist-item")
        if (uvpList.isNotEmpty()) {
            uvpList.forEachIndexed { index, el ->
                val a = el.selectFirst("a")
                val sourceAttr = el.attr("data-video-source").ifEmpty { el.attr("data-source") }
                val decryptedSource = uvpExtractor.decryptUvpPayload(sourceAttr)
                val targetSource = if (decryptedSource.startsWith("http") || decryptedSource.contains(".m3u8") || decryptedSource.contains(".mp4")) decryptedSource else sourceAttr
                val href = a?.absUrl("href")?.takeIf { it.isNotBlank() } ?: targetSource.ifEmpty { "$dramaUrl#ep${index + 1}" }
                val titleEl = el.selectFirst(".fwduvp-thumbnail-title, span, h4, .title")
                val rawTitle = titleEl?.text()?.trim() ?: a?.text()?.trim() ?: "Episode ${index + 1}"

                episodes.add(
                    DramaEpisode(
                        episodeNumber = index + 1,
                        url = href,
                        title = rawTitle.ifBlank { "Episode ${index + 1}" }
                    )
                )
            }
        }

        // 2. Standard episode anchors if UVP playlist not found
        if (episodes.isEmpty()) {
            val epAnchors = doc.select(".episodes a, .episodelist a, .gmr-listseries a")
            epAnchors.forEachIndexed { index, a ->
                val href = a.absUrl("href")
                if (href.isNotBlank()) {
                    val epTitle = a.text().trim().ifBlank { "Episode ${index + 1}" }
                    episodes.add(
                        DramaEpisode(
                            episodeNumber = index + 1,
                            url = href,
                            title = epTitle
                        )
                    )
                }
            }
        }

        return if (episodes.isNotEmpty()) {
            listOf(DramaSeason(seasonNumber = 1, title = "Season 1", episodes = episodes))
        } else {
            emptyList()
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val seasons = fetchSeasonsAndEpisodes(itemId)
            val chapters = mutableListOf<Chapter>()
            for (season in seasons) {
                for (ep in season.episodes) {
                    chapters.add(Chapter(id = ep.url, title = ep.title, url = ep.url, date = ep.date))
                }
            }

            if (chapters.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(chapters)
            } else {
                // If single movie page, check if page has UVP player, video tag or iframe
                val doc = fetchDocument(itemId)
                val hasPlayer = doc.selectFirst("li[data-video-source], [data-video-source], video, iframe, #player") != null
                if (hasPlayer) {
                    SourceStatusBus.clear(sourceId)
                    emit(listOf(Chapter(id = itemId, title = "Tonton Film", url = itemId)))
                } else {
                    throw java.io.IOException("Tidak ditemukan episode atau pemutar aktif pada LokalFilm.")
                }
            }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override suspend fun fetchVideoStream(episodeUrl: String): Result<VideoStreamInfo> {
        return runCatching {
            val customHeaders = mutableMapOf(
                "Referer" to episodeUrl,
                "User-Agent" to NetworkModule.userAgent,
                "Origin" to "https://lokalfilm.id"
            )

            // Step 1: Jika episodeUrl sendiri adalah direct URL yang sudah diekstrak / HLS / MP4
            if (episodeUrl.contains(".m3u8") || episodeUrl.contains(".mp4")) {
                val isM3u8 = episodeUrl.contains(".m3u8")
                return@runCatching VideoStreamInfo(
                    episodeId = episodeUrl,
                    embedUrl = episodeUrl,
                    qualities = listOf(com.example.data.sources.VideoQuality("Auto", episodeUrl, isM3u8, customHeaders)),
                    headers = customHeaders
                )
            }

            // Step 2: Coba decode episodeUrl jika merupakan raw Base64 payload UVP
            val directDecrypted = uvpExtractor.decryptUvpPayload(episodeUrl)
            if (directDecrypted.contains(".m3u8") || directDecrypted.contains(".mp4") || directDecrypted.startsWith("http")) {
                val parsed = uvpExtractor.extractVideoStreamInfo(directDecrypted, episodeUrl)
                if (parsed != null && parsed.qualities.isNotEmpty()) {
                    return@runCatching parsed
                }
            }

            // Step 3: Fetch HTML Dokumen dan ekstraksi via UVP Extractor
            val doc = fetchDocument(episodeUrl)
            val uvpStreamInfo = uvpExtractor.extractVideoStreamInfo(doc, episodeUrl)
            if (uvpStreamInfo != null && uvpStreamInfo.qualities.isNotEmpty()) {
                return@runCatching uvpStreamInfo
            }

            // Step 4: Headless WebView Network Interception (Sniffing XHR/Fetch .m3u8 & .mp4)
            val intercepted = videoUrlInterceptor.interceptVideo(episodeUrl, episodeUrl, timeoutMs = 15000L, headers = customHeaders)
            if (intercepted != null && intercepted.qualities.isNotEmpty()) {
                return@runCatching intercepted
            }

            val directStreamUrl = videoUrlInterceptor.interceptStreamUrl(episodeUrl, timeoutMs = 12000L, headers = customHeaders)
            if (!directStreamUrl.isNullOrBlank()) {
                return@runCatching VideoStreamInfo(
                    episodeId = episodeUrl,
                    embedUrl = directStreamUrl,
                    qualities = listOf(com.example.data.sources.VideoQuality("Auto", directStreamUrl, isM3U8 = directStreamUrl.contains(".m3u8"), headers = customHeaders)),
                    headers = customHeaders
                )
            }

            // Step 5: Sniff iframe embeds jika ada
            val iframes = doc.select("iframe, .video-embed iframe, .player iframe, #player iframe")
            val embedUrls = iframes.map { it.absUrl("src").ifEmpty { it.absUrl("data-src") } }.filter { it.isNotBlank() }
            for (src in embedUrls) {
                val iframeInfo = videoUrlInterceptor.interceptVideo(src, episodeUrl, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    return@runCatching iframeInfo
                }
            }

            if (embedUrls.isNotEmpty()) {
                VideoStreamInfo(episodeId = episodeUrl, embedUrl = embedUrls.first(), headers = customHeaders)
            } else {
                throw java.io.IOException("Tidak ditemukan tautan video/UVP stream aktif pada LokalFilm ($episodeUrl).")
            }
        }
    }
}
