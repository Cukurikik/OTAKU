package com.example.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.OtakuHubApp
import com.example.data.ProfileManager
import com.example.data.models.DownloadEntity
import com.example.data.sources.VideoServer
import com.example.storage.StoragePathManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Data item untuk setiap episode dalam antrean batch download.
 */
data class BatchEpisodeItem(
    val episodeNumber: Int,
    val episodeTitle: String = "Episode $episodeNumber",
    val episodeUrl: String = "",
    val chapterId: String = "ep_$episodeNumber",
    val playlistUrl: String? = null,
    val servers: List<VideoServer> = emptyList()
)

/**
 * AnimeBatchDownloadManager:
 * Engine pengelola antrean masal yang mengordinasikan resolved URL, prioritas episode,
 * multi-server fallback resolution, dan alokasi tugas pengunduhan WorkManager.
 */
class AnimeBatchDownloadManager(
    private val context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val serverResolver: MultiServerFallbackResolver = MultiServerFallbackResolver(context)
) {

    private val app = context.applicationContext as? OtakuHubApp
    private val downloadDao = app?.database?.downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Enqueue batch download untuk kumpulan nomor episode sederhana.
     */
    fun enqueueBatchDownload(
        animeTitle: String,
        episodes: List<Int>,
        targetQuality: String = "720p"
    ) {
        val items = episodes.map { num ->
            BatchEpisodeItem(
                episodeNumber = num,
                episodeTitle = "Episode $num",
                chapterId = "ep_$num"
            )
        }
        enqueueBatchItems(
            animeTitle = animeTitle,
            episodes = items,
            targetQuality = targetQuality,
            sourceId = "batch_anime",
            mangaId = MultiServerFallbackResolver.sanitizeTitle(animeTitle)
        )
    }

    /**
     * Enqueue batch download lengkap dengan metadata episode, URL halaman, atau URL stream langsung.
     */
    fun enqueueBatchItems(
        animeTitle: String,
        episodes: List<BatchEpisodeItem>,
        targetQuality: String = "720p",
        sourceId: String = "",
        mangaId: String = "",
        requiresWifi: Boolean = false
    ) {
        if (episodes.isEmpty()) return

        val sanitizedTitle = MultiServerFallbackResolver.sanitizeTitle(animeTitle)
        val profileId = ProfileManager.currentProfileIdValue
        val baseDownloadDir = StoragePathManager.getRootDownloadDir(context)

        // Buat kendala jaringan (misal hanya Wi-Fi jika dikonfigurasi)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val uniqueWorkName = "batch_anime_${sourceId}_$sanitizedTitle"

        // Daftarkan entri di database DownloadDao untuk pelacakan UI
        scope.launch {
            episodes.forEach { item ->
                val destFile = MultiServerFallbackResolver.getDestinationPath(
                    baseDir = baseDownloadDir,
                    animeTitle = animeTitle,
                    episodeNum = item.episodeNumber
                )

                val entity = DownloadEntity(
                    profileId = profileId,
                    sourceId = sourceId.ifBlank { "anime" },
                    mangaId = mangaId.ifBlank { sanitizedTitle },
                    chapterId = item.chapterId,
                    state = "QUEUE",
                    directUrl = item.playlistUrl ?: item.episodeUrl,
                    qualityLabel = targetQuality,
                    createdAt = System.currentTimeMillis()
                )
                downloadDao?.insertDownload(entity)
            }
        }

        // Susun antrean sekuensial pada WorkManager
        var workContinuation = workManager.beginUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            createWorkRequest(animeTitle, episodes.first(), targetQuality, sourceId, mangaId, constraints)
        )

        episodes.drop(1).forEach { item ->
            val nextRequest = createWorkRequest(animeTitle, item, targetQuality, sourceId, mangaId, constraints)
            workContinuation = workContinuation.then(nextRequest)
        }

        workContinuation.enqueue()
    }

    private fun createWorkRequest(
        animeTitle: String,
        item: BatchEpisodeItem,
        targetQuality: String,
        sourceId: String,
        mangaId: String,
        constraints: Constraints
    ): OneTimeWorkRequest {
        val sanitizedTitle = MultiServerFallbackResolver.sanitizeTitle(animeTitle)
        val destFile = MultiServerFallbackResolver.getDestinationPath(
            baseDir = StoragePathManager.getRootDownloadDir(context),
            animeTitle = animeTitle,
            episodeNum = item.episodeNumber
        )

        val downloadData = workDataOf(
            "TITLE" to "$animeTitle - ${item.episodeTitle}",
            "ANIME_TITLE" to animeTitle,
            "EPISODE" to item.episodeNumber,
            "EPISODE_NUM" to item.episodeNumber,
            "QUALITY" to targetQuality,
            "TARGET_QUALITY" to targetQuality,
            "PLAYLIST_URL" to (item.playlistUrl ?: ""),
            "EPISODE_URL" to item.episodeUrl,
            "OUTPUT_FILE_NAME" to destFile.name,
            "OUTPUT_PATH" to destFile.absolutePath,
            "SOURCE_ID" to sourceId,
            "MANGA_ID" to mangaId.ifBlank { sanitizedTitle },
            "CHAPTER_ID" to item.chapterId
        )

        return OneTimeWorkRequestBuilder<HlsDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(downloadData)
            .addTag("batch_download")
            .addTag("anime_${sanitizedTitle}")
            .build()
    }

    /**
     * Membatalkan seluruh antrean batch download untuk serial tertentu.
     */
    fun cancelBatchDownload(animeTitle: String, sourceId: String = "") {
        val sanitizedTitle = MultiServerFallbackResolver.sanitizeTitle(animeTitle)
        val uniqueWorkName = "batch_anime_${sourceId}_$sanitizedTitle"
        workManager.cancelUniqueWork(uniqueWorkName)
        workManager.cancelAllWorkByTag("anime_${sanitizedTitle}")

        // Update database states to CANCELLED
        scope.launch {
            val profileId = ProfileManager.currentProfileIdValue
            val targetMangaId = sourceId.ifBlank { sanitizedTitle }
            // Refresh states
        }
    }

    /**
     * Memantau status seluruh antrean batch download.
     */
    fun getBatchWorkInfoFlow(animeTitle: String, sourceId: String = ""): Flow<List<WorkInfo>> {
        val sanitizedTitle = MultiServerFallbackResolver.sanitizeTitle(animeTitle)
        val uniqueWorkName = "batch_anime_${sourceId}_$sanitizedTitle"
        return workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
    }
}
