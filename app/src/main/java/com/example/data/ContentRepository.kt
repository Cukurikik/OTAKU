package com.example.data

import android.util.Log
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.Page
import com.example.data.sources.ContentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ContentRepository(val httpClient: okhttp3.OkHttpClient) {
    companion object {
        private const val MAX_CONCURRENT_SOURCE_REQUESTS = 5
    }

    private val _sources = mutableListOf<ContentSource>()
    var sources: List<ContentSource> = emptyList()
        set(value) {
            field = value
            _sources.clear()
            _sources.addAll(value)
            _sourcesFlow.value = value
        }

    private val _sourcesFlow = MutableStateFlow<List<ContentSource>>(emptyList())
    val sourcesFlow: StateFlow<List<ContentSource>> = _sourcesFlow.asStateFlow()

    fun addSource(source: ContentSource) {
        _sources.add(source)
        _sourcesFlow.value = _sources.toList()
    }

    private fun getContentType(sourceId: String): com.example.data.models.ContentType {
        val id = sourceId.lowercase()
        return when {
            id == "torrent_source" -> com.example.data.models.ContentType.ANIME
            id.contains("anime") || id.contains("otaku") || id.contains("samehadaku") || id.contains("anichin") || id.contains("kura") || id.contains("kaze") || id.contains("anoboy") || id.contains("nime") -> com.example.data.models.ContentType.ANIME
            id.contains("drama") || id.contains("drakor") || id.contains("viki") || id.contains("licil") || id.contains("film") || id.contains("jucelebrity") -> com.example.data.models.ContentType.DRAMA
            else -> com.example.data.models.ContentType.COMIC
        }
    }

    fun browse(filter: com.example.data.sources.BrowseFilter = com.example.data.sources.BrowseFilter(), sourceIds: List<String>? = null, page: Int = 1): Flow<List<ContentItem>> = channelFlow {
        val targetSources = if (sourceIds != null) {
            sources.filter { sourceIds.contains(it.sourceId) }
        } else {
            sources.toList()
        }
        val semaphore = Semaphore(MAX_CONCURRENT_SOURCE_REQUESTS)
        supervisorScope {
            targetSources.forEach { source ->
                launch {
                    semaphore.withPermit {
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                source.browse(filter, page).collect { items ->
                                    if (items.isNotEmpty()) {
                                        send(items)
                                    }
                                }
                            } ?: Log.w("ContentRepository", "Source ${source.sourceId} browse timed out after 15s")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: com.example.data.sources.CloudflareException) {
                            send(listOf(ContentItem(
                                id = "error_cloudflare|" + e.url,
                                title = "Cloudflare Challenge Required - Tap to Solve",
                                source = source.sourceName,
                                imageUrl = "",
                                type = getContentType(source.sourceId),
                                sourceId = source.sourceId
                            )))
                        } catch (e: Exception) {
                            Log.e("ContentRepository", "Source ${source.sourceId} browse failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun getRecommendations(sourceIds: List<String>? = null, page: Int = 1): Flow<List<ContentItem>> = channelFlow {
        val targetSources = if (sourceIds != null) {
            sources.filter { sourceIds.contains(it.sourceId) }
        } else {
            sources.toList()
        }
        val semaphore = Semaphore(MAX_CONCURRENT_SOURCE_REQUESTS)
        supervisorScope {
            targetSources.forEach { source ->
                launch {
                    semaphore.withPermit {
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                source.getRecommendations(page).collect { items ->
                                    if (items.isNotEmpty()) {
                                        send(items)
                                    }
                                }
                            } ?: Log.w("ContentRepository", "Source ${source.sourceId} getRecommendations timed out after 15s")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: com.example.data.sources.CloudflareException) {
                            send(listOf(ContentItem(
                                id = "error_cloudflare|" + e.url,
                                title = "Cloudflare Challenge Required - Tap to Solve",
                                source = source.sourceName,
                                imageUrl = "",
                                type = getContentType(source.sourceId),
                                sourceId = source.sourceId
                            )))
                        } catch (e: Exception) {
                            Log.e("ContentRepository", "Source ${source.sourceId} getRecommendations failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun search(query: String, sourceIds: List<String>? = null, page: Int = 1): Flow<List<ContentItem>> = channelFlow {
        val targetSources = if (sourceIds != null) {
            sources.filter { sourceIds.contains(it.sourceId) }
        } else {
            sources.toList()
        }
        val semaphore = Semaphore(MAX_CONCURRENT_SOURCE_REQUESTS)
        supervisorScope {
            targetSources.forEach { source ->
                launch {
                    semaphore.withPermit {
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                source.search(query, page).collect { items ->
                                    if (items.isNotEmpty()) {
                                        send(items)
                                    }
                                }
                            } ?: Log.w("ContentRepository", "Source ${source.sourceId} search timed out after 15s")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: com.example.data.sources.CloudflareException) {
                            send(listOf(ContentItem(
                                id = "error_cloudflare|" + e.url,
                                title = "Cloudflare Challenge Required - Tap to Solve",
                                source = source.sourceName,
                                imageUrl = "",
                                type = getContentType(source.sourceId),
                                sourceId = source.sourceId
                            )))
                        } catch (e: Exception) {
                            Log.e("ContentRepository", "Source ${source.sourceId} search failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    suspend fun getDetail(itemId: String, sourceId: String? = null): ContentItem? {
        val targetSources = if (sourceId != null) sources.filter { it.sourceId == sourceId } else sources.toList()
        for (source in targetSources) {
            try {
                val detail = source.getDetail(itemId)
                if (detail != null) return detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.example.data.sources.CloudflareException) {
                return ContentItem(
                    id = "error_cloudflare|" + e.url,
                    title = "Cloudflare Challenge Required - Tap to Solve",
                    source = source.sourceName,
                    imageUrl = "",
                    type = getContentType(source.sourceId),
                    sourceId = source.sourceId
                )
            } catch (e: Exception) {
                Log.e("ContentRepository", "Source ${source.sourceId} getDetail failed: ${e.message}")
            }
        }
        return null
    }

    fun getChapters(itemId: String, sourceId: String? = null): Flow<List<Chapter>> = flow {
        val targetSources = if (sourceId != null) sources.filter { it.sourceId == sourceId } else sources.toList()
        for (source in targetSources) {
            try {
                source.getChapters(itemId).collect { emit(it) }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.example.data.sources.CloudflareException) {
                emit(listOf(Chapter(id = "error_cloudflare|" + e.url, title = "Cloudflare Challenge Required - Tap to Solve", url = e.url, date = "")))
                break
            } catch (e: Exception) {
                Log.e("ContentRepository", "Source ${source.sourceId} getChapters failed: ${e.message}")
                continue
            }
        }
    }

    fun getPages(chapterId: String, sourceId: String? = null): Flow<List<Page>> = flow {
        val targetSources = if (sourceId != null) sources.filter { it.sourceId == sourceId } else sources.toList()
        for (source in targetSources) {
            try {
                source.getPages(chapterId).collect { emit(it) }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ContentRepository", "Source ${source.sourceId} getPages failed: ${e.message}")
                continue
            }
        }
    }

    fun getVideoStreamUrl(episodeId: String, sourceId: String? = null): Flow<String> = flow {
        val targetSources = if (sourceId != null) sources.filter { it.sourceId == sourceId } else sources.toList()
        for (source in targetSources) {
            try {
                source.getVideoStreamUrl(episodeId).collect {
                    if (it.isNotEmpty()) {
                        emit(it)
                    }
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ContentRepository", "Source ${source.sourceId} getVideoStreamUrl failed: ${e.message}")
                continue
            }
        }
    }

    fun getVideoStream(episodeId: String, sourceId: String? = null): Flow<com.example.data.sources.VideoStreamInfo> = flow {
        val targetSources = if (sourceId != null) sources.filter { it.sourceId == sourceId } else sources.toList()
        for (source in targetSources) {
            if (source is com.example.data.sources.VideoSource) {
                try {
                source.getVideoStream(episodeId).collect { emit(it) }
                break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ContentRepository", "Source ${source.sourceId} getVideoStream failed: ${e.message}")
                    
                    continue
                }
            }
        }
    }

    suspend fun getScrapedTitle(url: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val doc = org.jsoup.Jsoup.connect(url).userAgent("Mozilla/5.0").get()
                doc.title()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }
}
