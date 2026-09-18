package com.example.data.sources.anime.base

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class OploverzThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : BaseAnimeSource(context) {

    protected open val cardSelector = ".venz ul li, .post-item, .article-list li, .monito li, article, .item, .bsx, .bixbox .bs"
    protected open val episodeSelector = ".episodelist ul li, .episodelist li, .monito li, .smokedl a, .smokeurl a, .box-list-episode a, .eplister li, a.ep-item"

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page == 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = if (page == 1) "${currentBaseUrl()}/?s=$encoded" else "${currentBaseUrl()}/page/$page/?s=$encoded"
            val doc = fetchDocument(url)
            val items = AnimeDomExtractor.extractCards(doc, sourceId, sourceName, currentBaseUrl(), cardSelector)
            emit(items)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val doc = fetchDocument(itemId)
            AnimeDomExtractor.extractDetail(doc, sourceId, sourceName, itemId, currentBaseUrl())
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val doc = fetchDocument(itemId)
            val eps = AnimeDomExtractor.extractEpisodes(doc, currentBaseUrl(), episodeSelector)
            if (eps.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(eps)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val interceptor = com.example.network.interceptor.VideoUrlInterceptor(context)
            val customHeaders = this@OploverzThemeScraper.requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Direct Interception pada episodeId via headless network sniffer
            val directInterception = interceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            val doc = fetchDocument(episodeId)
            val extractedMirrors = AnimeDomExtractor.extractAllServersAndEmbeds(doc, currentBaseUrl(), episodeId)
            val resolvedServers = mutableListOf<VideoServer>()
            val allQualities = mutableListOf<VideoQuality>()
            val embedUrls = mutableSetOf<String>()

            for ((serverName, embedUrl) in extractedMirrors) {
                embedUrls.add(embedUrl)
                val streamUrl = interceptor.interceptStreamUrl(embedUrl, timeoutMs = 10000L, headers = customHeaders)
                if (!streamUrl.isNullOrBlank()) {
                    val q = VideoQuality(serverName, streamUrl, isM3U8 = streamUrl.contains(".m3u8"), headers = customHeaders)
                    allQualities.add(q)
                    resolvedServers.add(VideoServer(
                        name = serverName,
                        embedUrl = embedUrl,
                        qualities = listOf(q),
                        headers = customHeaders
                    ))
                } else {
                    resolvedServers.add(VideoServer(
                        name = serverName,
                        embedUrl = embedUrl,
                        headers = customHeaders
                    ))
                }
            }

            if (allQualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                val firstEmbed = resolvedServers.firstOrNull { it.qualities.isNotEmpty() }?.embedUrl ?: resolvedServers.firstOrNull()?.embedUrl.orEmpty()
                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = firstEmbed,
                    qualities = allQualities.distinctBy { it.url },
                    servers = resolvedServers,
                    headers = customHeaders
                ))
                return@flow
            }

            for (embedUrl in embedUrls) {
                val iframeInfo = interceptor.interceptVideo(embedUrl, episodeId, timeoutMs = 12000L, headers = customHeaders)
                if (iframeInfo != null && iframeInfo.qualities.isNotEmpty()) {
                    SourceStatusBus.clear(sourceId)
                    emit(iframeInfo)
                    return@flow
                }
            }

            if (resolvedServers.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = resolvedServers.first().embedUrl,
                    servers = resolvedServers,
                    headers = customHeaders
                ))
                return@flow
            }

            // Fallback to BaseAnimeSource
            super.getVideoStream(episodeId).collect { emit(it) }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
