package com.example.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import com.example.OtakuHubApp
import com.example.data.downloads.ExoDownloadManager
import com.example.data.models.DownloadPayload
import com.example.data.models.SubtitlePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

class VideoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    com.example.R.string.video_download_channel_name,
    com.example.R.string.video_download_description
) {

    companion object {
        private const val CHANNEL_ID = "video_download_channel"
        private const val NOTIFICATION_ID = 1001
        
        val downloadProgressMap = mutableMapOf<String, MutableStateFlow<Float>>()
        val downloadBytesMap = mutableMapOf<String, MutableStateFlow<Long>>()
        val downloadTotalBytesMap = mutableMapOf<String, MutableStateFlow<Long>>()
        
        fun start(context: Context) {
            val intent = Intent(context, VideoDownloadService::class.java)
            intent.action = "PROCESS_QUEUE"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun enqueueDirectDownload(
            context: Context,
            payload: DownloadPayload
        ) {
            val downloadId = "${payload.sourceId}||${payload.mangaId}||${payload.chapterId}"
            val mime = payload.mimeType ?: if (payload.directUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
            
            // Build full headers map including Cookie if provided
            val headersMap = payload.headers.toMutableMap()
            if (!payload.cookies.isNullOrBlank() && !headersMap.containsKey("Cookie")) {
                headersMap["Cookie"] = payload.cookies
            }

            val request = ExoDownloadManager.buildDownloadRequest(
                mediaUri = Uri.parse(payload.directUrl),
                headers = headersMap,
                contentId = downloadId,
                mimeType = mime
            )
            
            DownloadService.sendAddDownload(
                context,
                VideoDownloadService::class.java,
                request,
                false
            )
        }

        fun cancelActiveDownload(context: Context, downloadId: String) {
            DownloadService.sendRemoveDownload(context, VideoDownloadService::class.java, downloadId, false)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationHelper: DownloadNotificationHelper
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
        
        // Execute background Storage Janitor task to clear orphaned .tmp files and staging older than 24h
        serviceScope.launch {
            try {
                com.example.storage.StoragePathManager.runStorageJanitorTask(this@VideoDownloadService)
            } catch (_: Exception) {}
        }

        val manager = ExoDownloadManager.getManager(this)
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                val downloadId = download.request.id
                val parts = downloadId.split("||")
                if (parts.size != 3) return
                val sourceId = parts[0]
                val mangaId = parts[1]
                val chapterId = parts[2]
                
                serviceScope.launch {
                    val app = applicationContext as OtakuHubApp
                    val downloadDao = app.database.downloadDao()
                    val chapterDao = app.database.chapterDao()
                    val entity = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, sourceId, mangaId, chapterId) ?: return@launch
                    
                    if (!downloadProgressMap.containsKey(downloadId)) {
                        downloadProgressMap[downloadId] = MutableStateFlow(0f)
                        downloadBytesMap[downloadId] = MutableStateFlow(0L)
                        downloadTotalBytesMap[downloadId] = MutableStateFlow(0L)
                    }
                    
                    downloadProgressMap[downloadId]?.value = download.percentDownloaded / 100f
                    downloadBytesMap[downloadId]?.value = download.bytesDownloaded
                    
                    when (download.state) {
                        Download.STATE_COMPLETED -> {
                            downloadProgressMap[downloadId]?.value = 1f
                            downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))
                            val chapter = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, sourceId, mangaId, chapterId)
                            if (chapter != null) {
                                chapterDao.updateChapter(chapter.copy(downloaded = true))
                            }
                        }
                        Download.STATE_FAILED -> {
                            downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = finalException?.message))
                        }
                        Download.STATE_DOWNLOADING -> {
                            downloadDao.updateDownload(entity.copy(state = "DOWNLOADING", startedAt = System.currentTimeMillis()))
                        }
                        Download.STATE_STOPPED -> {
                            downloadDao.updateDownload(entity.copy(state = "PAUSED"))
                        }
                    }
                }
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): DownloadManager {
        return ExoDownloadManager.getManager(this)
    }

    override fun getScheduler(): Scheduler? {
        return null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "PROCESS_QUEUE") {
            serviceScope.launch {
                val app = applicationContext as OtakuHubApp
                val downloadDao = app.database.downloadDao()
                val queue = downloadDao.getAllDownloads(com.example.data.ProfileManager.currentProfileIdValue).firstOrNull()?.filter { it.state == "QUEUE" }
                
                queue?.forEach { entity ->
                    val isVideo = entity.sourceId.lowercase().let { 
                         it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") ||
                         it.contains("anichin") || it.contains("kura") || it.contains("kaze") ||
                         it.contains("anoboy") || it.contains("nime") || it.contains("drama") ||
                         it.contains("drakor") || it.contains("viki") || it.contains("licil") ||
                         it.contains("film") || it.contains("jucelebrity") 
                    }
                    if (isVideo) {
                        val targetUrl = entity.directUrl
                        if (!targetUrl.isNullOrEmpty()) {
                            val downloadId = "${entity.sourceId}||${entity.mangaId}||${entity.chapterId}"
                            val safeDownloadId = downloadId.hashCode().toString()
                            
                            // Download subtitles before video if present
                            if (!entity.subtitlesJson.isNullOrEmpty()) {
                                try {
                                    val subtitles = json.decodeFromString<List<SubtitlePayload>>(entity.subtitlesJson)
                                    subtitles.forEach { subtitle ->
                                        try {
                                            val subRequest = okhttp3.Request.Builder().url(subtitle.url).build()
                                            val subResponse = com.example.data.NetworkModule.okHttpClient.newCall(subRequest).execute()
                                            if (subResponse.isSuccessful) {
                                                val subBody = subResponse.body?.bytes()
                                                if (subBody != null) {
                                                    val subDir = java.io.File(com.example.storage.StoragePathManager.getRootDownloadDir(this@VideoDownloadService), "subtitles")
                                                    com.example.storage.StoragePathManager.ensureNoMedia(subDir)
                                                    val safeLang = subtitle.language.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                                    val subFile = java.io.File(subDir, "${safeDownloadId}_${safeLang}.${subtitle.format}")
                                                    java.io.FileOutputStream(subFile).use { fos ->
                                                        fos.write(subBody)
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // ignore subtitle download failure
                                        }
                                    }
                                } catch (e: Exception) {
                                    // ignore json parse error
                                }
                            }

                            val mime = entity.mimeType ?: if (targetUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
                            val headersMap = mutableMapOf<String, String>()
                            if (!entity.headersJson.isNullOrEmpty()) {
                                try {
                                    val parsed = json.decodeFromString<Map<String, String>>(entity.headersJson)
                                    headersMap.putAll(parsed)
                                } catch (_: Exception) {}
                            }
                            if (!entity.cookies.isNullOrBlank() && !headersMap.containsKey("Cookie")) {
                                headersMap["Cookie"] = entity.cookies
                            }

                            val request = ExoDownloadManager.buildDownloadRequest(
                                mediaUri = Uri.parse(targetUrl),
                                headers = headersMap,
                                contentId = downloadId,
                                mimeType = mime
                            )
                            DownloadService.sendAddDownload(
                                this@VideoDownloadService,
                                VideoDownloadService::class.java,
                                request,
                                false
                            )
                        } else {
                            downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Direct stream URL belum di-resolve dari foreground"))
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
