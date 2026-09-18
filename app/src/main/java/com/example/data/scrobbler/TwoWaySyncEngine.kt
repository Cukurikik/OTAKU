package com.example.data.scrobbler

import android.content.Context
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.data.SettingsManager
import com.example.data.db.AppDatabase
import com.example.data.models.LibraryEntry
import com.example.data.models.TrackEntity
import com.example.data.trackers.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Two-Way Status Sync Engine.
 *
 * Implements bidirectional synchronization for MyAnimeList, AniList, and Kitsu:
 * - Imports remote Watchlists & Reading Lists directly into the local SQLCipher library database.
 * - Resolves progress conflicts (pushes higher local progress, updates higher remote progress).
 * - Synchronizes Watching/Reading and Completed statuses.
 */
class TwoWaySyncEngine(
    private val context: Context,
    private val secureStorage: SecureCredentialStore,
    private val trackerManager: TrackerManager
) {

    private val TAG = "TwoWaySyncEngine"

    private val db: AppDatabase?
        get() = try {
            (context.applicationContext as? OtakuHubApp)?.database ?: OtakuHubApp.instance.database
        } catch (_: Exception) {
            null
        }

    /**
     * Executes two-way status sync across all authenticated platforms.
     */
    suspend fun syncAll(profileId: Long): TwoWaySyncSummary = withContext(Dispatchers.IO) {
        val results = mutableListOf<SyncResult>()
        for (platform in ScrobblePlatform.values()) {
            if (secureStorage.isLogged(platform)) {
                results.add(syncPlatform(platform, profileId))
            }
        }
        TwoWaySyncSummary(results = results)
    }

    /**
     * Executes two-way status sync for a specific platform.
     */
    suspend fun syncPlatform(platform: ScrobblePlatform, profileId: Long): SyncResult = withContext(Dispatchers.IO) {
        if (!secureStorage.isLogged(platform)) {
            return@withContext SyncResult(
                platform = platform,
                importedCount = 0,
                updatedCount = 0,
                pushedCount = 0,
                success = false,
                message = "Not logged in to ${platform.displayName}"
            )
        }

        try {
            val remoteItems = when (platform) {
                ScrobblePlatform.MY_ANIME_LIST -> fetchMalItems()
                ScrobblePlatform.ANI_LIST -> fetchAniListItems()
                ScrobblePlatform.KITSU -> fetchKitsuItems()
            }

            var imported = 0
            var updated = 0
            var pushed = 0

            val currentDatabase = db
            if (currentDatabase == null) {
                return@withContext SyncResult(
                    platform = platform,
                    importedCount = 0,
                    updatedCount = 0,
                    pushedCount = 0,
                    success = false,
                    message = "Database is unavailable"
                )
            }

            val existingTracks = currentDatabase.trackDao().getTracksForProfileSync(profileId)
            val libraryEntries = currentDatabase.libraryDao().getAllEntriesSync(profileId)

            for (remote in remoteItems) {
                val matchedTrack = existingTracks.find {
                    it.trackerId.equals(platform.id, ignoreCase = true) && it.remoteId == remote.remoteId
                }

                if (matchedTrack == null) {
                    // Check if title matches existing library entry
                    val matchedEntry = libraryEntries.find {
                        it.title.equals(remote.title, ignoreCase = true)
                    }

                    val mangaId = matchedEntry?.mangaId ?: "${platform.id}_${remote.remoteId}"
                    val sourceId = matchedEntry?.sourceId ?: platform.id

                    // Create library entry if completely new
                    if (matchedEntry == null) {
                        val newEntry = LibraryEntry(
                            profileId = profileId,
                            sourceId = sourceId,
                            mangaId = mangaId,
                            title = remote.title,
                            thumbnailUrl = remote.coverUrl.ifEmpty { null },
                            addedAt = System.currentTimeMillis(),
                            favorite = true,
                            status = remote.status.name,
                            unreadCount = if (remote.totalCount > remote.progress) remote.totalCount - remote.progress else 0,
                            lastUpdated = System.currentTimeMillis()
                        )
                        currentDatabase.libraryDao().insertLibraryEntry(newEntry)
                        imported++
                    }

                    // Create track binding
                    val newTrack = TrackEntity(
                        profileId = profileId,
                        mangaId = mangaId,
                        trackerId = platform.id,
                        remoteId = remote.remoteId,
                        title = remote.title,
                        lastChapterRead = remote.progress.toFloat(),
                        totalChapters = remote.totalCount,
                        status = if (remote.status == ScrobbleStatus.COMPLETED) 1 else 0,
                        score = remote.score
                    )
                    currentDatabase.trackDao().insertTrack(newTrack)
                } else {
                    // Item already bound: perform two-way conflict resolution
                    if (remote.progress > matchedTrack.lastChapterRead.toInt()) {
                        // Remote is ahead: pull from remote to local
                        val updatedTrack = matchedTrack.copy(
                            lastChapterRead = remote.progress.toFloat(),
                            status = if (remote.status == ScrobbleStatus.COMPLETED) 1 else matchedTrack.status,
                            totalChapters = if (remote.totalCount > 0) remote.totalCount else matchedTrack.totalChapters
                        )
                        currentDatabase.trackDao().insertTrack(updatedTrack)
                        updated++
                    } else if (matchedTrack.lastChapterRead.toInt() > remote.progress) {
                        // Local is ahead: push local progress to remote
                        val success = trackerManager.pushProgress(
                            mediaId = matchedTrack.mangaId,
                            trackerType = platform.id,
                            progress = matchedTrack.lastChapterRead.toInt(),
                            remoteId = matchedTrack.remoteId
                        )
                        if (success) {
                            pushed++
                        }
                    }
                }
            }

            SyncResult(
                platform = platform,
                importedCount = imported,
                updatedCount = updated,
                pushedCount = pushed,
                success = true,
                message = "Berhasil sinkronisasi $imported impor baru, $updated diperbarui, $pushed terdorong."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for ${platform.displayName}: ${e.message}", e)
            SyncResult(
                platform = platform,
                importedCount = 0,
                updatedCount = 0,
                pushedCount = 0,
                success = false,
                message = e.message ?: "Unknown sync error"
            )
        }
    }

    /**
     * Fetches user watchlist (Anime) and reading list (Manga) from MyAnimeList REST API v2.
     */
    suspend fun fetchMalItems(): List<ImportedWatchlistItem> = withContext(Dispatchers.IO) {
        val token = secureStorage.getOrRefreshMalToken() ?: return@withContext emptyList()
        val items = mutableListOf<ImportedWatchlistItem>()

        // 1. Fetch Anime List
        try {
            val animeUrl = "https://api.myanimelist.net/v2/users/@me/animelist?fields=list_status,num_episodes,main_picture&limit=100"
            val animeRequest = Request.Builder()
                .url(animeUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val animeResponse = NetworkModule.okHttpClient.newCall(animeRequest).execute()
            if (animeResponse.isSuccessful) {
                val bodyStr = animeResponse.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    val node = obj.optJSONObject("node") ?: continue
                    val listStatus = obj.optJSONObject("list_status") ?: JSONObject()

                    val remoteId = node.optLong("id")
                    val title = node.optString("title", "Untitled Anime")
                    val coverUrl = node.optJSONObject("main_picture")?.optString("large")
                        ?: node.optJSONObject("main_picture")?.optString("medium") ?: ""
                    val totalEpisodes = node.optInt("num_episodes", 0)
                    val watchedEp = listStatus.optInt("num_episodes_watched", 0)
                    val rawStatus = listStatus.optString("status", "watching")
                    val score = listStatus.optDouble("score", 0.0).toFloat()

                    items.add(
                        ImportedWatchlistItem(
                            remoteId = remoteId,
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = MediaSyncType.ANIME,
                            progress = watchedEp,
                            totalCount = totalEpisodes,
                            status = ScrobbleStatus.fromMal(rawStatus, isAnime = true),
                            platform = ScrobblePlatform.MY_ANIME_LIST,
                            score = score
                        )
                    )
                }
            }
            animeResponse.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching MAL anime list: ${e.message}")
        }

        // 2. Fetch Manga List
        try {
            val mangaUrl = "https://api.myanimelist.net/v2/users/@me/mangalist?fields=list_status,num_chapters,main_picture&limit=100"
            val mangaRequest = Request.Builder()
                .url(mangaUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val mangaResponse = NetworkModule.okHttpClient.newCall(mangaRequest).execute()
            if (mangaResponse.isSuccessful) {
                val bodyStr = mangaResponse.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    val node = obj.optJSONObject("node") ?: continue
                    val listStatus = obj.optJSONObject("list_status") ?: JSONObject()

                    val remoteId = node.optLong("id")
                    val title = node.optString("title", "Untitled Manga")
                    val coverUrl = node.optJSONObject("main_picture")?.optString("large")
                        ?: node.optJSONObject("main_picture")?.optString("medium") ?: ""
                    val totalChapters = node.optInt("num_chapters", 0)
                    val readCh = listStatus.optInt("num_chapters_read", 0)
                    val rawStatus = listStatus.optString("status", "reading")
                    val score = listStatus.optDouble("score", 0.0).toFloat()

                    items.add(
                        ImportedWatchlistItem(
                            remoteId = remoteId,
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = MediaSyncType.MANGA,
                            progress = readCh,
                            totalCount = totalChapters,
                            status = ScrobbleStatus.fromMal(rawStatus, isAnime = false),
                            platform = ScrobblePlatform.MY_ANIME_LIST,
                            score = score
                        )
                    )
                }
            }
            mangaResponse.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching MAL manga list: ${e.message}")
        }

        items
    }

    /**
     * Fetches user watchlist and reading list from AniList GraphQL v2.
     */
    suspend fun fetchAniListItems(): List<ImportedWatchlistItem> = withContext(Dispatchers.IO) {
        val token = secureStorage.getAnilistToken()
        if (token.isEmpty()) return@withContext emptyList()

        val items = mutableListOf<ImportedWatchlistItem>()

        try {
            // First get authenticated user's ID
            val viewerQuery = "{\"query\": \"query { Viewer { id name } }\"}"
            val viewerReq = Request.Builder()
                .url("https://graphql.anilist.co")
                .addHeader("Authorization", "Bearer $token")
                .post(viewerQuery.toRequestBody("application/json".toMediaType()))
                .build()

            val viewerRes = NetworkModule.okHttpClient.newCall(viewerReq).execute()
            if (!viewerRes.isSuccessful) {
                viewerRes.close()
                return@withContext emptyList()
            }
            val viewerBody = viewerRes.body?.string() ?: "{}"
            viewerRes.close()
            val userId = JSONObject(viewerBody)
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
                ?.optInt("id", 0) ?: 0

            if (userId <= 0) return@withContext emptyList()

            // Fetch MediaListCollection for both Anime and Manga
            for (type in listOf("ANIME", "MANGA")) {
                val listQuery = """
                    query (${'$'}userId: Int, ${'$'}type: MediaType) {
                      MediaListCollection(userId: ${'$'}userId, type: ${'$'}type) {
                        lists {
                          name
                          entries {
                            id
                            mediaId
                            status
                            progress
                            score
                            media {
                              id
                              title {
                                romaji
                                english
                              }
                              episodes
                              chapters
                              coverImage {
                                large
                              }
                            }
                          }
                        }
                      }
                    }
                """.trimIndent().replace("\n", " ").replace("\"", "\\\"")

                val payload = "{\"query\": \"$listQuery\", \"variables\": {\"userId\": $userId, \"type\": \"$type\"}}"

                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .addHeader("Authorization", "Bearer $token")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                val res = NetworkModule.okHttpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val resBody = res.body?.string() ?: "{}"
                    val root = JSONObject(resBody)
                    val data = root.optJSONObject("data")?.optJSONObject("MediaListCollection")
                    val lists = data?.optJSONArray("lists") ?: JSONArray()

                    val isAnime = type == "ANIME"
                    for (i in 0 until lists.length()) {
                        val listObj = lists.getJSONObject(i)
                        val entries = listObj.optJSONArray("entries") ?: JSONArray()
                        for (j in 0 until entries.length()) {
                            val entry = entries.getJSONObject(j)
                            val media = entry.optJSONObject("media") ?: continue
                            val remoteId = media.optLong("id")
                            val titleObj = media.optJSONObject("title")
                            val title = titleObj?.optString("english")?.ifEmpty { titleObj.optString("romaji") }
                                ?: titleObj?.optString("romaji") ?: "Unknown"
                            val cover = media.optJSONObject("coverImage")?.optString("large") ?: ""
                            val totalCount = if (isAnime) media.optInt("episodes", 0) else media.optInt("chapters", 0)
                            val progress = entry.optInt("progress", 0)
                            val rawStatus = entry.optString("status", "CURRENT")
                            val score = entry.optDouble("score", 0.0).toFloat()

                            items.add(
                                ImportedWatchlistItem(
                                    remoteId = remoteId,
                                    title = title,
                                    coverUrl = cover,
                                    mediaType = if (isAnime) MediaSyncType.ANIME else MediaSyncType.MANGA,
                                    progress = progress,
                                    totalCount = totalCount,
                                    status = ScrobbleStatus.fromAniList(rawStatus, isAnime),
                                    platform = ScrobblePlatform.ANI_LIST,
                                    score = score
                                )
                            )
                        }
                    }
                }
                res.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching AniList media list: ${e.message}")
        }

        items
    }

    /**
     * Fetches user library entries from Kitsu JSON:API.
     */
    suspend fun fetchKitsuItems(): List<ImportedWatchlistItem> = withContext(Dispatchers.IO) {
        val token = secureStorage.getKitsuToken()
        val userId = secureStorage.getKitsuUserId()
        if (token.isEmpty() || userId.isEmpty()) return@withContext emptyList()

        val items = mutableListOf<ImportedWatchlistItem>()

        try {
            val url = "https://kitsu.io/api/edge/library-entries?filter[user_id]=$userId&include=anime,manga&page[limit]=50"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val res = NetworkModule.okHttpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val bodyStr = res.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val includedArray = json.optJSONArray("included") ?: JSONArray()

                // Build included map (id -> node)
                val mediaMap = mutableMapOf<String, JSONObject>()
                for (k in 0 until includedArray.length()) {
                    val inc = includedArray.getJSONObject(k)
                    val incType = inc.optString("type")
                    val incId = inc.optString("id")
                    mediaMap["${incType}_$incId"] = inc
                }

                for (i in 0 until dataArray.length()) {
                    val entry = dataArray.getJSONObject(i)
                    val attrs = entry.optJSONObject("attributes") ?: JSONObject()
                    val progress = attrs.optInt("progress", 0)
                    val rawStatus = attrs.optString("status", "current")
                    val score = attrs.optDouble("ratingTwenty", 0.0).toFloat()

                    val rels = entry.optJSONObject("relationships") ?: JSONObject()
                    val animeRel = rels.optJSONObject("anime")?.optJSONObject("data")
                    val mangaRel = rels.optJSONObject("manga")?.optJSONObject("data")

                    val isAnime = animeRel != null
                    val mediaRel = animeRel ?: mangaRel ?: continue
                    val mediaType = mediaRel.optString("type")
                    val mediaId = mediaRel.optString("id")

                    val mediaNode = mediaMap["${mediaType}_$mediaId"]
                    val mediaAttrs = mediaNode?.optJSONObject("attributes")

                    val title = mediaAttrs?.optJSONObject("titles")?.optString("en")
                        ?: mediaAttrs?.optJSONObject("titles")?.optString("en_jp")
                        ?: mediaAttrs?.optString("canonicalTitle")
                        ?: "Kitsu Item $mediaId"

                    val coverUrl = mediaAttrs?.optJSONObject("posterImage")?.optString("large")
                        ?: mediaAttrs?.optJSONObject("posterImage")?.optString("medium") ?: ""

                    val totalCount = if (isAnime) {
                        mediaAttrs?.optInt("episodeCount", 0) ?: 0
                    } else {
                        mediaAttrs?.optInt("chapterCount", 0) ?: 0
                    }

                    items.add(
                        ImportedWatchlistItem(
                            remoteId = mediaId.toLongOrNull() ?: 0L,
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = if (isAnime) MediaSyncType.ANIME else MediaSyncType.MANGA,
                            progress = progress,
                            totalCount = totalCount,
                            status = ScrobbleStatus.fromKitsu(rawStatus, isAnime),
                            platform = ScrobblePlatform.KITSU,
                            score = score
                        )
                    )
                }
            }
            res.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Kitsu library entries: ${e.message}")
        }

        items
    }

    companion object {
        fun parseMalResponse(bodyStr: String, defaultMediaType: MediaSyncType = MediaSyncType.ANIME): List<ImportedWatchlistItem> {
            val items = mutableListOf<ImportedWatchlistItem>()
            try {
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    val node = obj.optJSONObject("node") ?: continue
                    val listStatus = obj.optJSONObject("list_status") ?: JSONObject()

                    val remoteId = node.optLong("id")
                    val title = node.optString("title", "Untitled")
                    val coverUrl = node.optJSONObject("main_picture")?.optString("large")
                        ?: node.optJSONObject("main_picture")?.optString("medium") ?: ""
                    val total = if (defaultMediaType == MediaSyncType.ANIME) node.optInt("num_episodes", 0) else node.optInt("num_chapters", 0)
                    val progress = if (defaultMediaType == MediaSyncType.ANIME) listStatus.optInt("num_episodes_watched", 0) else listStatus.optInt("num_chapters_read", 0)
                    val rawStatus = listStatus.optString("status", if (defaultMediaType == MediaSyncType.ANIME) "watching" else "reading")
                    val score = listStatus.optDouble("score", 0.0).toFloat()

                    items.add(
                        ImportedWatchlistItem(
                            remoteId = remoteId,
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = defaultMediaType,
                            progress = progress,
                            totalCount = total,
                            status = ScrobbleStatus.fromMal(rawStatus, defaultMediaType == MediaSyncType.ANIME),
                            platform = ScrobblePlatform.MY_ANIME_LIST,
                            score = score
                        )
                    )
                }
            } catch (_: Exception) {}
            return items
        }

        fun parseAniListResponse(resBody: String): List<ImportedWatchlistItem> {
            val items = mutableListOf<ImportedWatchlistItem>()
            try {
                val root = JSONObject(resBody)
                val data = root.optJSONObject("data")?.optJSONObject("MediaListCollection")
                val lists = data?.optJSONArray("lists") ?: JSONArray()

                for (i in 0 until lists.length()) {
                    val listObj = lists.getJSONObject(i)
                    val entries = listObj.optJSONArray("entries") ?: JSONArray()
                    for (j in 0 until entries.length()) {
                        val entry = entries.getJSONObject(j)
                        val media = entry.optJSONObject("media") ?: continue
                        val remoteId = media.optLong("id").takeIf { it > 0 } ?: media.optLong("mediaId")
                        val titleObj = media.optJSONObject("title")
                        val title = titleObj?.optString("english")?.ifEmpty { titleObj.optString("romaji") }
                            ?: titleObj?.optString("romaji") ?: "Unknown"
                        val cover = media.optJSONObject("coverImage")?.optString("large") ?: ""
                        val typeStr = media.optString("type", "ANIME")
                        val isAnime = typeStr.equals("ANIME", ignoreCase = true)
                        val totalCount = if (isAnime) media.optInt("episodes", 0) else media.optInt("chapters", 0)
                        val progress = entry.optInt("progress", 0)
                        val rawStatus = entry.optString("status", "CURRENT")
                        val score = entry.optDouble("score", 0.0).toFloat()

                        items.add(
                            ImportedWatchlistItem(
                                remoteId = remoteId,
                                title = title,
                                coverUrl = cover,
                                mediaType = if (isAnime) MediaSyncType.ANIME else MediaSyncType.MANGA,
                                progress = progress,
                                totalCount = totalCount,
                                status = ScrobbleStatus.fromAniList(rawStatus, isAnime),
                                platform = ScrobblePlatform.ANI_LIST,
                                score = score
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
            return items
        }

        fun parseKitsuResponse(bodyStr: String): List<ImportedWatchlistItem> {
            val items = mutableListOf<ImportedWatchlistItem>()
            try {
                val json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data") ?: JSONArray()
                val includedArray = json.optJSONArray("included") ?: JSONArray()

                val mediaMap = mutableMapOf<String, JSONObject>()
                for (k in 0 until includedArray.length()) {
                    val inc = includedArray.getJSONObject(k)
                    val incType = inc.optString("type")
                    val incId = inc.optString("id")
                    mediaMap["${incType}_$incId"] = inc
                }

                for (i in 0 until dataArray.length()) {
                    val entry = dataArray.getJSONObject(i)
                    val attrs = entry.optJSONObject("attributes") ?: JSONObject()
                    val progress = attrs.optInt("progress", 0)
                    val rawStatus = attrs.optString("status", "current")
                    val score = attrs.optDouble("ratingTwenty", 0.0).toFloat()

                    val rels = entry.optJSONObject("relationships") ?: JSONObject()
                    val animeRel = rels.optJSONObject("anime")?.optJSONObject("data")
                    val mangaRel = rels.optJSONObject("manga")?.optJSONObject("data")

                    val isAnime = animeRel != null
                    val mediaRel = animeRel ?: mangaRel ?: continue
                    val mediaType = mediaRel.optString("type")
                    val mediaId = mediaRel.optString("id")

                    val mediaNode = mediaMap["${mediaType}_$mediaId"]
                    val mediaAttrs = mediaNode?.optJSONObject("attributes")

                    val title = mediaAttrs?.optJSONObject("titles")?.optString("en")
                        ?: mediaAttrs?.optJSONObject("titles")?.optString("en_jp")
                        ?: mediaAttrs?.optString("canonicalTitle")
                        ?: "Kitsu Item $mediaId"

                    val coverUrl = mediaAttrs?.optJSONObject("posterImage")?.optString("large")
                        ?: mediaAttrs?.optJSONObject("posterImage")?.optString("medium") ?: ""

                    val totalCount = if (isAnime) {
                        mediaAttrs?.optInt("episodeCount", 0) ?: 0
                    } else {
                        mediaAttrs?.optInt("chapterCount", 0) ?: 0
                    }

                    items.add(
                        ImportedWatchlistItem(
                            remoteId = mediaId.toLongOrNull() ?: 0L,
                            title = title,
                            coverUrl = coverUrl,
                            mediaType = if (isAnime) MediaSyncType.ANIME else MediaSyncType.MANGA,
                            progress = progress,
                            totalCount = totalCount,
                            status = ScrobbleStatus.fromKitsu(rawStatus, isAnime),
                            platform = ScrobblePlatform.KITSU,
                            score = score
                        )
                    )
                }
            } catch (_: Exception) {}
            return items
        }
    }
}
