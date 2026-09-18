package com.example.data.sources.local

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.BrowseFilter
import com.example.data.sources.VideoSource
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class LocalContentSource(
    private val context: Context? = null
) : VideoSource {
    override val sourceId: String = "local_storage"
    override val sourceName: String = "Penyimpanan Lokal"
    override val supportsVideo: Boolean = true
    override val supportsSearch: Boolean = true

    private val storageManager: LocalMediaStorageManager? = context?.let { LocalMediaStorageManager(it) }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        if (page > 1) {
            emit(emptyList())
            return@flow
        }
        if (storageManager != null) {
            val items = storageManager.scanAllLocalContent()
            emit(items)
        } else {
            // Fallback if context is not available
            emit(emptyList())
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        if (page > 1 || storageManager == null) {
            emit(emptyList())
            return@flow
        }
        val all = storageManager.scanAllLocalContent()
        val filtered = all.filter { it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        emit(filtered)
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        if (storageManager != null) {
            val chapters = storageManager.getChaptersForItem(itemId)
            emit(chapters)
        } else {
            val file = File(itemId)
            if (file.exists()) {
                emit(listOf(Chapter(id = itemId, title = file.nameWithoutExtension, url = itemId)))
            } else {
                emit(emptyList())
            }
        }
    }

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        if (storageManager != null) {
            val pages = storageManager.getPagesForChapter(chapterId)
            emit(pages)
        } else {
            emit(emptyList())
        }
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = flow {
        val uri = storageManager?.getVideoUri(episodeId) ?: "file://$episodeId"
        emit(
            VideoStreamInfo(
                episodeId = episodeId,
                embedUrl = uri,
                qualities = listOf(VideoQuality("Lokal (Original)", uri))
            )
        )
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        if (storageManager != null) {
            val all = storageManager.scanAllLocalContent()
            val found = all.find { it.id == itemId }
            if (found != null) return found
        }
        
        // Fallback check
        val cleanPath = itemId.removePrefix("local_file:").removePrefix("local_dir:").removePrefix("local_video_file:").removePrefix("local_video_dir:")
        val file = File(cleanPath)
        if (!file.exists()) return null

        val isVideo = file.extension.lowercase() in LocalMediaStorageManager.SUPPORTED_VIDEO_EXTENSIONS
        val type = if (isVideo) ContentType.ANIME else ContentType.COMIC

        return ContentItem(
            id = itemId,
            title = file.nameWithoutExtension,
            source = sourceName,
            imageUrl = if (type == ContentType.COMIC) "cbz://${file.absolutePath}::cover" else "",
            type = type,
            description = "Lokasi: ${file.absolutePath}\nUkuran: ${file.length() / (1024 * 1024)} MB",
            sourceId = sourceId
        )
    }
}
