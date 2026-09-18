package com.example.data.scrobbler

import android.content.Context
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.data.SettingsManager
import com.example.data.models.ContentType
import com.example.data.models.TrackEntity
import com.example.data.trackers.KitsuApi
import com.example.data.trackers.KitsuDataNode
import com.example.data.trackers.KitsuLibraryEntryAttributes
import com.example.data.trackers.KitsuLibraryEntryRelationships
import com.example.data.trackers.KitsuLibraryEntryRequest
import com.example.data.trackers.KitsuLibraryEntryRequestData
import com.example.data.trackers.KitsuLibraryEntryUpdateRequest
import com.example.data.trackers.KitsuLibraryEntryUpdateRequestData
import com.example.data.trackers.KitsuRelationshipData
import com.example.data.trackers.TrackerManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Multi-Service Auto-Scrobbler Engine.
 *
 * Automatically detects completion threshold (85% video playback or final manga page),
 * and pushes updates to AniList, MyAnimeList, and Kitsu APIs in the background.
 */
class ScrobblerEngine(
    private val context: Context,
    private val secureStorage: SecureCredentialStore,
    private val trackerManager: TrackerManager,
    var config: ScrobbleConfig = ScrobbleConfig()
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "ScrobblerEngine"

    // Guard against duplicate scrobble dispatches for the same item & episode
    private val scrobbledKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks video playback and triggers auto-scrobble if progress >= 85%.
     *
     * @param currentPosition Current playback position in ms
     * @param duration Video total duration in ms
     * @param mediaId Local media item ID
     * @param episodeNumber Current episode number
     * @param totalEpisodes Known total episodes (0 if unknown)
     * @param contentType Content type (ANIME / DRAMA)
     * @return true if scrobble was triggered in this call
     */
    fun checkAndTriggerScrobble(
        currentPosition: Long,
        duration: Long,
        mediaId: String,
        episodeNumber: Int = 1,
        totalEpisodes: Int = 0,
        contentType: MediaSyncType = MediaSyncType.ANIME
    ): Boolean {
        val scrobbleKey = "anime_${mediaId}_ep_${episodeNumber}"
        if (scrobbledKeys.contains(scrobbleKey)) return false

        val shouldTrigger = ScrobbleThresholdDetector.shouldTriggerScrobble(
            currentPosition = currentPosition,
            duration = duration,
            threshold = config.completionThreshold
        )

        if (shouldTrigger) {
            scrobbledKeys.add(scrobbleKey)
            val isCompleted = totalEpisodes > 0 && episodeNumber >= totalEpisodes

            scope.launch {
                updateExternalWatchlist(
                    mediaId = mediaId,
                    progress = episodeNumber,
                    isCompleted = isCompleted,
                    totalCount = totalEpisodes,
                    mediaType = contentType
                )
            }
            return true
        }
        return false
    }

    /**
     * Checks manga reading progress and triggers auto-scrobble if user is on the final page.
     *
     * @param currentPage Current page index (0-indexed)
     * @param totalPages Total pages in chapter
     * @param mediaId Local manga ID
     * @param chapterNumber Chapter number
     * @param totalChapters Known total chapters (0 if unknown)
     * @return true if scrobble was triggered in this call
     */
    fun checkAndTriggerMangaScrobble(
        currentPage: Int,
        totalPages: Int,
        mediaId: String,
        chapterNumber: Int = 1,
        totalChapters: Int = 0
    ): Boolean {
        val scrobbleKey = "manga_${mediaId}_ch_${chapterNumber}"
        if (scrobbledKeys.contains(scrobbleKey)) return false

        val shouldTrigger = ScrobbleThresholdDetector.shouldTriggerMangaScrobble(
            currentPage = currentPage,
            totalPages = totalPages
        )

        if (shouldTrigger) {
            scrobbledKeys.add(scrobbleKey)
            val isCompleted = totalChapters > 0 && chapterNumber >= totalChapters

            scope.launch {
                updateExternalWatchlist(
                    mediaId = mediaId,
                    progress = chapterNumber,
                    isCompleted = isCompleted,
                    totalCount = totalChapters,
                    mediaType = MediaSyncType.MANGA
                )
            }
            return true
        }
        return false
    }

    /**
     * Executes external API updates to MyAnimeList, AniList, and Kitsu in background.
     * Also synchronizes local SQLCipher database state (TrackEntity).
     */
    suspend fun updateExternalWatchlist(
        mediaId: String,
        progress: Int,
        isCompleted: Boolean = false,
        totalCount: Int = 0,
        mediaType: MediaSyncType = MediaSyncType.ANIME
    ): Map<ScrobblePlatform, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<ScrobblePlatform, Boolean>()

        val db = try {
            (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
        } catch (_: Exception) {
            null
        }

        val profileId = com.example.data.ProfileManager.currentProfileIdValue
        val tracks = db?.trackDao()?.getTracksForMangaSync(profileId, mediaId) ?: emptyList()

        for (platformId in config.targetPlatforms) {
            val platform = ScrobblePlatform.fromId(platformId) ?: continue
            if (!secureStorage.isLogged(platform)) continue

            val boundTrack = tracks.find { it.trackerId.equals(platform.id, ignoreCase = true) }
            val remoteId = boundTrack?.remoteId ?: mediaId.toLongOrNull() ?: 0L

            if (remoteId <= 0L) {
                // Not bound yet, cannot push to unmapped media
                continue
            }

            // Don't downgrade progress
            if (boundTrack != null && boundTrack.lastChapterRead >= progress && !isCompleted) {
                results[platform] = true
                continue
            }

            var success = false
            try {
                success = when (platform) {
                    ScrobblePlatform.MY_ANIME_LIST -> pushToMal(remoteId, progress, isCompleted, mediaType)
                    ScrobblePlatform.ANI_LIST -> pushToAniList(remoteId, progress, isCompleted)
                    ScrobblePlatform.KITSU -> pushToKitsu(remoteId, progress, isCompleted, mediaType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing to ${platform.displayName}: ${e.message}")
                success = false
            }

            results[platform] = success

            if (success) {
                // Update local TrackEntity
                if (db != null && boundTrack != null) {
                    val statusInt = if (isCompleted) 1 else 0
                    val updatedTrack = boundTrack.copy(
                        lastChapterRead = progress.toFloat(),
                        status = statusInt,
                        totalChapters = if (totalCount > 0) totalCount else boundTrack.totalChapters
                    )
                    db.trackDao().insertTrack(updatedTrack)
                }
            } else {
                // Queue for offline synchronization
                try {
                    db?.pendingSyncsDao()?.let { dao ->
                        dao.insert(
                            com.example.data.models.PendingSyncEntity(
                                profileId = profileId,
                                trackerType = platform.id,
                                remoteId = remoteId,
                                itemId = mediaId,
                                mediaId = mediaId,
                                progress = progress,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    com.example.workers.TrackerSyncWorker.enqueue(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to queue offline sync: ${e.message}")
                }
            }
        }

        results
    }

    private suspend fun pushToMal(
        remoteId: Long,
        progress: Int,
        isCompleted: Boolean,
        mediaType: MediaSyncType
    ): Boolean {
        val token = secureStorage.getOrRefreshMalToken() ?: return false
        val isAnime = mediaType == MediaSyncType.ANIME
        val endpoint = if (isAnime) "anime" else "manga"

        val statusVal = if (isCompleted) "completed" else if (isAnime) "watching" else "reading"
        val countParam = if (isAnime) "num_watched_episodes" else "num_chapters_read"
        val bodyStr = "$countParam=$progress&status=$statusVal"

        val request = Request.Builder()
            .url("https://api.myanimelist.net/v2/$endpoint/$remoteId/my_list_status")
            .header("Authorization", "Bearer $token")
            .put(bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = NetworkModule.okHttpClient.newCall(request).execute()
        val ok = response.isSuccessful
        response.close()
        return ok
    }

    private suspend fun pushToAniList(
        remoteId: Long,
        progress: Int,
        isCompleted: Boolean
    ): Boolean {
        val token = secureStorage.getAnilistToken()
        if (token.isEmpty()) return false

        val statusVal = if (isCompleted) "COMPLETED" else "CURRENT"
        val query = """
            mutation SaveMedia(${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                    id
                    progress
                    status
                }
            }
        """.trimIndent().replace("\n", " ").replace("\"", "\\\"")

        val payload = "{\"query\": \"$query\", \"variables\": {\"mediaId\": $remoteId, \"progress\": $progress, \"status\": \"$statusVal\"}}"

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .addHeader("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = NetworkModule.okHttpClient.newCall(request).execute()
        val ok = response.isSuccessful
        response.close()
        return ok
    }

    private suspend fun pushToKitsu(
        remoteId: Long,
        progress: Int,
        isCompleted: Boolean,
        mediaType: MediaSyncType
    ): Boolean {
        val token = secureStorage.getKitsuToken()
        val userId = secureStorage.getKitsuUserId()
        if (token.isEmpty() || userId.isEmpty()) return false

        val type = if (mediaType == MediaSyncType.ANIME) "anime" else "manga"
        val statusVal = if (isCompleted) "completed" else "current"

        val retrofit = Retrofit.Builder()
            .baseUrl("https://kitsu.io/api/edge/")
            .client(NetworkModule.okHttpClient)
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                )
            )
            .build()

        val api = retrofit.create(KitsuApi::class.java)
        val authHeader = "Bearer $token"
        val mediaIdStr = remoteId.toString()

        val entryResponse = if (type == "anime") {
            api.getLibraryEntry(authHeader, userId, mediaIdStr, null)
        } else {
            api.getLibraryEntry(authHeader, userId, null, mediaIdStr)
        }

        if (!entryResponse.isSuccessful) return false

        val existingEntryId = entryResponse.body()?.data?.firstOrNull()?.id
        return if (existingEntryId != null) {
            val updateReq = KitsuLibraryEntryUpdateRequest(
                data = KitsuLibraryEntryUpdateRequestData(
                    id = existingEntryId,
                    attributes = KitsuLibraryEntryAttributes(
                        progress = progress,
                        status = statusVal
                    )
                )
            )
            val updateRes = api.updateLibraryEntry(authHeader, existingEntryId, updateReq)
            updateRes.isSuccessful
        } else {
            val createReq = KitsuLibraryEntryRequest(
                data = KitsuLibraryEntryRequestData(
                    attributes = KitsuLibraryEntryAttributes(
                        progress = progress,
                        status = statusVal
                    ),
                    relationships = KitsuLibraryEntryRelationships(
                        user = KitsuRelationshipData(KitsuDataNode(id = userId, type = "users")),
                        media = KitsuRelationshipData(KitsuDataNode(id = mediaIdStr, type = type))
                    )
                )
            )
            val createRes = api.createLibraryEntry(authHeader, createReq)
            createRes.isSuccessful
        }
    }

    fun resetSessionCache() {
        scrobbledKeys.clear()
    }
}
