package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.db.ChapterDao
import com.example.data.db.LibraryDao
import com.example.data.models.Chapter
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import com.example.workers.MangaDownloadWorker
import com.example.data.db.DownloadDao
import com.example.data.db.CategoryDao
import androidx.media3.common.MimeTypes
import com.example.data.models.DownloadPayload
import com.example.data.models.SubtitlePayload
import com.example.workers.VideoDownloadService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import android.content.Context
import java.io.File
import com.example.data.sources.VideoStreamInfo
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DetailViewModel(
    private val context: Context,
    private val itemId: String,
    private val sourceId: String,
    private val repository: ContentRepository,
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao,
    private val downloadDao: DownloadDao,
    private val categoryDao: CategoryDao,
    private val trackDao: com.example.data.db.TrackDao
) : ViewModel() {

    val categories = categoryDao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryEntry = libraryDao.observeLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _item = MutableStateFlow<ContentItem?>(null)
    val item: StateFlow<ContentItem?> = _item.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    enum class StreamAction { PLAY, DOWNLOAD }
    data class StreamTarget(val episodeId: String, val episodeTitle: String, val action: StreamAction)
    
    private val _resolvingStream = MutableStateFlow(false)
    val resolvingStream: StateFlow<Boolean> = _resolvingStream.asStateFlow()
    
    private val _streamTarget = MutableStateFlow<StreamTarget?>(null)
    val streamTarget: StateFlow<StreamTarget?> = _streamTarget.asStateFlow()
    
    private val _resolvedStreamInfo = MutableStateFlow<VideoStreamInfo?>(null)
    val resolvedStreamInfo: StateFlow<VideoStreamInfo?> = _resolvedStreamInfo.asStateFlow()
    
    private val _streamAction = MutableSharedFlow<StreamAction?>(replay = 1)
    val streamAction: SharedFlow<StreamAction?> = _streamAction.asSharedFlow()
    
    fun clearStreamAction() {
        _streamAction.tryEmit(null)
    }

    private val _chapterEntries = MutableStateFlow<Map<String, ChapterEntry>>(emptyMap())
    val chapterEntries: StateFlow<Map<String, ChapterEntry>> = _chapterEntries.asStateFlow()

    init {
        loadDetail()
        loadChapters()
        checkFavorite()
        observeChapterEntries()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            try {
                _item.value = repository.getDetail(itemId, sourceId)
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load detail"
            }
        }
    }

    private fun loadChapters() {
        viewModelScope.launch {
            try {
                repository.getChapters(itemId, sourceId).collect { ch ->
                    android.util.Log.d("OtakuHub", "loadChapters received: ${ch.size} chapters for $itemId from $sourceId")
                    _chapters.value = ch
                    
                    // Batch upsert chapter entries to Room
                    val existingEntries = _chapterEntries.value
                    val newEntries = ch.mapIndexed { index, chapter ->
                        val existing = existingEntries[chapter.id]
                        if (existing != null) {
                            existing.copy(
                                name = chapter.title,
                                url = chapter.url,
                                dateUpload = null, // TODO parse date
                                number = (ch.size - index).toFloat()
                            )
                        } else {
                            ChapterEntry(
                                sourceId = sourceId,
                                mangaId = itemId,
                                chapterId = chapter.id,
                                name = chapter.title,
                                url = chapter.url,
                                dateUpload = null,
                                number = (ch.size - index).toFloat(),
                                read = false,
                                bookmark = false,
                                lastPageRead = 0,
                                lastReadAt = null,
                                downloaded = false
                            )
                        }
                    }
                    // Remove missing chapters and perform atomic database refresh
                    val incomingChapterIds = ch.map { it.id }.toSet()
                    val missingEntries = existingEntries.values.filter { it.chapterId !in incomingChapterIds }
                    chapterDao.refreshChapters(newEntries, missingEntries)
                    libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId)
                }
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load chapters"
            }
        }
    }

    private fun observeChapterEntries() {
        viewModelScope.launch {
            try {
                chapterDao.getChapters(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId).collect { entries ->
                    val map = entries.associateBy { it.chapterId }
                    _chapterEntries.value = map
                }
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun checkFavorite() {
        viewModelScope.launch {
            try {
                val entry = libraryDao.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId)
                _isFavorite.value = entry != null && entry.favorite
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load detail"
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val currentFav = _isFavorite.value
                val contentItem = _item.value
                if (contentItem != null) {
                    if (currentFav) {
                        libraryDao.deleteLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId)
                        _isFavorite.value = false
                    } else {
                        libraryDao.insertLibraryEntry(
                            LibraryEntry(
                                sourceId = sourceId,
                                mangaId = itemId,
                                title = contentItem.title,
                                thumbnailUrl = contentItem.imageUrl,
                                addedAt = System.currentTimeMillis(),
                                lastUpdatedAt = System.currentTimeMillis(),
                                favorite = true
                            )
                        )
                        _isFavorite.value = true
                        
                        // Fire-and-forget background task for Auto-Bind
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.trackers.AutoBindMappingEngine.tryAutoBind(context, contentItem)
                        }
                    }
                }
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load detail"
            }
        }
    }

    fun resolveVideoStream(episodeId: String, episodeTitle: String, action: StreamAction) {
        viewModelScope.launch {
            _resolvingStream.value = true
            _streamTarget.value = StreamTarget(episodeId, episodeTitle, action)
            try {
                val info = repository.getVideoStream(episodeId, sourceId).firstOrNull { 
                    it.qualities.isNotEmpty() || it.embedUrl.isNotEmpty() 
                }
                if (info != null && info.qualities.isNotEmpty()) {
                    _resolvedStreamInfo.value = info
                } else {
                    // No qualities to select, proceed directly
                    _resolvingStream.value = false
                    val directUrl = info?.bestQuality?.url ?: info?.embedUrl ?: episodeId
                    if (action == StreamAction.DOWNLOAD && info != null) {
                        startDirectVideoDownload(episodeId, directUrl, info.headers, info.subtitles, info.bestQuality?.qualityLabel)
                    } else {
                        executeStreamAction(directUrl, action, episodeId)
                    }
                }
            } catch (e: Exception) {
                _resolvingStream.value = false
                if (action == StreamAction.DOWNLOAD) {
                    _errorMessage.value = "Gagal menyelesaikan stream: ${e.message}"
                } else {
                    executeStreamAction(episodeId, action, episodeId) // Fallback for play
                }
            }
        }
    }
    
    fun dismissQualitySelector() {
        _resolvingStream.value = false
        _resolvedStreamInfo.value = null
        _streamTarget.value = null
    }
    
    fun selectQualityAndProceed(qualityUrl: String) {
        val target = _streamTarget.value
        val info = _resolvedStreamInfo.value
        _resolvingStream.value = false
        _resolvedStreamInfo.value = null
        _streamTarget.value = null
        
        if (target != null) {
            if (target.action == StreamAction.DOWNLOAD && info != null) {
                val quality = info.qualities.find { it.url == qualityUrl }
                startDirectVideoDownload(
                    episodeId = target.episodeId,
                    directUrl = qualityUrl,
                    headers = quality?.headers?.takeIf { it.isNotEmpty() } ?: info.headers,
                    subtitles = info.subtitles,
                    qualityLabel = quality?.qualityLabel
                )
            } else {
                executeStreamAction(qualityUrl, target.action, target.episodeId)
            }
        }
    }

    private fun startDirectVideoDownload(
        episodeId: String,
        directUrl: String,
        headers: Map<String, String>,
        subtitles: List<com.example.data.sources.Subtitle>,
        qualityLabel: String?
    ) {
        viewModelScope.launch {
            try {
                val mime = if (directUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
                val subPayloads = subtitles.map { SubtitlePayload(it.language, it.url, it.format) }
                val headersJson = Json.encodeToString(headers)
                val subsJson = Json.encodeToString(subPayloads)

                val download = com.example.data.models.DownloadEntity(
                    sourceId = sourceId,
                    mangaId = itemId,
                    chapterId = episodeId,
                    state = "QUEUE",
                    priority = 0,
                    directUrl = directUrl,
                    headersJson = headersJson,
                    mimeType = mime,
                    qualityLabel = qualityLabel,
                    subtitlesJson = subsJson
                )
                downloadDao.insertDownload(download)

                val payload = DownloadPayload(
                    sourceId = sourceId,
                    mangaId = itemId,
                    chapterId = episodeId,
                    directUrl = directUrl,
                    headers = headers,
                    mimeType = mime,
                    qualityLabel = qualityLabel,
                    subtitles = subPayloads
                )

                VideoDownloadService.enqueueDirectDownload(context, payload)
                VideoDownloadService.start(context)
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memulai unduhan video: ${e.message}"
            }
        }
    }
    
    private fun executeStreamAction(urlOrId: String, action: StreamAction, episodeId: String) {
        if (action == StreamAction.DOWNLOAD) {
            val urlFile = File(context.cacheDir, "download_urls")
            urlFile.mkdirs()
            File(urlFile, "${episodeId.hashCode()}.txt").writeText(urlOrId)
        } else {
            val urlFile = File(context.cacheDir, "play_urls")
            urlFile.mkdirs()
            File(urlFile, "${episodeId.hashCode()}.txt").writeText(urlOrId)
        }
        _streamAction.tryEmit(action)
    }

    fun downloadChapter(chapterId: String) {
        val type = _item.value?.type
        if (type == com.example.data.models.ContentType.ANIME || type == com.example.data.models.ContentType.DRAMA) {
            resolveVideoStream(chapterId, "Episode", StreamAction.DOWNLOAD)
            return
        }

        viewModelScope.launch {
            try {
                val download = com.example.data.models.DownloadEntity(
                    sourceId = sourceId,
                    mangaId = itemId,
                    chapterId = chapterId,
                    state = "QUEUE",
                    priority = 0
                )
                downloadDao.insertDownload(download)
                
                val source = repository.sources.find { it.sourceId == sourceId }
                var baseUrl = ""
                if (source != null) {
                    try {
                        val m = source.javaClass.getMethod("getBaseUrl")
                        val b = m.invoke(source) as? String
                        if (b != null) baseUrl = b
                    } catch (e: Exception) {
                        try {
                            val f = source.javaClass.getDeclaredField("baseUrl")
                            f.isAccessible = true
                            val b = f.get(source) as? String
                            if (b != null) baseUrl = b
                        } catch (ex: Exception) {}
                    }
                }
                
                val data = Data.Builder()
                    .putString("sourceId", sourceId)
                    .putString("baseUrl", baseUrl)
                    .putString("mangaId", itemId).putString("animeId", itemId).putString("episodeId", chapterId)
                    .putString("chapterId", chapterId)
                    .build()
                    
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = androidx.work.OneTimeWorkRequest.Builder(MangaDownloadWorker::class.java)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()
                    
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "download_${sourceId}_${itemId}_${chapterId}",
                    ExistingWorkPolicy.KEEP,
                    request
                )
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun deleteDownloadedChapter(chapterId: String) {
        viewModelScope.launch {
            try {
                val cbzFile = File(context.filesDir, "downloads/$sourceId/$itemId/$chapterId.cbz")
                if (cbzFile.exists()) cbzFile.delete()
                val stagingDir = File(context.filesDir, "downloads/$sourceId/$itemId/tmp_$chapterId")
                if (stagingDir.exists()) stagingDir.deleteRecursively()
                val chapterDir = File(context.filesDir, "downloads/$sourceId/$itemId/$chapterId")
                if (chapterDir.exists()) {
                    chapterDir.deleteRecursively()
                }
                try {
                    (context.applicationContext as? com.example.OtakuHubApp)?.database?.pageDownloadDao()
                        ?.deletePagesByChapter(sourceId, itemId, chapterId)
                } catch (e: Exception) {}
                val download = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId, chapterId)
                if (download != null) {
                    downloadDao.deleteDownload(download)
                }
                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId, chapterId)
                if (chapterEntry != null) {
                    chapterDao.updateChapter(chapterEntry.copy(downloaded = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun updateItemCategory(categoryId: Long?) {
        viewModelScope.launch {
            try {
                val entry = libraryDao.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, sourceId, itemId)
                if (entry != null) {
                    libraryDao.insertLibraryEntry(entry.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis()))
                }
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun bindTracker(tracker: com.example.data.trackers.Tracker, trackItem: com.example.data.models.TrackItem) {
        viewModelScope.launch {
            try {
                val contentItem = _item.value ?: return@launch
                val trackEntity = tracker.bind(trackItem, contentItem)
                val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                trackDao.insertTrack(trackEntity.copy(profileId = currentProfileId))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private val _quickSourceSearchResults = MutableStateFlow<List<ContentItem>>(emptyList())
    val quickSourceSearchResults: StateFlow<List<ContentItem>> = _quickSourceSearchResults.asStateFlow()

    private val _isSearchingOtherSources = MutableStateFlow(false)
    val isSearchingOtherSources: StateFlow<Boolean> = _isSearchingOtherSources.asStateFlow()

    fun searchAcrossOtherSources() {
        val currentTitle = _item.value?.title?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            _isSearchingOtherSources.value = true
            _quickSourceSearchResults.value = emptyList()
            try {
                val foundItems = mutableListOf<ContentItem>()
                val seenKeys = mutableSetOf<String>()
                // Search across all other sources
                val otherSources = repository.sources.filter { it.sourceId != sourceId }
                val otherSourceIds = otherSources.map { it.sourceId }
                
                if (otherSourceIds.isNotEmpty()) {
                    repository.search(currentTitle, sourceIds = otherSourceIds).collect { items ->
                        for (item in items) {
                            val key = "${item.sourceId}_${item.id}"
                            if (item.sourceId != sourceId && seenKeys.add(key)) {
                                foundItems.add(item)
                            }
                        }
                        _quickSourceSearchResults.value = foundItems.toList()
                    }
                }
            } catch (e: Exception) {
                // Ignore errors from individual failing sources
            } finally {
                _isSearchingOtherSources.value = false
            }
        }
    }
}
