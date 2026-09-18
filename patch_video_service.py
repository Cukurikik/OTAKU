import sys

new_content = """package com.example.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull

class VideoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    0,
    0
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
            context.startService(intent)
        }

        fun cancelActiveDownload(context: Context, downloadId: String) {
            DownloadService.sendRemoveDownload(context, VideoDownloadService::class.java, downloadId, false)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        createNotificationChannel()
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
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
                    val entity = downloadDao.getDownload(sourceId, mangaId, chapterId) ?: return@launch
                    
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
                            val chapter = chapterDao.getChapter(sourceId, mangaId, chapterId)
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
                val repo = app.repository
                val queue = downloadDao.getAllDownloads().firstOrNull()?.filter { it.state == "QUEUE" }
                
                queue?.forEach { entity ->
                    val isVideo = entity.sourceId.lowercase().let { 
                         it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") ||
                         it.contains("anichin") || it.contains("kura") || it.contains("kaze") ||
                         it.contains("anoboy") || it.contains("nime") || it.contains("drama") ||
                         it.contains("drakor") || it.contains("viki") || it.contains("licil") ||
                         it.contains("film") || it.contains("jucelebrity") 
                    }
                    if (isVideo) {
                        val streamInfo = repo.getVideoStream(entity.chapterId, entity.sourceId).firstOrNull { 
                            it.qualities.isNotEmpty() || it.embedUrl.isNotEmpty() 
                        }
                        val bestQuality = streamInfo?.bestQuality
                        val targetUrl = bestQuality?.url ?: streamInfo?.embedUrl
                        
                        if (!targetUrl.isNullOrEmpty()) {
                            val downloadId = "${entity.sourceId}||${entity.mangaId}||${entity.chapterId}"
                            val request = DownloadRequest.Builder(downloadId, Uri.parse(targetUrl))
                                .setMimeType(if (targetUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
                                .build()
                            DownloadService.sendAddDownload(
                                this@VideoDownloadService,
                                VideoDownloadService::class.java,
                                request,
                                false
                            )
                        } else {
                            downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Stream not found"))
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
}
"""

with open('app/src/main/java/com/example/workers/VideoDownloadService.kt', 'w') as f:
    f.write(new_content)
