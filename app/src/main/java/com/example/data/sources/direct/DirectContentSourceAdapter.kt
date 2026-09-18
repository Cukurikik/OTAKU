package com.example.data.sources.direct

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.BrowseFilter
import com.example.data.sources.ContentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DirectContentSourceAdapter(
    private val context: Context,
    private val config: ScraperSourceConfig,
    private val contentType: ContentType
) : com.example.data.sources.VideoSource {

    private val animeEngine by lazy { AnimeDramaScraperEngine(context, config) }
    private val comicEngine by lazy { ComicScraperEngine(context, config) }

    override val sourceId: String = config.sourceId
    override val sourceName: String = config.sourceName
    override val supportsVideo: Boolean = contentType == ContentType.ANIME || contentType == ContentType.DRAMA
    override val supportsFilter: Boolean = true
    override val supportedFilters: List<com.example.data.sources.FilterType> = listOf(com.example.data.sources.FilterType.ORDER, com.example.data.sources.FilterType.STATUS)
    
    // We only support VideoSource methods if it's anime/drama
    
    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        val items = if (contentType == ContentType.COMIC) {
            comicEngine.search(query, page)
        } else {
            animeEngine.search(query, page)
        }.map {
            ContentItem(
                id = it.url,
                title = it.title,
                source = sourceName,
                imageUrl = it.imageUrl,
                type = contentType,
                sourceId = sourceId
            )
        }
        emit(items)
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        val items = if (contentType == ContentType.COMIC) {
            comicEngine.popular(page)
        } else {
            animeEngine.popular(page)
        }.map {
            ContentItem(
                id = it.url,
                title = it.title,
                source = sourceName,
                imageUrl = it.imageUrl,
                type = contentType,
                sourceId = sourceId
            )
        }
        emit(items)
    }

    override fun browse(filter: BrowseFilter, page: Int): Flow<List<ContentItem>> = flow {
        val items = if (contentType == ContentType.COMIC) {
            comicEngine.popular(page)
        } else {
            animeEngine.popular(page)
        }.map {
            ContentItem(
                id = it.url,
                title = it.title,
                source = sourceName,
                imageUrl = it.imageUrl,
                type = contentType,
                sourceId = sourceId
            )
        }
        emit(items)
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        val detail = if (contentType == ContentType.COMIC) {
            comicEngine.detail(itemId)
        } else {
            animeEngine.detail(itemId)
        } ?: return null
        
        return ContentItem(
            id = detail.url,
            title = detail.title,
            source = sourceName,
            imageUrl = detail.imageUrl,
            type = contentType,
            description = detail.description,
            genres = detail.genres,
            sourceId = sourceId,
            status = detail.status,
            year = detail.year
        )
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        val eps = if (contentType == ContentType.COMIC) {
            comicEngine.chapters(itemId)
        } else {
            animeEngine.episodes(itemId)
        }.map {
            Chapter(
                id = it.url,
                title = it.title.takeIf { t -> t.isNotBlank() } ?: it.label,
                url = it.url,
                date = ""
            )
        }
        emit(eps)
    }

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        if (contentType == ContentType.COMIC) {
            val pages = comicEngine.pages(chapterId)
            if (pages.isEmpty()) {
                throw Exception("Gagal memuat halaman, atau scraper perlu disesuaikan.")
            }
            emit(pages)
        } else {
            emit(emptyList())
        }
    }

    override fun getVideoStreamUrl(episodeId: String): Flow<String> = flow {
        if (contentType == ContentType.COMIC) throw UnsupportedOperationException("Not supported for comic")
        val streamInfo = animeEngine.resolveVideo(episodeId)
        if (streamInfo != null) {
            emit(streamInfo.bestQuality?.url ?: streamInfo.embedUrl)
        } else {
            throw Exception("Video tidak ditemukan")
        }
    }

    override fun getVideoStream(episodeId: String): Flow<com.example.data.sources.VideoStreamInfo> = flow {
        if (contentType == ContentType.COMIC) throw UnsupportedOperationException("Not supported for comic")
        val streamInfo = animeEngine.resolveVideo(episodeId)
        if (streamInfo != null) {
            emit(streamInfo)
        } else {
            throw Exception("Video tidak ditemukan")
        }
    }
}