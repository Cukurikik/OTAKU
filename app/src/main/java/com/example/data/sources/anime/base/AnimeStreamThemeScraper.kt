package com.example.data.sources.anime.base

import android.content.Context
import android.util.Base64
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.base.BaseAnimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

open class AnimeStreamThemeScraper(
    context: Context,
    override val sourceId: String,
    override val sourceName: String,
    override val baseUrl: String
) : BaseAnimeSource(context) {

    private val defaultCardSelector = "a.group.block, .manga-card, .series-card, .film-list .item, .listupd .bs, .bsx, article, .post-item, .item, .gmr-box-content, .movie-item, .animepost, .venz, .series, .bixbox, .post-show, .product__item"
    private val defaultCardLinkSelector = "a"
    private val defaultCardTitleSelector = ".tt, .title, .entry-title, h2, h3, h4, h5, .album-title, .video-title, .titlelist, .product__item__text h5"
    private val defaultCardImageSelector = "img, .thumb img, .kover img, .video-thumb img, .anime__details__pic, .set-bg, .product__item__pic, .post-thumbnail img"
    private val defaultEpisodeSelector = "a.group.flex, .episodelist li, .eplister li, .episode-list li, .epslist li, .episodes a, .video-links a, .server-list li, ul.servers li, .lsteps li, .ep-button, #episodes a, .ep-list a, .list-eps a, .box-list-episode a, .episode-list-2 li, a.ep-item"
    private val defaultEpisodeLinkSelector = "a"
    private val defaultEpisodeTitleSelector = ".epl-title, .ep-title, .episodi, .num, .server-name, .eps"

    protected open val cardSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "card", defaultCardSelector)

    protected open val cardLinkSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "card_link", defaultCardLinkSelector)

    protected open val cardTitleSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "card_title", defaultCardTitleSelector)

    protected open val cardImageSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "card_image", defaultCardImageSelector)

    protected open val episodeSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "episode", defaultEpisodeSelector)

    protected open val episodeLinkSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "episode_link", defaultEpisodeLinkSelector)

    protected open val episodeTitleSelector: String
        get() = com.example.data.sources.dynamic.DynamicSourceConfigManager.getSelector(sourceId, "episode_title", defaultEpisodeTitleSelector)

    protected open fun getPopularUrl(page: Int) = if (page <= 1) currentBaseUrl() else "${currentBaseUrl()}/page/$page/"
    protected open fun getSearchUrl(query: String, page: Int): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        return if (page <= 1) "${currentBaseUrl()}/?s=$encodedQuery" else "${currentBaseUrl()}/page/$page/?s=$encodedQuery"
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = getPopularUrl(page)
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
            val url = getSearchUrl(query, page)
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
            val item = AnimeDomExtractor.extractDetail(doc, sourceId, sourceName, itemId, currentBaseUrl())
            item
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
            val customHeaders = this@AnimeStreamThemeScraper.requestHeaders.toMutableMap()
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
