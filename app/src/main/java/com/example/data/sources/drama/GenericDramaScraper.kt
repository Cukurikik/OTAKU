package com.example.data.sources.drama

import android.content.Context
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.templates.AnimeScraperTemplate
import com.example.data.sources.direct.ScraperSourceConfig
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException

class GenericDramaScraper(
    context: Context,
    private val config: ScraperSourceConfig
) : AnimeScraperTemplate(context) {
    override val sourceId: String = config.sourceId
    override val sourceName: String = config.sourceName
    override val baseUrl: String = config.baseUrl
    override val contentType = ContentType.DRAMA

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
                    type = ContentType.DRAMA, sourceId = sourceId
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
                    type = ContentType.DRAMA, sourceId = sourceId
                ) 
            })
        } catch (e: CancellationException) {
            throw e
        } catch(e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override suspend fun getDetail(itemId: String): com.example.data.models.ContentItem? {
        return try {
            val d = engine.detail(itemId) ?: return null
            com.example.data.models.ContentItem(
                id = d.url, title = d.title, source = sourceName, imageUrl = d.imageUrl,
                description = d.description, genres = d.genres, type = ContentType.DRAMA, sourceId = sourceId,
                castList = d.castList, subtitleStatus = d.subtitleStatus, seasonNumber = d.seasonNumber
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }
    
        override fun getChapters(itemId: String): Flow<List<com.example.data.models.Chapter>> = flow {
        try {
            val eps = engine.episodes(itemId)
            if (eps.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(eps.map { com.example.data.models.Chapter(it.id, it.title, it.url, "") })
            } else {
                emit(listOf(com.example.data.models.Chapter(itemId, "Tonton Sekarang / Watch Now", itemId, "")))
            }
        } catch (e: CancellationException) {
            throw e
        } catch(e: Exception) {
            emit(listOf(com.example.data.models.Chapter(itemId, "Tonton Sekarang / Watch Now", itemId, "")))
        }
    }

}
