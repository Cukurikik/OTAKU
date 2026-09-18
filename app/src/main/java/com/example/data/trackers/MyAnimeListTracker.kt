package com.example.data.trackers

import com.example.data.NetworkModule
import com.example.data.SettingsManager
import com.example.data.models.ContentItem
import com.example.data.models.TrackEntity
import com.example.data.models.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class MalResponse(val data: List<MalData>?)

@JsonClass(generateAdapter = true)
data class MalData(val node: MalNode?)

@JsonClass(generateAdapter = true)
data class MalNode(
    val id: Long,
    val title: String,
    val main_picture: MalPicture?,
    val num_episodes: Int?,
    val num_chapters: Int?,
    val synopsis: String?
)

@JsonClass(generateAdapter = true)
data class MalPicture(val large: String?, val medium: String?)

class MyAnimeListTracker(private val settings: SettingsManager) : Tracker {
    override val id = "myanimelist"
    override val name = "MyAnimeList"
    override val logoUrl = "https://upload.wikimedia.org/wikipedia/commons/7/7a/MyAnimeList_Logo.png"

    // NOTE: In a real app, this should be injected or hidden. For the sake of demonstration, we'll use a placeholder.
    // Replace with a valid MAL Client ID to make it functional.
    private val clientId = "6114d00ca681b7701d1e15fe11a4987e" 

    override suspend fun search(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val animeRequest = Request.Builder()
            .url("https://api.myanimelist.net/v2/anime?q=${query}&limit=10&fields=id,title,main_picture,num_episodes,synopsis")
            .header("X-MAL-CLIENT-ID", clientId)
            .get()
            .build()
            
        val mangaRequest = Request.Builder()
            .url("https://api.myanimelist.net/v2/manga?q=${query}&limit=10&fields=id,title,main_picture,num_chapters,synopsis")
            .header("X-MAL-CLIENT-ID", clientId)
            .get()
            .build()
            
        val results = mutableListOf<TrackItem>()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(MalResponse::class.java)

        try {
            NetworkModule.okHttpClient.newCall(animeRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val data = adapter.fromJson(bodyStr ?: "")?.data ?: emptyList()
                    for (item in data) {
                        val node = item.node ?: continue
                        results.add(
                            TrackItem(
                                trackerId = id,
                                id = node.id,
                                title = node.title,
                                totalChapters = node.num_episodes ?: 0,
                                coverUrl = node.main_picture?.large ?: node.main_picture?.medium ?: "",
                                summary = node.synopsis ?: ""
                            )
                        )
                    }
                }
            }
            NetworkModule.okHttpClient.newCall(mangaRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val data = adapter.fromJson(bodyStr ?: "")?.data ?: emptyList()
                    for (item in data) {
                        val node = item.node ?: continue
                        results.add(
                            TrackItem(
                                trackerId = id,
                                id = node.id,
                                title = node.title,
                                totalChapters = node.num_chapters ?: 0,
                                coverUrl = node.main_picture?.large ?: node.main_picture?.medium ?: "",
                                summary = node.synopsis ?: ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    override suspend fun bind(trackItem: TrackItem, manga: ContentItem): TrackEntity {
        return TrackEntity(
            mangaId = manga.id,
            trackerId = id,
            remoteId = trackItem.id,
            title = trackItem.title,
            totalChapters = trackItem.totalChapters
        )
    }

    override suspend fun update(track: TrackEntity) = withContext(Dispatchers.IO) {
        if (!isLogged()) return@withContext
        val db = (com.example.OtakuHubApp.instance as com.example.OtakuHubApp).database
        val currentProfileId = com.example.data.ProfileManager.currentProfileIdValue
        val existing = db.pendingSyncsDao().getSync("myanimelist", track.remoteId)
        if (existing != null) {
            db.pendingSyncsDao().updateProgress(existing.id, track.lastChapterRead.toInt(), System.currentTimeMillis())
        } else {
            db.pendingSyncsDao().insert(
                com.example.data.models.PendingSyncEntity(
                    profileId = currentProfileId,
                    trackerType = "myanimelist",
                    remoteId = track.remoteId,
                    itemId = track.mangaId,
                    mediaId = track.mangaId,
                    progress = track.lastChapterRead.toInt(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.workers.TrackerSyncWorker>()
            .setConstraints(constraints)
            .build()
        val context = com.example.OtakuHubApp.instance.applicationContext
        androidx.work.WorkManager.getInstance(context).enqueue(request)
        return@withContext
    }

    override suspend fun getRemoteProgress(remoteId: Long): RemoteProgressState? = withContext(Dispatchers.IO) {
        var token = settings.malToken
        if (token.isEmpty()) return@withContext null
        
        val refreshToken = settings.malRefreshToken
        val expiresAt = settings.malExpiresAt
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis() + 60_000L && refreshToken.isNotEmpty()) {
            val refreshed = com.example.ui.screens.refreshMalOAuthToken(clientId, refreshToken)
            if (refreshed != null) {
                token = refreshed.accessToken
                settings.malToken = refreshed.accessToken
                settings.malRefreshToken = refreshed.refreshToken
                settings.malExpiresAt = System.currentTimeMillis() + (refreshed.expiresIn * 1000L)
            }
        }
        
        try {
            var req = Request.Builder()
                .url("https://api.myanimelist.net/v2/anime/$remoteId?fields=my_list_status")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            var resp = NetworkModule.okHttpClient.newCall(req).execute()
            var body = resp.body?.string() ?: ""
            var code = resp.code
            resp.close()
            
            if (code == 404) {
                req = Request.Builder()
                    .url("https://api.myanimelist.net/v2/manga/$remoteId?fields=my_list_status")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                resp = NetworkModule.okHttpClient.newCall(req).execute()
                body = resp.body?.string() ?: ""
                code = resp.code
                resp.close()
            }
            
            if (code !in 200..299) return@withContext null
            val json = org.json.JSONObject(body)
            val listStatus = json.optJSONObject("my_list_status") ?: return@withContext null
            val progress = if (listStatus.has("num_episodes_watched")) {
                listStatus.optInt("num_episodes_watched", 0)
            } else {
                listStatus.optInt("num_chapters_read", 0)
            }
            val updatedAtStr = listStatus.optString("updated_at")
            val updatedAtMillis = try {
                java.time.Instant.parse(updatedAtStr).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            val status = listStatus.optString("status")
            RemoteProgressState(progress = progress, updatedAt = updatedAtMillis, status = status)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun pushProgress(remoteId: Long, progress: Int): Boolean = withContext(Dispatchers.IO) {
        var token = settings.malToken
        if (token.isEmpty()) return@withContext false
        val refreshToken = settings.malRefreshToken
        val expiresAt = settings.malExpiresAt
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis() + 60_000L && refreshToken.isNotEmpty()) {
            val refreshed = com.example.ui.screens.refreshMalOAuthToken(clientId, refreshToken)
            if (refreshed != null) {
                token = refreshed.accessToken
                settings.malToken = refreshed.accessToken
                settings.malRefreshToken = refreshed.refreshToken
                settings.malExpiresAt = System.currentTimeMillis() + (refreshed.expiresIn * 1000L)
            }
        }
        try {
            val bodyStrAnime = "num_watched_episodes=$progress"
            var request = Request.Builder()
                .url("https://api.myanimelist.net/v2/anime/$remoteId/my_list_status")
                .header("Authorization", "Bearer $token")
                .put(bodyStrAnime.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            var response = NetworkModule.okHttpClient.newCall(request).execute()
            if (response.code == 404) {
                response.close()
                val bodyStrManga = "num_chapters_read=$progress"
                request = Request.Builder()
                    .url("https://api.myanimelist.net/v2/manga/$remoteId/my_list_status")
                    .header("Authorization", "Bearer $token")
                    .put(bodyStrManga.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                response = NetworkModule.okHttpClient.newCall(request).execute()
            }
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun refresh(track: TrackEntity): TrackEntity {
        return track
    }

    override fun isLogged(): Boolean = settings.malToken.isNotEmpty()
    
    override fun logout() {
        settings.malToken = ""
        settings.malRefreshToken = ""
        settings.malExpiresAt = 0L
    }
}
