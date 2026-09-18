package com.example.data.downloads

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.example.OtakuHubApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaDownloadListener(private val context: Context) : DownloadManager.Listener {

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        // ID format: sourceId||animeId||episodeId
        val parts = download.request.id.split("||")
        if (parts.size != 3) return
        
        val sourceId = parts[0]
        val animeId = parts[1]
        val episodeId = parts[2]

        val db = (context.applicationContext as OtakuHubApp).database
        val downloadDao = db.downloadDao()
        val chapterDao = db.chapterDao()

        CoroutineScope(Dispatchers.IO).launch {
            val entity = downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, sourceId, animeId, episodeId) ?: return@launch
            
            when (download.state) {
                Download.STATE_COMPLETED -> {
                    downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))
                    val chapter = chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, sourceId, animeId, episodeId)
                    if (chapter != null) {
                        chapterDao.updateChapter(chapter.copy(downloaded = true))
                    }
                }
                Download.STATE_FAILED -> {
                    downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = finalException?.message ?: "Unknown Error"))
                }
                Download.STATE_DOWNLOADING -> {
                    if (entity.state != "DOWNLOADING") {
                        downloadDao.updateDownload(entity.copy(state = "DOWNLOADING"))
                    }
                }
            }
        }
    }
}
