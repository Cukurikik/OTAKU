package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ContentRepository
import com.example.data.db.ChapterDao
import com.example.data.db.DownloadDao
import com.example.data.db.LibraryDao
import com.example.data.db.PageDownloadDao
import com.example.data.models.DownloadEntity
import com.example.data.models.DownloadPayload
import com.example.data.models.SubtitlePayload
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class DownloadItemUiState(
    val entity: DownloadEntity,
    val title: String,
    val chapterTitle: String,
    val thumbnailUrl: String,
    val progress: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isVideo: Boolean,
    val downloadSpeedKbps: Long = 0L
)

class DownloadViewModel(
    private val downloadDao: DownloadDao,
    private val pageDownloadDao: PageDownloadDao,
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao,
    private val repository: ContentRepository? = null
) : ViewModel() {

    private val allDownloadsFlow = downloadDao.getAllDownloads(com.example.data.ProfileManager.currentProfileIdValue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    private val _uiDownloads = MutableStateFlow<List<DownloadItemUiState>>(emptyList())
    val uiDownloads: StateFlow<List<DownloadItemUiState>> = _uiDownloads
    
    private val previousBytesMap = mutableMapOf<String, Long>()
    private val lastRefreshTimeMap = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            allDownloadsFlow.collect {
                refreshUiState(it)
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (allDownloadsFlow.value.any { it.state == "DOWNLOADING" || it.state == "QUEUE" }) {
                    refreshUiState(allDownloadsFlow.value)
                }
            }
        }
    }

    private suspend fun refreshUiState(downloads: List<DownloadEntity>) {
        val uiList = mutableListOf<DownloadItemUiState>()
        for (download in downloads) {
            val libraryEntry = libraryDao.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, download.sourceId, download.mangaId)
            val title = libraryEntry?.title ?: "Unknown Title"
            val thumbnailUrl = libraryEntry?.thumbnailUrl ?: ""
            val chapterEntry = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, download.sourceId, download.mangaId, download.chapterId)
            val chapterTitle = chapterEntry?.name ?: "Chapter ${download.chapterId}"
            
            val isVideo = download.sourceId.lowercase().let { 
                it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") || 
                it.contains("anichin") || it.contains("kura") || it.contains("kaze") || 
                it.contains("anoboy") || it.contains("nime") || it.contains("drama") || 
                it.contains("drakor") || it.contains("viki") || it.contains("licil") || 
                it.contains("film") || it.contains("jucelebrity") 
            }
            
            var progress = 0f
            var bytesDownloaded = 0L
            var bytesTotal = 0L
            var speedKbps = 0L
            val downloadIdKey = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
            
            if (isVideo) {
                if (download.state == "DOWNLOADED") {
                    progress = 1f
                } else if (download.state == "DOWNLOADING") {
                    val downloadId = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
                    progress = com.example.workers.VideoDownloadService.downloadProgressMap[downloadId]?.value ?: 0f
                    bytesDownloaded = com.example.workers.VideoDownloadService.downloadBytesMap[downloadId]?.value ?: 0L
                    bytesTotal = com.example.workers.VideoDownloadService.downloadTotalBytesMap[downloadId]?.value ?: 0L
                }
            } else {
                val pages = pageDownloadDao.getPagesList(download.sourceId, download.mangaId, download.chapterId)
                if (pages.isNotEmpty()) {
                    val downloadedPages = pages.count { it.state == "DOWNLOADED" }
                    progress = downloadedPages.toFloat() / pages.size
                    bytesDownloaded = pages.sumOf { it.bytes }
                } else if (download.state == "DOWNLOADED") {
                    progress = 1f
                }
            }
            
            if (download.state == "DOWNLOADING") {
                val now = System.currentTimeMillis()
                val prevBytes = previousBytesMap[downloadIdKey] ?: bytesDownloaded
                val prevTime = lastRefreshTimeMap[downloadIdKey] ?: now
                val timeDiff = now - prevTime
                if (timeDiff > 0 && bytesDownloaded > prevBytes) {
                    speedKbps = ((bytesDownloaded - prevBytes) / timeDiff) * 1000 / 1024
                }
                previousBytesMap[downloadIdKey] = bytesDownloaded
                lastRefreshTimeMap[downloadIdKey] = now
            } else {
                previousBytesMap.remove(downloadIdKey)
                lastRefreshTimeMap.remove(downloadIdKey)
            }
            
            uiList.add(
                DownloadItemUiState(
                    entity = download,
                    title = title,
                    chapterTitle = chapterTitle,
                    thumbnailUrl = thumbnailUrl,
                    progress = progress.coerceIn(0f, 1f),
                    downloadedBytes = bytesDownloaded,
                    totalBytes = bytesTotal,
                    isVideo = isVideo,
                    downloadSpeedKbps = speedKbps
                )
            )
        }
        _uiDownloads.value = uiList
    }

    fun pauseDownload(download: DownloadEntity) {
        viewModelScope.launch {
            if (download.state == "DOWNLOADING" || download.state == "QUEUE") {
                downloadDao.updateDownload(download.copy(state = "PAUSED"))
                val downloadId = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
                com.example.workers.VideoDownloadService.cancelActiveDownload(com.example.OtakuHubApp.instance, downloadId)
            }
        }
    }

    fun resumeDownload(download: DownloadEntity) {
        viewModelScope.launch {
            if (download.state == "PAUSED" || download.state == "ERROR") {
                val isVideo = download.sourceId.lowercase().let { 
                    it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") || 
                    it.contains("anichin") || it.contains("kura") || it.contains("kaze") || 
                    it.contains("anoboy") || it.contains("nime") || it.contains("drama") || 
                    it.contains("drakor") || it.contains("viki") || it.contains("licil") || 
                    it.contains("film") || it.contains("jucelebrity") 
                }
                if (isVideo) {
                    if (!download.directUrl.isNullOrEmpty()) {
                        downloadDao.updateDownload(download.copy(state = "QUEUE", errorMessage = null))
                        val payload = DownloadPayload(
                            sourceId = download.sourceId,
                            mangaId = download.mangaId,
                            chapterId = download.chapterId,
                            directUrl = download.directUrl,
                            mimeType = download.mimeType,
                            qualityLabel = download.qualityLabel
                        )
                        com.example.workers.VideoDownloadService.enqueueDirectDownload(com.example.OtakuHubApp.instance, payload)
                        com.example.workers.VideoDownloadService.start(com.example.OtakuHubApp.instance)
                    } else if (repository != null) {
                        try {
                            val info = repository.getVideoStream(download.chapterId, download.sourceId).firstOrNull {
                                it.qualities.isNotEmpty() || it.embedUrl.isNotEmpty()
                            }
                            val directUrl = info?.bestQuality?.url ?: info?.embedUrl
                            if (!directUrl.isNullOrEmpty()) {
                                val mime = if (directUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
                                val subPayloads = info?.subtitles?.map { SubtitlePayload(it.language, it.url, it.format) } ?: emptyList()
                                val headersJson = Json.encodeToString(info?.headers ?: emptyMap())
                                val subsJson = Json.encodeToString(subPayloads)
                                val updated = download.copy(
                                    state = "QUEUE",
                                    directUrl = directUrl,
                                    headersJson = headersJson,
                                    mimeType = mime,
                                    subtitlesJson = subsJson,
                                    errorMessage = null
                                )
                                downloadDao.updateDownload(updated)
                                val payload = DownloadPayload(
                                    sourceId = download.sourceId,
                                    mangaId = download.mangaId,
                                    chapterId = download.chapterId,
                                    directUrl = directUrl,
                                    headers = info?.headers ?: emptyMap(),
                                    mimeType = mime,
                                    subtitles = subPayloads
                                )
                                com.example.workers.VideoDownloadService.enqueueDirectDownload(com.example.OtakuHubApp.instance, payload)
                                com.example.workers.VideoDownloadService.start(com.example.OtakuHubApp.instance)
                            } else {
                                downloadDao.updateDownload(download.copy(state = "ERROR", errorMessage = "Gagal menyelesaikan direct stream URL"))
                            }
                        } catch (e: Exception) {
                            downloadDao.updateDownload(download.copy(state = "ERROR", errorMessage = "Gagal ekstraksi: ${e.message}"))
                        }
                    } else {
                        downloadDao.updateDownload(download.copy(state = "ERROR", errorMessage = "Repository tidak tersedia"))
                    }
                } else {
                    downloadDao.updateDownload(download.copy(state = "QUEUE", errorMessage = null))
                    val context = com.example.OtakuHubApp.instance
                    val data = androidx.work.Data.Builder()
                        .putString("sourceId", download.sourceId)
                        .putString("mangaId", download.mangaId)
                        .putString("chapterId", download.chapterId)
                        .build()
                    val request = androidx.work.OneTimeWorkRequest.Builder(com.example.workers.MangaDownloadWorker::class.java)
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .setInputData(data)
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                        "download_${download.sourceId}_${download.mangaId}_${download.chapterId}",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        request
                    )
                }
            }
        }
    }

    fun cancelDownload(download: DownloadEntity) {
        viewModelScope.launch {
            downloadDao.deleteDownload(download)
            try {
                val downloadId = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
                com.example.workers.VideoDownloadService.cancelActiveDownload(com.example.OtakuHubApp.instance, downloadId)
                androidx.work.WorkManager.getInstance(com.example.OtakuHubApp.instance)
                    .cancelUniqueWork("download_${download.sourceId}_${download.mangaId}_${download.chapterId}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val isVideo = download.sourceId.lowercase().let { 
                     it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") || 
                     it.contains("anichin") || it.contains("kura") || it.contains("kaze") || 
                     it.contains("anoboy") || it.contains("nime") || it.contains("drama") || 
                     it.contains("drakor") || it.contains("viki") || it.contains("licil") || 
                     it.contains("film") || it.contains("jucelebrity") 
                 }
                if (!isVideo) {
                    val context = com.example.OtakuHubApp.instance
                    val cbzFile = java.io.File(context.filesDir, "downloads/${download.sourceId}/${download.mangaId}/${download.chapterId}.cbz")
                    if (cbzFile.exists()) cbzFile.delete()
                    val stagingDir = java.io.File(context.filesDir, "downloads/${download.sourceId}/${download.mangaId}/tmp_${download.chapterId}")
                    if (stagingDir.exists()) stagingDir.deleteRecursively()

                    val pages = pageDownloadDao.getPagesList(download.sourceId, download.mangaId, download.chapterId)
                    pages.forEach { page ->
                        page.localUri?.let { uri ->
                            if (!uri.startsWith("cbz://")) {
                                java.io.File(uri.removePrefix("file://")).delete()
                            }
                        }
                    }
                    pageDownloadDao.deletePages(pages)
                } else {
                    val fileMp4 = java.io.File(com.example.OtakuHubApp.instance.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub/${download.mangaId}-${download.chapterId}.mp4")
                    val fileTs = java.io.File(com.example.OtakuHubApp.instance.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub/${download.mangaId}-${download.chapterId}.ts")
                    if (fileMp4.exists()) fileMp4.delete()
                    if (fileTs.exists()) fileTs.delete()
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * Schedules a resilient background download using WorkManager with network constraints
     * and exponential backoff retry policy.
     */
    fun scheduleResilientDownload(
        context: android.content.Context,
        downloadUrl: String,
        contentId: String
    ) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val downloadData = androidx.work.workDataOf(
            "KEY_DOWNLOAD_URL" to downloadUrl,
            "KEY_CONTENT_ID" to contentId
        )

        val downloadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.workers.DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .setInputData(downloadData)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$contentId",
            androidx.work.ExistingWorkPolicy.KEEP,
            downloadWorkRequest
        )
    }

    /**
     * Schedules a resilient download with full metadata and exponential backoff.
     */
    fun scheduleResilientDownload(
        context: android.content.Context,
        downloadUrl: String,
        sourceId: String,
        mangaId: String,
        chapterId: String
    ) {
        val contentId = "${sourceId}||${mangaId}||${chapterId}"
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val downloadData = androidx.work.workDataOf(
            "KEY_DOWNLOAD_URL" to downloadUrl,
            "KEY_CONTENT_ID" to contentId,
            "KEY_SOURCE_ID" to sourceId,
            "KEY_MANGA_ID" to mangaId,
            "KEY_CHAPTER_ID" to chapterId
        )

        val downloadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.workers.DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .setInputData(downloadData)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download_${sourceId}_${mangaId}_${chapterId}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            downloadWorkRequest
        )
    }

    fun pauseAll() {
        viewModelScope.launch {
            val currentList = _uiDownloads.value
            currentList.forEach { item ->
                if (item.entity.state == "DOWNLOADING" || item.entity.state == "QUEUE") {
                    pauseDownload(item.entity)
                }
            }
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            val currentList = _uiDownloads.value
            currentList.forEach { item ->
                if (item.entity.state == "PAUSED" || item.entity.state == "ERROR") {
                    resumeDownload(item.entity)
                }
            }
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            val currentList = _uiDownloads.value
            currentList.forEach { item ->
                if (item.entity.state == "DOWNLOADED") {
                    downloadDao.deleteDownload(item.entity)
                }
            }
        }
    }

    fun retryDownload(download: DownloadEntity) {
        resumeDownload(download)
    }

    /**
     * Exports a downloaded comic or video to an external SAF document URI.
     */
    fun exportMediaToSaf(
        context: android.content.Context,
        download: DownloadEntity,
        targetUri: android.net.Uri,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = com.example.storage.SafMediaManager.exportMediaEntityToSaf(context, download, targetUri)
            result.onSuccess { bytes ->
                val mb = bytes / (1024f * 1024f)
                onResult(true, "Berhasil diekspor ke SAF (%.1f MB)".format(mb))
            }.onFailure { error ->
                onResult(false, "Gagal mengekspor: ${error.message}")
            }
        }
    }

    /**
     * Imports a media file (.cbz/.zip or .mp4/.mkv) from a SAF source URI into local downloads.
     */
    fun importMediaFromSaf(
        context: android.content.Context,
        sourceUri: android.net.Uri,
        isVideo: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (isVideo) {
                val result = com.example.storage.SafMediaManager.importVideoFromSaf(context, sourceUri)
                result.onSuccess {
                    onResult(true, "Berhasil mengimpor video ke antrean lokal")
                }.onFailure { error ->
                    onResult(false, "Gagal mengimpor video: ${error.message}")
                }
            } else {
                val result = com.example.storage.SafMediaManager.importComicFromSaf(context, sourceUri)
                result.onSuccess {
                    onResult(true, "Berhasil mengimpor komik CBZ ke antrean lokal")
                }.onFailure { error ->
                    onResult(false, "Gagal mengimpor komik: ${error.message}")
                }
            }
        }
    }

    /**
     * Exports a downloaded video file (or HLS manifest/segments) to the System Gallery as an MP4 solid file using FFmpeg stream copying (-c copy).
     */
    fun exportVideoToSystemGallery(
        context: android.content.Context,
        download: DownloadEntity,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val appMoviesDir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub")
            val safeName = "${download.mangaId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}-${download.chapterId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
            val localMp4File = java.io.File(appMoviesDir, "$safeName.mp4")
            val localM3u8File = java.io.File(appMoviesDir, "$safeName.m3u8")

            val inputPath = when {
                localMp4File.exists() -> localMp4File.absolutePath
                localM3u8File.exists() -> localM3u8File.absolutePath
                !download.directUrl.isNullOrBlank() -> download.directUrl!!
                else -> {
                    onResult(false, "Berkas video lokal tidak ditemukan untuk diekspor")
                    return@launch
                }
            }

            val title = "${download.mangaId}_${download.chapterId}"
            val (success, message) = com.example.download.FFmpegExporterEngine.exportToGalleryMp4(
                context,
                inputPath,
                title
            )
            onResult(success, message)
        }
    }
}

