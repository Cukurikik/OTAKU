package com.example.data.sources.anime

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.direct.ScraperSourceConfig
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class GenericAnimeScraper(
    context: Context,
    private val config: ScraperSourceConfig
) : AnimeScraperTemplate(context) {
    override val sourceId: String = config.sourceId
    override val sourceName: String = config.sourceName
    override val baseUrl: String = config.baseUrl

    override val selectors = AnimeSelectors(
        card = config.selectors.card,
        title = config.selectors.cardTitle,
        link = config.selectors.cardLink,
        image = config.selectors.cardImage,
        description = config.selectors.description,
        genres = config.selectors.genres,
        episode = config.selectors.episode,
        episodeTitle = config.selectors.episodeTitle,
        episodeDate = "",
        searchCard = config.selectors.card
    )

    override fun recommendationsUrl(page: Int): String = config.popularUrl(page)

    override fun searchUrl(query: String, page: Int): String = config.searchUrl(query, page)

    
    private val engine by lazy { com.example.data.sources.direct.AnimeDramaScraperEngine(context, config) }
    private val videoExtractor by lazy { com.example.data.sources.VideoExtractorEngine(context) }

    override fun getRecommendations(page: Int): Flow<List<com.example.data.models.ContentItem>> = flow {
        try {
            val cards = engine.popular(page)
            emit(cards.map { 
                com.example.data.models.ContentItem(
                    id = it.url, title = it.title, source = sourceName, imageUrl = it.imageUrl, 
                    type = com.example.data.models.ContentType.ANIME, sourceId = sourceId
                ) 
            })
        } catch (e: CancellationException) {
            throw e
        } catch(e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
        }
    }

    override fun search(query: String, page: Int): Flow<List<com.example.data.models.ContentItem>> = flow {
        try {
            val cards = engine.search(query, page)
            emit(cards.map { 
                com.example.data.models.ContentItem(
                    id = it.url, title = it.title, source = sourceName, imageUrl = it.imageUrl, 
                    type = com.example.data.models.ContentType.ANIME, sourceId = sourceId
                ) 
            })
        } catch (e: CancellationException) {
            throw e
        } catch(e: Exception) {}
    }

    override suspend fun getDetail(itemId: String): com.example.data.models.ContentItem? {
        return try {
            val d = engine.detail(itemId) ?: return null
            com.example.data.models.ContentItem(
                id = d.url, title = d.title, source = sourceName, imageUrl = d.imageUrl,
                description = d.description, genres = d.genres, type = com.example.data.models.ContentType.ANIME, sourceId = sourceId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }
    
    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        if (sourceId == "nimegami") {
            try {
                val doc = fetcher.fetchHtml(itemId)
                val lis = doc.select(".list_eps_stream li.select-eps")
                val episodes = lis.mapIndexed { idx, li ->
                    val data = li.attr("data")
                    val title = li.text().trim()
                    val epUrl = "nimegami-stream://$data"
                    Chapter(epUrl, title.ifBlank { "Episode ${idx + 1}" }, epUrl, "")
                }
                if (episodes.isNotEmpty()) {
                    SourceStatusBus.clear(sourceId)
                    emit(episodes)
                    return@flow
                }
            } catch (e: Exception) {}
        }
        
        try {
            val eps = engine.episodes(itemId)
            if (eps.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(eps.map { Chapter(it.id, it.title, it.url, "") })
            } else {
                // fallback
                super.getChapters(itemId).collect { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch(e: Exception) {
            super.getChapters(itemId).collect { emit(it) }
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        try {
            val customHeaders = requestHeaders.toMutableMap()
            if (!customHeaders.containsKey("Referer")) {
                customHeaders["Referer"] = episodeId
            }

            // 1. Direct Interception
            val directInterception = videoUrlInterceptor.interceptVideo(episodeId, episodeId, timeoutMs = 15000L, headers = customHeaders)
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(directInterception)
                return@flow
            }

            // 2. Multi-Server DOM Extraction
            val doc = fetchDocument(episodeId)
            val pageHtml = doc.html()
            val multiStreams = extractMultiServerStreams(pageHtml, episodeId)
            if (multiStreams.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                val allQualities = multiStreams.map {
                    VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)
                }.distinctBy { it.url }

                val allServers = multiStreams.map {
                    VideoServer(
                        name = it.provider.ifBlank { it.quality },
                        embedUrl = it.embedUrl.ifBlank { it.url },
                        qualities = listOf(VideoQuality(it.quality, it.url, it.url.contains(".m3u8"), customHeaders)),
                        headers = customHeaders
                    )
                }.distinctBy { it.embedUrl }

                emit(VideoStreamInfo(
                    episodeId = episodeId,
                    embedUrl = multiStreams.first().url,
                    qualities = allQualities,
                    servers = allServers,
                    headers = customHeaders
                ))
                return@flow
            }

            // 3. Fallback to super getVideoStream which performs deep extraction and AES deobfuscation
            super.getVideoStream(episodeId).collect { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }
}
