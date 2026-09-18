package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.db.ChapterDao
import com.example.data.db.HistoryDao
import com.example.data.models.ChapterEntry
import com.example.data.models.HistoryItem
import com.example.data.models.Page
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

import com.example.data.db.PageDownloadDao
import kotlinx.coroutines.flow.firstOrNull

sealed interface ReaderState {
    object Loading : ReaderState
    data class Success(
        val pages: List<Page>, 
        val initialPage: Int,
        val nextChapter: ChapterEntry? = null,
        val prevChapter: ChapterEntry? = null,
        val allChapters: List<ChapterEntry> = emptyList(), val isOffline: Boolean = false
    ) : ReaderState
    data class Error(val message: String) : ReaderState
    object Empty : ReaderState
}

class ReaderViewModel(
    chapterId: String,
    itemId: String,
    title: String,
    sourceId: String,
    private val repository: ContentRepository,
    private val historyDao: HistoryDao,
    private val chapterDao: ChapterDao,
    private val pageDownloadDao: PageDownloadDao,
    private val libraryDao: com.example.data.db.LibraryDao,
    private val trackDao: com.example.data.db.TrackDao,
    private val settingsManager: com.example.data.SettingsManager,
    private val downloadDao: com.example.data.db.DownloadDao,
    private val context: android.content.Context,
    private val initialPage: Int = 0
) : ViewModel() {

    var activeSourceId: String = sourceId
    var activeItemId: String = itemId
    var activeChapterId: String = chapterId
    var activeTitle: String = title

    val currentChapterId: String get() = activeChapterId
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val isSearchingMigration = MutableStateFlow(false)
    val migrationCandidates = MutableStateFlow<List<com.example.data.linkrot.MigrationCandidate>>(emptyList())
    val showMigrationDialog = MutableStateFlow(false)
    val isPerformingMigration = MutableStateFlow(false)

    fun dismissMigrationDialog() {
        showMigrationDialog.value = false
    }

    fun openMigrationDialog() {
        if (migrationCandidates.value.isEmpty()) {
            triggerAutoMigrationSearch()
        } else {
            showMigrationDialog.value = true
        }
    }

    fun triggerAutoMigrationSearch() {
        viewModelScope.launch {
            isSearchingMigration.value = true
            showMigrationDialog.value = true
            try {
                val app = context.applicationContext as com.example.OtakuHubApp
                val queryTitle = activeTitle.ifBlank { activeItemId }
                val candidates = com.example.data.linkrot.AutoMigrationEngine.findCandidates(
                    app = app,
                    currentSourceId = activeSourceId,
                    currentTitle = queryTitle,
                    targetType = com.example.data.models.ContentType.COMIC
                )
                migrationCandidates.value = candidates
            } catch (e: Exception) {
                migrationCandidates.value = emptyList()
            } finally {
                isSearchingMigration.value = false
            }
        }
    }

    fun executeOneTapMigration(candidate: com.example.data.linkrot.MigrationCandidate) {
        viewModelScope.launch {
            isPerformingMigration.value = true
            try {
                val app = context.applicationContext as com.example.OtakuHubApp
                val oldTitle = activeTitle
                val numStr = oldTitle.replace(Regex("[^0-9.]"), "")
                val currentNum = numStr.toFloatOrNull()

                val result = com.example.data.linkrot.AutoMigrationEngine.executeMigrationCandidate(
                    app = app,
                    oldSourceId = activeSourceId,
                    oldMangaId = activeItemId,
                    oldTitle = oldTitle,
                    candidate = candidate
                )

                if (result.isSuccess) {
                    activeSourceId = candidate.sourceId
                    activeItemId = candidate.mangaId

                    val newChaptersFromDb = app.database.chapterDao().getChaptersList(
                        profileId = com.example.data.ProfileManager.currentProfileIdValue,
                        sourceId = activeSourceId,
                        mangaId = activeItemId
                    )

                    val matchedChapter = com.example.data.linkrot.AutoMigrationEngine.findMatchingChapter(
                        newChapters = newChaptersFromDb,
                        targetNumber = currentNum,
                        targetName = oldTitle
                    )

                    if (matchedChapter != null) {
                        activeChapterId = matchedChapter.chapterId
                        activeTitle = matchedChapter.name
                    }

                    showMigrationDialog.value = false
                    _uiEvent.emit("Berhasil dipindahkan ke sumber ${candidate.sourceName}")
                    loadContent()
                } else {
                    _uiEvent.emit("Migrasi gagal: ${result.errorMessage ?: "Terjadi kesalahan"}")
                }
            } catch (e: Exception) {
                _uiEvent.emit("Migrasi gagal: ${e.localizedMessage}")
            } finally {
                isPerformingMigration.value = false
            }
        }
    }

    private var updateProgressJob: Job? = null
    private var lastSavedIndex: Int = -1
    private var lastActiveWindow: Set<Int> = emptySet()

    /**
     * Viewport Bounded Pre-fetching Window:
     * Keeps at most 3 pages ahead and 1 page behind in RAM memory cache,
     * evicting off-screen bitmaps outside this window back to disk cache.
     */
    fun updateActiveViewport(currentIndex: Int, totalPages: Int) {
        if (totalPages <= 0) return
        val minKeepIndex = (currentIndex - 1).coerceAtLeast(0)
        val maxKeepIndex = (currentIndex + 3).coerceAtMost(totalPages - 1)
        
        val activeWindowIndices = (minKeepIndex..maxKeepIndex).toSet()
        if (activeWindowIndices == lastActiveWindow) return
        lastActiveWindow = activeWindowIndices
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            evictBitmapsOutsideWindow(activeWindowIndices)
            prefetchActiveWindow(activeWindowIndices)
        }
    }

    fun evictBitmapsOutsideWindow(activeWindowIndices: Set<Int>, pagesOverride: List<Page>? = null) {
        val pages = pagesOverride ?: (_state.value as? ReaderState.Success)?.pages ?: return
        if (pages.isEmpty()) return

        try {
            val imageLoader = context.imageLoader
            val memoryCache = imageLoader.memoryCache ?: return

            for (i in pages.indices) {
                if (i !in activeWindowIndices) {
                    val url = pages[i].url
                    memoryCache.remove(coil.memory.MemoryCache.Key(url))
                    val keysToRemove = memoryCache.keys.filter { it.key.contains(url) }
                    for (key in keysToRemove) {
                        memoryCache.remove(key)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore safely
        }
    }

    fun prefetchActiveWindow(activeWindowIndices: Set<Int>, pagesOverride: List<Page>? = null) {
        val pages = pagesOverride ?: (_state.value as? ReaderState.Success)?.pages ?: return
        if (pages.isEmpty()) return

        try {
            val imageLoader = context.imageLoader
            for (idx in activeWindowIndices) {
                if (idx in pages.indices) {
                    val request = ImageRequest.Builder(context)
                        .data(pages[idx].url)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        } catch (e: Exception) {
            // Ignore safely
        }
    }

    init {
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            try {
                // Load initial page first
                var effectiveInitialPage = if (initialPage > 0) initialPage else 0
                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, activeChapterId)
                if (initialPage <= 0 && chapterEntry != null) {
                    effectiveInitialPage = chapterEntry.lastPageRead
                } else if (chapterEntry == null) {
                    chapterDao.insertChapter(
                        ChapterEntry(
                            sourceId = activeSourceId,
                            mangaId = activeItemId,
                            chapterId = activeChapterId,
                            name = activeTitle,
                            url = "",
                            dateUpload = null,
                            number = null
                        )
                    )
                }
                
                // Get next/prev chapters
                val chapters = chapterDao.getChaptersList(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId)
                val currentIndex = chapters.indexOfFirst { it.chapterId == activeChapterId }
                val nextChapter = if (currentIndex > 0) chapters[currentIndex - 1] else null
                val prevChapter = if (currentIndex in 0 until chapters.size - 1) chapters[currentIndex + 1] else null

                // Load pages
                var foundPages = false
                
                // First check local downloaded pages
                val localPages = pageDownloadDao.getPages(activeSourceId, activeItemId, activeChapterId).firstOrNull()
                if (localPages != null && localPages.isNotEmpty()) {
                    val convertedPages = localPages.map { p ->
                        val imagePath = p.localUri?.let { if (it.startsWith("cbz://")) it else "file://$it" } ?: p.remoteUrl
                        Page(url = imagePath)
                    }
                    foundPages = true
                    val finalInitialPage = if (convertedPages.isNotEmpty()) {
                        effectiveInitialPage.coerceIn(0, convertedPages.size - 1)
                    } else {
                        0
                    }
                    val isOffline = convertedPages.any { it.url.startsWith("cbz://") || it.url.startsWith("file://") }
                    _state.value = ReaderState.Success(convertedPages, finalInitialPage, nextChapter, prevChapter, chapters, isOffline)
                }
                
                // If not downloaded, fetch from remote
                if (!foundPages) {
                    repository.getPages(activeChapterId, activeSourceId).collect { pages ->
                        if (pages.isNotEmpty()) {
                            foundPages = true
                            val finalInitialPage = if (pages.isNotEmpty()) {
                                effectiveInitialPage.coerceIn(0, pages.size - 1)
                            } else {
                                0
                            }
                            val isOffline = pages.any { it.url.startsWith("cbz://") || it.url.startsWith("file://") || it.url.startsWith("content://") }
                            _state.value = ReaderState.Success(pages, finalInitialPage, nextChapter, prevChapter, chapters, isOffline)
                        }
                    }
                }
                if (!foundPages) {
                    com.example.data.linkrot.LinkRotTracker.getInstance(context).recordFailure(activeSourceId, activeItemId, "Halaman kosong atau tautan mati")
                    _state.value = ReaderState.Error("Halaman komik tidak ditemukan atau tautan bermasalah.")
                    triggerAutoMigrationSearch()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                com.example.data.linkrot.LinkRotTracker.getInstance(context).recordFailure(activeSourceId, activeItemId, errorMsg)
                _state.value = ReaderState.Error(errorMsg)
                triggerAutoMigrationSearch()
            }
        }
    }

    fun updateLastPageRead(pageIndex: Int, totalPages: Int) {
        if (pageIndex == lastSavedIndex) return
        
        updateProgressJob?.cancel()
        updateProgressJob = viewModelScope.launch {
            delay(500) // Debounce
            lastSavedIndex = pageIndex
            try {
                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, activeChapterId)
                val isRead = pageIndex >= totalPages - 1
                if (chapterEntry != null) {
                    val wasRead = chapterEntry.read
                    chapterDao.updateChapter(
                        chapterEntry.copy(
                            lastPageRead = pageIndex,
                            lastReadAt = System.currentTimeMillis(),
                            read = if (isRead) true else chapterEntry.read
                        )
                    )
                    if (isRead && !wasRead) {
                        libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId)
                        com.example.workers.DownloadAutomator.processAutomations(
                            context = context,
                            settings = settingsManager,
                            chapterDao = chapterDao,
                            downloadDao = downloadDao,
                            sourceId = activeSourceId,
                            itemId = activeItemId,
                            currentChapterId = activeChapterId,
                            contentType = com.example.data.models.ContentType.COMIC
                        )
                        
                        // Auto-Sync Tracker via ScrobblerEngine
                        try {
                            val number = activeTitle.filter { it.isDigit() }.toIntOrNull() ?: 1
                            com.example.OtakuHubApp.instance.scrobblerEngine.checkAndTriggerMangaScrobble(
                                currentPage = pageIndex,
                                totalPages = totalPages,
                                mediaId = activeItemId,
                                chapterNumber = number
                            )
                            val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                            val tracks = trackDao.getTracksForManga(currentProfileId, activeItemId).firstOrNull()
                            if (!tracks.isNullOrEmpty()) {
                                for (track in tracks) {
                                    if (number > track.lastChapterRead) {
                                        val updatedTrack = track.copy(lastChapterRead = number.toFloat())
                                        trackDao.updateTrack(updatedTrack)
                                        val tracker = com.example.OtakuHubApp.instance.trackerManager.getTracker(track.trackerId)
                                        tracker?.update(updatedTrack)
                                        tracker?.let {
                                            _uiEvent.emit("Progres tersinkronisasi dengan ${it.name}")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Update history progress only if incognito is disabled
                if (!settingsManager.isIncognito) {
                    val detail = repository.getDetail(activeItemId, activeSourceId)
                    if (detail != null) {
                        val progress = if (totalPages > 0) (pageIndex + 1).toFloat() / totalPages.toFloat() else 0f
                        val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                        val historyItem = HistoryItem(
                            profileId = currentProfileId,
                            id = detail.id,
                            title = detail.title,
                            source = detail.source,
                            imageUrl = detail.imageUrl,
                            type = detail.type,
                            progress = progress,
                            lastChapterOrEpisode = activeTitle,
                            sourceId = activeSourceId,
                            timestamp = System.currentTimeMillis()
                        )
                        historyDao.insertHistory(historyItem)
                    }

                    // Delta State Sync: Push micro-state to Cloud & Local LAN Bus
                    com.example.data.sync.CrossDeviceResumeManager.pushReaderState(
                        context = context,
                        itemId = activeItemId,
                        sourceId = activeSourceId,
                        contentTitle = detail?.title ?: activeTitle,
                        chapterId = activeChapterId,
                        chapterTitle = activeTitle,
                        pageIndex = pageIndex,
                        totalPages = totalPages,
                        coverImageUrl = detail?.imageUrl ?: "",
                        force = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
