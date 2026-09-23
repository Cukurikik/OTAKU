package com.example.data.repository

import com.example.data.ProfileManager
import com.example.data.db.ChapterDao
import com.example.data.db.HistoryDao
import com.example.data.models.ChapterEntry
import com.example.data.models.HistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * PlaybackStateRepository:
 * Centralized repository for persistent real-time playback position, progress tracking,
 * and last watched episode resolution across Anime and Drama series.
 */
class PlaybackStateRepository(
    private val historyDao: HistoryDao? = null,
    private val chapterDao: ChapterDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val inMemoryPositionCache = ConcurrentHashMap<String, Long>()
    private val inMemoryDurationCache = ConcurrentHashMap<String, Long>()
    private val inMemoryEpisodeNumberCache = ConcurrentHashMap<Int, Long>()
    private val lastWatchedEpisodeCache = ConcurrentHashMap<String, String>()

    /**
     * Saves playback position for an episode identified by episode string ID or URL.
     */
    fun savePosition(
        episodeId: String,
        currentPosMs: Long,
        totalDurationMs: Long = 0L,
        isFinished: Boolean = false,
        sourceId: String = "",
        seriesId: String = "",
        seriesTitle: String = "",
        imageUrl: String = ""
    ) {
        if (episodeId.isBlank()) return
        inMemoryPositionCache[episodeId] = currentPosMs
        if (totalDurationMs > 0L) {
            inMemoryDurationCache[episodeId] = totalDurationMs
        }
        if (seriesId.isNotBlank()) {
            lastWatchedEpisodeCache[seriesId] = episodeId
        }

        scope.launch {
            savePositionToDatabase(
                episodeId = episodeId,
                currentPosMs = currentPosMs,
                totalDurationMs = totalDurationMs,
                isFinished = isFinished,
                sourceId = sourceId,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                imageUrl = imageUrl
            )
        }
    }

    /**
     * Saves playback position using numeric episode number (convenience method for AutoQueueEngine).
     */
    fun savePosition(
        episodeNumber: Int,
        currentPosMs: Long,
        totalDurationMs: Long = 0L,
        isFinished: Boolean = false
    ) {
        inMemoryEpisodeNumberCache[episodeNumber] = currentPosMs
        savePosition(
            episodeId = "ep_$episodeNumber",
            currentPosMs = currentPosMs,
            totalDurationMs = totalDurationMs,
            isFinished = isFinished
        )
    }

    private suspend fun savePositionToDatabase(
        episodeId: String,
        currentPosMs: Long,
        totalDurationMs: Long,
        isFinished: Boolean,
        sourceId: String,
        seriesId: String,
        seriesTitle: String,
        imageUrl: String
    ) = withContext(Dispatchers.IO) {
        try {
            val profileId = ProfileManager.currentProfileIdValue
            val progressFloat = if (totalDurationMs > 0L) {
                (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            // 1. Update ChapterEntry if chapterDao is available
            if (chapterDao != null && sourceId.isNotBlank() && seriesId.isNotBlank()) {
                val existing = chapterDao.getChapter(profileId, sourceId, seriesId, episodeId)
                if (existing != null) {
                    chapterDao.updateProgress(
                        profileId = profileId,
                        sourceId = sourceId,
                        mangaId = seriesId,
                        chapterId = episodeId,
                        lastPageRead = currentPosMs.toInt(),
                        lastReadAt = System.currentTimeMillis(),
                        isRead = isFinished || existing.read
                    )
                } else {
                    chapterDao.insertChapter(
                        ChapterEntry(
                            profileId = profileId,
                            sourceId = sourceId,
                            mangaId = seriesId,
                            chapterId = episodeId,
                            name = episodeId,
                            url = episodeId,
                            dateUpload = System.currentTimeMillis(),
                            number = episodeId.filter { it.isDigit() || it == '.' }.toFloatOrNull(),
                            lastPageRead = currentPosMs.toInt(),
                            lastReadAt = System.currentTimeMillis(),
                            read = isFinished
                        )
                    )
                }
            }

            // 2. Update HistoryItem if historyDao is available
            if (historyDao != null && seriesId.isNotBlank()) {
                val historyItem = HistoryItem(
                    profileId = profileId,
                    id = seriesId,
                    title = seriesTitle.ifBlank { seriesId },
                    source = sourceId,
                    imageUrl = imageUrl,
                    type = com.example.data.models.ContentType.ANIME,
                    progress = progressFloat,
                    lastChapterOrEpisode = episodeId,
                    sourceId = sourceId,
                    timestamp = System.currentTimeMillis()
                )
                historyDao.insertHistory(historyItem)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves the last saved position in milliseconds for an episode.
     */
    fun getPosition(episodeId: String): Long {
        return inMemoryPositionCache[episodeId] ?: 0L
    }

    /**
     * Retrieves the last saved position for a numeric episode number.
     */
    fun getPosition(episodeNumber: Int): Long {
        return inMemoryEpisodeNumberCache[episodeNumber] ?: getPosition("ep_$episodeNumber")
    }

    /**
     * Retrieves the last watched episode ID for a series.
     */
    fun getLastWatchedEpisode(seriesId: String): String? {
        return lastWatchedEpisodeCache[seriesId]
    }

    /**
     * Clears cached positions in memory.
     */
    fun clearCache() {
        inMemoryPositionCache.clear()
        inMemoryDurationCache.clear()
        inMemoryEpisodeNumberCache.clear()
        lastWatchedEpisodeCache.clear()
    }
}
