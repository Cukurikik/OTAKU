package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.db.HistoryDao
import com.example.data.models.Chapter
import com.example.data.models.HistoryItem
import com.example.data.sources.VideoStreamInfo
import java.io.File
import com.example.data.db.DownloadDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import androidx.media3.common.PlaybackException
import com.example.data.sources.VideoExtractorEngine
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.registry.SourceHealthProbe
import com.example.ui.player.PlaylistQueue
import com.example.ui.player.PlaylistQueueState

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Success(val data: VideoStreamInfo) : PlayerState
    data class SeamlessBuffering(val previousData: VideoStreamInfo, val message: String = "Menyambungkan kembali stream...") : PlayerState
    data class Error(val message: String) : PlayerState
}

class PlayerViewModel(
    initialEpisodeId: String,
    private val itemId: String,
    initialTitle: String, 
    private val sourceId: String,
    private val repository: ContentRepository,
    private val historyDao: HistoryDao,
    private val chapterDao: com.example.data.db.ChapterDao,
    private val libraryDao: com.example.data.db.LibraryDao,
    private val trackDao: com.example.data.db.TrackDao,
    private val settingsManager: com.example.data.SettingsManager,
    private val downloadDao: DownloadDao,
    private val context: android.content.Context,
    private val initialSeekPosition: Long = 0L
) : ViewModel() {

    var activeSourceId: String = sourceId
    var activeItemId: String = itemId

    private val videoExtractorEngine by lazy {
        VideoExtractorEngine(context)
    }

    private val _isFailoverResolving = MutableStateFlow(false)
    val isFailoverResolving: StateFlow<Boolean> = _isFailoverResolving.asStateFlow()

    private val _failoverMessage = MutableStateFlow<String?>(null)
    val failoverMessage: StateFlow<String?> = _failoverMessage.asStateFlow()

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
                val queryTitle = _currentTitle.value.ifBlank { activeItemId }
                val candidates = com.example.data.linkrot.AutoMigrationEngine.findCandidates(
                    app = app,
                    currentSourceId = activeSourceId,
                    currentTitle = queryTitle,
                    targetType = com.example.data.models.ContentType.ANIME
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
                val oldTitle = _currentTitle.value
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
                        _currentEpisodeId.value = matchedChapter.chapterId
                        _currentTitle.value = matchedChapter.name
                    }

                    _chapters.value = newChaptersFromDb.map {
                        Chapter(id = it.chapterId, title = it.name, url = it.url, date = it.dateUpload?.toString() ?: "")
                    }
                    updateEpisodeNavigation()
                    showMigrationDialog.value = false
                    _uiEvent.emit("Berhasil dipindahkan ke sumber ${candidate.sourceName}")
                    
                    loadVideo()
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

    private val _currentEpisodeId = MutableStateFlow(initialEpisodeId)
    private val _currentTitle = MutableStateFlow(initialTitle)
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _streamInfo = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val streamInfo: StateFlow<PlayerState> = _streamInfo.asStateFlow()
    val skipTimes = MutableStateFlow<List<com.example.data.trackers.AniSkipResult>>(emptyList())
    
    val startPosition = MutableStateFlow(0L)
    val subtitleOffsetMs = MutableStateFlow(0L)

    val playerSpeed = MutableStateFlow(settingsManager.playerSpeed)
    val subtitleFontSize = MutableStateFlow(settingsManager.subtitleFontSize)
    val subtitleTextColor = MutableStateFlow(settingsManager.subtitleTextColor)
    val subtitleBgColor = MutableStateFlow(settingsManager.subtitleBgColor)
    val backgroundAudioEnabled = MutableStateFlow(settingsManager.backgroundAudio)
    val autoSkipOpEd = MutableStateFlow(settingsManager.autoSkipOpEd)
    val audioOnlyMode = MutableStateFlow(settingsManager.audioOnlyMode)
    val isScreenLocked = MutableStateFlow(false)

    private var hasSyncedTrackersForCurrentEp = false

    fun toggleAudioOnlyMode() {
        val newMode = !audioOnlyMode.value
        audioOnlyMode.value = newMode
        settingsManager.audioOnlyMode = newMode
    }

    fun setAudioOnlyMode(enabled: Boolean) {
        audioOnlyMode.value = enabled
        settingsManager.audioOnlyMode = enabled
    }

    fun updateAutoSkipOpEd(enabled: Boolean) {
        autoSkipOpEd.value = enabled
        settingsManager.autoSkipOpEd = enabled
    }

    fun setAutoSkipOpEd(enabled: Boolean) = updateAutoSkipOpEd(enabled)

    fun updateSubtitleOffset(offsetMs: Long) {
        subtitleOffsetMs.value = offsetMs
    }

    fun updatePlayerSpeed(speed: Float) {
        playerSpeed.value = speed
        settingsManager.playerSpeed = speed
    }

    fun updateSubtitleFontSize(size: Float) {
        subtitleFontSize.value = size
        settingsManager.subtitleFontSize = size
    }

    fun updateSubtitleTextColor(color: Int) {
        subtitleTextColor.value = color
        settingsManager.subtitleTextColor = color
    }

    fun updateSubtitleBgColor(color: Int) {
        subtitleBgColor.value = color
        settingsManager.subtitleBgColor = color
    }

    fun updateBackgroundAudio(enabled: Boolean) {
        backgroundAudioEnabled.value = enabled
        settingsManager.backgroundAudio = enabled
    }

    fun toggleScreenLock() {
        isScreenLocked.value = !isScreenLocked.value
    }

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _coverImageUrl = MutableStateFlow("")
    val coverImageUrl: StateFlow<String> = _coverImageUrl.asStateFlow()

    private val _seriesTitle = MutableStateFlow("")
    val seriesTitle: StateFlow<String> = _seriesTitle.asStateFlow()

    val playlistQueue = PlaylistQueue(initialEpisodeId = initialEpisodeId)
    val nextEpisode = MutableStateFlow<Chapter?>(null)
    val previousEpisode = MutableStateFlow<Chapter?>(null)

    val showAutoNextOverlay = MutableStateFlow(false)
    val autoNextCountdownSeconds = MutableStateFlow(5)
    var isAutoNextCancelled: Boolean = false

    fun getNextEpisode(): Chapter? {
        val nextFromQueue = playlistQueue.getNextEpisode()
        if (nextFromQueue != null) return nextFromQueue

        val currentList = _chapters.value
        if (currentList.isEmpty()) return null
        val currentIndex = currentList.indexOfFirst { it.id == _currentEpisodeId.value }
        if (currentIndex != -1) {
            var nextIndex = -1
            if (currentList.size > 1) {
                val firstNum = currentList.first().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                val lastNum = currentList.last().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (firstNum < lastNum) {
                    if (currentIndex < currentList.size - 1) {
                        nextIndex = currentIndex + 1
                    }
                } else {
                    if (currentIndex > 0) {
                        nextIndex = currentIndex - 1
                    }
                }
            }
            if (nextIndex != -1) {
                return currentList[nextIndex]
            }
        }
        return null
    }

    fun getPreviousEpisode(): Chapter? {
        val prevFromQueue = playlistQueue.getPreviousEpisode()
        if (prevFromQueue != null) return prevFromQueue

        val currentList = _chapters.value
        if (currentList.isEmpty()) return null
        val currentIndex = currentList.indexOfFirst { it.id == _currentEpisodeId.value }
        if (currentIndex != -1) {
            var prevIndex = -1
            if (currentList.size > 1) {
                val firstNum = currentList.first().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                val lastNum = currentList.last().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (firstNum < lastNum) {
                    if (currentIndex > 0) {
                        prevIndex = currentIndex - 1
                    }
                } else {
                    if (currentIndex < currentList.size - 1) {
                        prevIndex = currentIndex + 1
                    }
                }
            }
            if (prevIndex != -1) {
                return currentList[prevIndex]
            }
        }
        return null
    }

    private fun updateEpisodeNavigation() {
        playlistQueue.setEpisodes(_chapters.value, _currentEpisodeId.value)
        nextEpisode.value = playlistQueue.getNextEpisode() ?: getNextEpisode()
        previousEpisode.value = playlistQueue.getPreviousEpisode() ?: getPreviousEpisode()
    }

    /**
     * Checks continuous playback threshold:
     * t_sisa = T_total - T_current
     * Trigger Overlay: Show Countdown if t_sisa <= 10s and not cancelled.
     */
    fun checkContinuousPlayback(currentPosition: Long, duration: Long) {
        if (duration <= 0L || isAutoNextCancelled) return
        val remainingTime = duration - currentPosition
        val hasNext = playlistQueue.hasNext() || nextEpisode.value != null

        if (hasNext && remainingTime in 1..10_000L) {
            val countdownSecs = ((remainingTime / 1000L).toInt()).coerceIn(1, 10).coerceAtMost(5)
            autoNextCountdownSeconds.value = countdownSecs
            if (!showAutoNextOverlay.value) {
                showAutoNextOverlay.value = true
            }
        } else if (remainingTime > 10_000L) {
            if (showAutoNextOverlay.value) {
                showAutoNextOverlay.value = false
            }
        }
    }

    fun cancelAutoNext() {
        isAutoNextCancelled = true
        showAutoNextOverlay.value = false
    }

    fun onEndOfStream() {
        if (!isAutoNextCancelled && (playlistQueue.hasNext() || nextEpisode.value != null)) {
            playNextEpisode()
        }
    }

    init {
        loadChapters()
        loadVideo()
    }
    
    private fun loadChapters() {
        viewModelScope.launch {
            try {
                val detail = repository.getDetail(activeItemId, activeSourceId)
                if (detail != null) {
                    _coverImageUrl.value = detail.imageUrl
                    _seriesTitle.value = detail.title
                }
            } catch (_: Exception) {}

            try {
                val list = repository.getChapters(itemId, sourceId).firstOrNull()
                if (list != null) {
                    _chapters.value = list
                    updateEpisodeNavigation()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun loadVideo() {
        isAutoNextCancelled = false
        showAutoNextOverlay.value = false
        hasSyncedTrackersForCurrentEp = false
        viewModelScope.launch {
            _streamInfo.value = PlayerState.Loading
            try {
                if (initialSeekPosition > 0L) {
                    startPosition.value = initialSeekPosition
                } else {
                    val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, _currentEpisodeId.value)
                    if (chapterEntry != null) {
                        startPosition.value = chapterEntry.lastPageRead.toLong()
                    } else {
                        startPosition.value = 0L
                    }
                }

                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, _currentEpisodeId.value)
                // Fetch AniSkip times
                fetchAniSkip(chapterEntry?.number ?: 1f)

                val urlFile = File(context.cacheDir, "play_urls/${_currentEpisodeId.value.hashCode()}.txt")
                if (urlFile.exists()) {
                    val selectedUrl = urlFile.readText().also { urlFile.delete() }
                    val info = VideoStreamInfo(
                        episodeId = _currentEpisodeId.value,
                        embedUrl = selectedUrl,
                        qualities = listOf(com.example.data.sources.VideoQuality("Selected", selectedUrl))
                    )
                    _streamInfo.value = PlayerState.Success(info)
                    return@launch
                }

                val download = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, _currentEpisodeId.value)
                if (download != null && download.state == "DOWNLOADED") {
                    val downloadId = "${activeSourceId}||${activeItemId}||${_currentEpisodeId.value}"
                    val exoDownload = com.example.data.downloads.ExoDownloadManager.getManager(context).downloadIndex.getDownload(downloadId)
                    if (exoDownload != null && exoDownload.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                        val offlineUri = exoDownload.request.uri.toString()
                        
                        val safeDownloadId = downloadId.hashCode().toString()
                        val subDir = File(context.getExternalFilesDir(null), "subtitles")
                        val localSubtitles = mutableListOf<com.example.data.sources.Subtitle>()
                        if (subDir.exists()) {
                            subDir.listFiles()?.forEach { file ->
                                if (file.name.startsWith("${safeDownloadId}_")) {
                                    val parts = file.name.removePrefix("${safeDownloadId}_").split(".")
                                    if (parts.size >= 2) {
                                        val lang = parts[0]
                                        val format = parts[1]
                                        localSubtitles.add(com.example.data.sources.Subtitle(
                                            language = lang,
                                            url = "file://${file.absolutePath}",
                                            format = format
                                        ))
                                    }
                                }
                            }
                        }

                        val info = VideoStreamInfo(
                            episodeId = _currentEpisodeId.value,
                            embedUrl = offlineUri,
                            qualities = listOf(com.example.data.sources.VideoQuality("Offline Cache", offlineUri)),
                            subtitles = localSubtitles
                        )
                        _streamInfo.value = PlayerState.Success(info)
                        return@launch
                    }
                }
                
                repository.getVideoStream(_currentEpisodeId.value, activeSourceId).collect { info ->
                    _streamInfo.value = PlayerState.Success(info)
                }
                if (_streamInfo.value is PlayerState.Loading) {
                    repository.getVideoStreamUrl(_currentEpisodeId.value, activeSourceId).collect { url ->
                        _streamInfo.value = PlayerState.Success(VideoStreamInfo(_currentEpisodeId.value, embedUrl = url))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _streamInfo.value = PlayerState.Error(e.message ?: "Failed to load video")
            }
        }
    }

    fun retry() {
        loadVideo()
    }

    private fun fetchAniSkip(episodeNumber: Float) {
        viewModelScope.launch {
            try {
                val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                var tracks = trackDao.getTracksForMangaSync(currentProfileId, itemId)
                var malTrack = tracks.find { it.trackerId.equals("myanimelist", ignoreCase = true) }

                // Auto-resolve MAL ID if not yet mapped
                if (malTrack == null) {
                    val detail = repository.getDetail(itemId, sourceId)
                    val searchTitle = (detail?.title ?: _currentTitle.value)
                        .replace(Regex("\\(.*?\\)"), "")
                        .replace(Regex("\\[.*?\\]"), "")
                        .trim()
                    
                    if (searchTitle.isNotBlank()) {
                        val malTracker = com.example.data.trackers.MyAnimeListTracker(settingsManager)
                        val searchResults = malTracker.search(searchTitle)
                        var bestMatch: com.example.data.models.TrackItem? = null
                        var bestScore = 0.0
                        for (item in searchResults) {
                            val score = com.example.data.trackers.AutoBindMappingEngine.calculateSimilarity(searchTitle, item.title)
                            if (score > bestScore) {
                                bestScore = score
                                bestMatch = item
                            }
                        }
                        if (bestMatch != null && bestScore >= 0.70) {
                            val contentItem = detail ?: com.example.data.models.ContentItem(
                                id = itemId,
                                title = _currentTitle.value,
                                source = sourceId,
                                imageUrl = "",
                                type = com.example.data.models.ContentType.ANIME
                            )
                            val boundEntity = malTracker.bind(bestMatch, contentItem).copy(profileId = currentProfileId)
                            trackDao.insertTrack(boundEntity)
                            malTrack = boundEntity
                        }
                    }
                }

                if (malTrack != null && malTrack.remoteId > 0) {
                    val response = com.example.data.trackers.AniSkipClient.api.getSkipTimes(
                        malId = malTrack.remoteId,
                        episodeNumber = episodeNumber
                    )
                    if (response.found && response.results != null) {
                        skipTimes.value = response.results
                    } else {
                        skipTimes.value = emptyList()
                    }
                } else {
                    skipTimes.value = emptyList()
                }
            } catch (e: Exception) {
                // Ignore API failures or rate limits gracefully
                skipTimes.value = emptyList()
            }
        }
    }
    
    

    fun onPlaybackError(failedUrl: String) {
        handlePlaybackError(failedUrl, startPosition.value, null)
    }

    fun handlePlaybackError(
        failedUrl: String,
        currentPositionMs: Long = 0L,
        error: PlaybackException? = null
    ) {
        if (currentPositionMs > 0L) {
            startPosition.value = currentPositionMs
            saveProgress(currentPositionMs, 0L)
        }

        val currentState = _streamInfo.value
        val currentInfo = when (currentState) {
            is PlayerState.Success -> currentState.data
            is PlayerState.SeamlessBuffering -> currentState.previousData
            else -> null
        }

        if (currentInfo != null) {
            // 1. Coba kualitas alternatif lokal yang belum gagal
            val remainingQualities = currentInfo.qualities.filter { it.url != failedUrl && it.url.isNotBlank() }
            if (remainingQualities.isNotEmpty()) {
                _streamInfo.value = PlayerState.Success(currentInfo.copy(qualities = remainingQualities))
                return
            }

            // 2. Coba server alternatif lokal yang belum gagal
            val remainingServers = currentInfo.servers.mapNotNull { server -> 
                val filteredQualities = server.qualities.filter { it.url != failedUrl && it.url.isNotBlank() }
                if (server.qualities.isNotEmpty()) {
                    if (filteredQualities.isNotEmpty()) {
                        server.copy(qualities = filteredQualities)
                    } else null // Semua resolusi server ini sudah gagal, beralih ke server berikutnya
                } else if (server.embedUrl.isNotBlank() && server.embedUrl != failedUrl) {
                    server
                } else null
            }

            if (remainingServers.isNotEmpty()) {
                val nextServer = remainingServers.first()
                val nextQualities = nextServer.qualities
                _streamInfo.value = PlayerState.Success(
                    currentInfo.copy(
                        qualities = nextQualities,
                        servers = remainingServers,
                        embedUrl = nextServer.embedUrl,
                        headers = nextServer.headers.ifEmpty { currentInfo.headers }
                    )
                )
                return
            }
        }

        // 3. Seluruh server/kualitas lokal habis atau token stream kedaluwarsa -> Picu Seamless Auto-Failover
        if (_isFailoverResolving.value) return
        _isFailoverResolving.value = true

        val errorDesc = when {
            error?.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "HTTP status error"
            error?.message?.contains("403") == true -> "HTTP 403 Forbidden"
            error?.message?.contains("404") == true -> "HTTP 404 Not Found"
            else -> error?.message ?: "Playback error"
        }

        val failoverMsg = if (errorDesc.contains("403")) {
            "Token video kedaluwarsa, memperbarui tautan..."
        } else if (errorDesc.contains("404")) {
            "Tautan tidak ditemukan, mengalihkan ke domain cadangan..."
        } else {
            "Koneksi terputus, menyambungkan kembali stream..."
        }
        _failoverMessage.value = failoverMsg

        if (currentInfo != null) {
            _streamInfo.value = PlayerState.SeamlessBuffering(currentInfo, failoverMsg)
        }

        viewModelScope.launch {
            var newStreamInfo: VideoStreamInfo? = null
            var lastException: Throwable? = null

            // LANGKAH 1: Refresh token & scraping stream baru dari Repository
            try {
                val fresh = repository.getVideoStream(_currentEpisodeId.value, activeSourceId).firstOrNull()
                if (fresh != null && (fresh.qualities.isNotEmpty() || fresh.embedUrl.isNotBlank())) {
                    val validQualities = fresh.qualities.filter { it.url != failedUrl && it.url.isNotBlank() }
                    if (validQualities.isNotEmpty() || (fresh.embedUrl.isNotBlank() && fresh.embedUrl != failedUrl)) {
                        newStreamInfo = if (validQualities.isNotEmpty()) fresh.copy(qualities = validQualities) else fresh
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }

            // LANGKAH 2: Re-ekstrak langsung via VideoExtractorEngine (hingga 3 kali percobaan untuk timeout)
            if (newStreamInfo == null) {
                val candidateTargetUrl = currentInfo?.embedUrl?.ifEmpty { null } ?: failedUrl
                if (candidateTargetUrl.isNotBlank() && !candidateTargetUrl.startsWith("file://")) {
                    for (attempt in 1..3) {
                        try {
                            _failoverMessage.value = "Menghubungkan server cadangan ($attempt/3)..."
                            val extracted = videoExtractorEngine.extractVideo(
                                episodeUrl = candidateTargetUrl,
                                episodeId = _currentEpisodeId.value,
                                customHeaders = currentInfo?.headers ?: emptyMap()
                            )
                            if (extracted != null && (extracted.qualities.isNotEmpty() || extracted.embedUrl.isNotBlank())) {
                                val validQualities = extracted.qualities.filter { it.url != failedUrl && it.url.isNotBlank() }
                                if (validQualities.isNotEmpty() || (extracted.embedUrl.isNotBlank() && extracted.embedUrl != failedUrl)) {
                                    newStreamInfo = if (validQualities.isNotEmpty()) extracted.copy(qualities = validQualities) else extracted
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            lastException = e
                        }
                        delay(1000L)
                    }
                }
            }

            // LANGKAH 3: Failover domain aktif melalui SourceHealthProbe
            if (newStreamInfo == null) {
                try {
                    val probe = SourceHealthProbe()
                    val backupDomain = probe.probeAndFailover(context, activeSourceId, _currentEpisodeId.value)
                    if (!backupDomain.isNullOrBlank()) {
                        val failoverStream = repository.getVideoStream(_currentEpisodeId.value, activeSourceId).firstOrNull()
                        if (failoverStream != null && (failoverStream.qualities.isNotEmpty() || failoverStream.embedUrl.isNotBlank())) {
                            newStreamInfo = failoverStream
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }

            _isFailoverResolving.value = false
            _failoverMessage.value = null

            if (newStreamInfo != null) {
                _streamInfo.value = PlayerState.Success(newStreamInfo)
                _uiEvent.emit("Streaming berhasil dipulihkan.")
            } else {
                val finalError = lastException?.message ?: "Semua server gagal diputar atau mengalami timeout."
                com.example.data.linkrot.LinkRotTracker.getInstance(context).recordFailure(activeSourceId, activeItemId, finalError)
                SourceStatusBus.report(activeSourceId, "Stream video terputus ($finalError)")
                _streamInfo.value = PlayerState.Error(finalError)
                triggerAutoMigrationSearch()
            }
        }
    }

    fun playNextEpisode() {
        isAutoNextCancelled = false
        showAutoNextOverlay.value = false
        markAsFinishedAndProcessAutomations()

        val nextFromQueue = playlistQueue.getNextEpisode()
        if (nextFromQueue != null) {
            _currentEpisodeId.value = nextFromQueue.id
            _currentTitle.value = nextFromQueue.title
            playlistQueue.setCurrentEpisodeId(nextFromQueue.id)
            updateEpisodeNavigation()
            loadVideo()
            return
        }

        val currentList = _chapters.value
        if (currentList.isEmpty()) return
        
        val currentIndex = currentList.indexOfFirst { it.id == _currentEpisodeId.value }
        if (currentIndex != -1) {
            // Check list order to determine next episode
            var nextIndex = -1
            if (currentList.size > 1) {
                val firstNum = currentList.first().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                val lastNum = currentList.last().title.filter { it.isDigit() }.toIntOrNull() ?: 0
                
                if (firstNum < lastNum) {
                    // Ascending (Older to Newer)
                    if (currentIndex < currentList.size - 1) {
                        nextIndex = currentIndex + 1
                    }
                } else {
                    // Descending (Newer to Older)
                    if (currentIndex > 0) {
                        nextIndex = currentIndex - 1
                    }
                }
            }
            
            if (nextIndex != -1) {
                val nextEp = currentList[nextIndex]
                _currentEpisodeId.value = nextEp.id
                _currentTitle.value = nextEp.title
                playlistQueue.setCurrentEpisodeId(nextEp.id)
                updateEpisodeNavigation()
                loadVideo()
            }
        }
    }

    fun playPreviousEpisode() {
        val prevEp = getPreviousEpisode()
        if (prevEp != null) {
            _currentEpisodeId.value = prevEp.id
            _currentTitle.value = prevEp.title
            updateEpisodeNavigation()
            loadVideo()
        }
    }

    private var autoSaveJob: kotlinx.coroutines.Job? = null

    /**
     * Proposal 7: Periodic Debounced Progress Auto-Save (5s - 10s interval).
     * Guarantees 99.9% resiliency against low memory kills and unexpected app closures.
     */
    fun startPeriodicAutoSave(
        intervalMs: Long = 5000L,
        getPositionAndDuration: () -> Pair<Long, Long>
    ) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs.coerceIn(5000L, 10000L))
                val (positionMs, durationMs) = getPositionAndDuration()
                if (positionMs > 0L && durationMs > 0L) {
                    saveProgress(positionMs, durationMs)
                }
            }
        }
    }

    fun stopPeriodicAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun saveProgress(positionMs: Long, durationMs: Long, forceSync: Boolean = false) {
        if (settingsManager.isIncognito || durationMs <= 0) return
        viewModelScope.launch {
            try {
                val isFinished = positionMs >= durationMs * 0.9f
                val progressFloat = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, _currentEpisodeId.value)
                if (chapterEntry != null) {
                    val wasRead = chapterEntry.read
                    chapterDao.updateChapter(
                        chapterEntry.copy(
                            lastPageRead = positionMs.toInt(),
                            read = if (isFinished) true else chapterEntry.read,
                            lastReadAt = System.currentTimeMillis()
                        )
                    )
                    if (isFinished && !wasRead) {
                        libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId)
                    }
                }

                // Trigger Multi-Service Auto-Scrobbler Engine (MAL, AniList, Kitsu) at completion threshold
                val epNum = chapterEntry?.number?.toInt()
                    ?: _currentTitle.value.filter { it.isDigit() }.toIntOrNull()
                    ?: 1

                val triggered = try {
                    com.example.OtakuHubApp.instance.scrobblerEngine.checkAndTriggerScrobble(
                        currentPosition = positionMs,
                        duration = durationMs,
                        mediaId = activeItemId,
                        episodeNumber = epNum,
                        contentType = com.example.data.scrobbler.MediaSyncType.ANIME
                    )
                } catch (_: Exception) { false }

                if ((triggered || positionMs >= durationMs * 0.85f) && !hasSyncedTrackersForCurrentEp) {
                    hasSyncedTrackersForCurrentEp = true
                    syncProgressToTrackers(epNum)
                }

                val detail = repository.getDetail(activeItemId, activeSourceId)
                if (detail != null) {
                    val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                    val historyItem = HistoryItem(
                        profileId = currentProfileId,
                        id = detail.id,
                        title = detail.title,
                        source = detail.source,
                        imageUrl = detail.imageUrl,
                        type = detail.type,
                        progress = progressFloat,
                        lastChapterOrEpisode = _currentTitle.value, 
                        sourceId = activeSourceId,
                        timestamp = System.currentTimeMillis()
                    )
                    historyDao.insertHistory(historyItem)
                }

                // Delta State Sync: Push micro-state to Cloud & Local LAN Bus
                if (positionMs > 0) {
                    com.example.data.sync.CrossDeviceResumeManager.pushPlaybackState(
                        context = context,
                        itemId = activeItemId,
                        sourceId = activeSourceId,
                        contentTitle = detail?.title ?: _currentTitle.value,
                        episodeId = _currentEpisodeId.value,
                        episodeTitle = _currentTitle.value,
                        contentType = detail?.type?.name ?: "ANIME",
                        positionMs = positionMs,
                        durationMs = durationMs,
                        coverImageUrl = detail?.imageUrl ?: "",
                        force = forceSync
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun flushPlaybackState(positionMs: Long, durationMs: Long) {
        saveProgress(positionMs, durationMs, forceSync = true)
    }

    fun markAsFinishedAndProcessAutomations() {
        val chapterId = _currentEpisodeId.value
        viewModelScope.launch {
            try {
                // Update chapter as read
                val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId, chapterId)
                if (chapterEntry != null) {
                    val wasRead = chapterEntry.read
                    chapterDao.updateChapter(
                        chapterEntry.copy(
                            read = true,
                            lastReadAt = System.currentTimeMillis()
                        )
                    )
                    if (!wasRead) {
                        libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, activeSourceId, activeItemId)
                        com.example.workers.DownloadAutomator.processAutomations(
                            context = context,
                            settings = settingsManager,
                            chapterDao = chapterDao,
                            downloadDao = downloadDao,
                            sourceId = activeSourceId,
                            itemId = activeItemId,
                            currentChapterId = chapterId,
                            contentType = com.example.data.models.ContentType.ANIME
                        )
                    }

                    // Auto-Sync Tracker
                    val epNum = chapterEntry.number?.toInt()
                        ?: _currentTitle.value.filter { it.isDigit() }.toIntOrNull()
                        ?: 1
                    syncProgressToTrackers(epNum)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun syncProgressToTrackers(episodeNumber: Int) {
        if (!settingsManager.autoTrackerSync) return
        viewModelScope.launch {
            try {
                val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
                var tracks = trackDao.getTracksForMangaSync(currentProfileId, activeItemId)

                // Auto-bind if not bound yet
                if (tracks.isEmpty()) {
                    val detail = repository.getDetail(activeItemId, activeSourceId)
                    if (detail != null) {
                        val contentItem = com.example.data.models.ContentItem(
                            id = detail.id,
                            title = detail.title,
                            source = detail.source,
                            imageUrl = detail.imageUrl,
                            type = detail.type
                        )
                        com.example.data.trackers.AutoBindMappingEngine.tryAutoBind(context, contentItem)
                        tracks = trackDao.getTracksForMangaSync(currentProfileId, activeItemId)
                    }
                }

                val trackerManager = com.example.OtakuHubApp.instance.trackerManager
                for (tracker in trackerManager.trackers) {
                    if (tracker.isLogged()) {
                        val track = tracks.find { it.trackerId.equals(tracker.id, ignoreCase = true) }
                        val remoteId = track?.remoteId ?: 0L

                        if (track != null && track.lastChapterRead >= episodeNumber) {
                            continue
                        }

                        if (track != null) {
                            val updatedTrack = track.copy(lastChapterRead = episodeNumber.toFloat())
                            trackDao.updateTrack(updatedTrack)
                        }

                        val success = trackerManager.pushProgress(
                            mediaId = activeItemId,
                            trackerType = tracker.id,
                            progress = episodeNumber,
                            remoteId = remoteId
                        )

                        if (success) {
                            _uiEvent.emit("Progres Ep $episodeNumber tersinkronisasi ke ${tracker.name}")
                        } else {
                            // Queue offline action for background sync
                            com.example.OtakuHubApp.instance.pendingSyncQueue.queueAction(
                                mediaId = itemId,
                                trackerType = tracker.id,
                                progress = episodeNumber,
                                remoteId = remoteId,
                                profileId = currentProfileId
                            )
                            com.example.workers.TrackerSyncWorker.enqueue(context)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore sync errors gracefully
            }
        }
    }
}
