package com.example.data.trackers
import android.content.Context

import com.example.data.NetworkModule
import com.example.data.SettingsManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
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
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class KitsuResponse(val data: List<KitsuData>?)

@JsonClass(generateAdapter = true)
data class KitsuData(
    val id: String,
    val type: String,
    val attributes: KitsuAttributes?
)

@JsonClass(generateAdapter = true)
data class KitsuAttributes(
    val canonicalTitle: String?,
    val synopsis: String?,
    val episodeCount: Int?,
    val chapterCount: Int?,
    val posterImage: KitsuPosterImage?
)

@JsonClass(generateAdapter = true)
data class KitsuPosterImage(val original: String?)

class KitsuTracker(private val context: Context, private val settings: SettingsManager) : Tracker {
    override val id = "kitsu"
    override val name = "Kitsu"
    override val logoUrl = "https://kitsu.io/kitsu-256-ed442f7567271af715884ca06a9f6871.png"

    override suspend fun search(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val animeRequest = Request.Builder()
            .url("https://kitsu.io/api/edge/anime?filter[text]=${query}&page[limit]=10")
            .get()
            .build()
            
        val mangaRequest = Request.Builder()
            .url("https://kitsu.io/api/edge/manga?filter[text]=${query}&page[limit]=10")
            .get()
            .build()
            
        val results = mutableListOf<TrackItem>()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(KitsuResponse::class.java)

        try {
            NetworkModule.okHttpClient.newCall(animeRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    val data = adapter.fromJson(bodyStr ?: "")?.data ?: emptyList()
                    for (item in data) {
                        results.add(
                            TrackItem(
                                trackerId = id,
                                id = item.id.toLongOrNull() ?: 0L,
                                title = item.attributes?.canonicalTitle ?: "Unknown",
                                totalChapters = item.attributes?.episodeCount ?: 0,
                                coverUrl = item.attributes?.posterImage?.original ?: "",
                                summary = item.attributes?.synopsis ?: ""
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
                        results.add(
                            TrackItem(
                                trackerId = id,
                                id = item.id.toLongOrNull() ?: 0L,
                                title = item.attributes?.canonicalTitle ?: "Unknown",
                                totalChapters = item.attributes?.chapterCount ?: 0,
                                coverUrl = item.attributes?.posterImage?.original ?: "",
                                summary = item.attributes?.synopsis ?: ""
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
        val existing = db.pendingSyncsDao().getSync("kitsu", track.remoteId)
        if (existing != null) {
            db.pendingSyncsDao().updateProgress(existing.id, track.lastChapterRead.toInt(), System.currentTimeMillis())
        } else {
            db.pendingSyncsDao().insert(
                com.example.data.models.PendingSyncEntity(
                    profileId = currentProfileId,
                    trackerType = "kitsu",
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
        val token = settings.kitsuToken
        val userId = settings.kitsuUserId
        if (token.isEmpty()) return@withContext null
        try {
            val url = if (userId.isNotEmpty()) {
                "https://kitsu.io/api/edge/library-entries?filter[user_id]=$userId&filter[anime_id]=$remoteId"
            } else {
                "https://kitsu.io/api/edge/library-entries/$remoteId"
            }
            var request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.api+json")
                .get()
                .build()
            var response = NetworkModule.okHttpClient.newCall(request).execute()
            var bodyStr = response.body?.string() ?: ""
            response.close()

            var json = JSONObject(bodyStr)
            var attributes = if (json.has("data")) {
                val dataObj = json.opt("data")
                if (dataObj is org.json.JSONArray && dataObj.length() > 0) {
                    dataObj.getJSONObject(0).optJSONObject("attributes")
                } else if (dataObj is JSONObject) {
                    dataObj.optJSONObject("attributes")
                } else null
            } else null

            if (attributes == null && userId.isNotEmpty()) {
                // Try manga filter
                val mangaUrl = "https://kitsu.io/api/edge/library-entries?filter[user_id]=$userId&filter[manga_id]=$remoteId"
                request = Request.Builder()
                    .url(mangaUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.api+json")
                    .get()
                    .build()
                response = NetworkModule.okHttpClient.newCall(request).execute()
                bodyStr = response.body?.string() ?: ""
                response.close()
                json = JSONObject(bodyStr)
                val dataArray = json.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    attributes = dataArray.getJSONObject(0).optJSONObject("attributes")
                }
            }

            if (attributes == null) return@withContext null
            val progress = attributes.optInt("progress", 0)
            val updatedAtStr = attributes.optString("updatedAt")
            val updatedAtMillis = try {
                java.time.Instant.parse(updatedAtStr).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            val status = attributes.optString("status")
            RemoteProgressState(progress = progress, updatedAt = updatedAtMillis, status = status)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun pushProgress(remoteId: Long, progress: Int): Boolean = withContext(Dispatchers.IO) {
        val token = settings.kitsuToken
        val userId = settings.kitsuUserId
        if (token.isEmpty()) return@withContext false
        try {
            // Check if entry already exists
            var entryId: String? = null
            if (userId.isNotEmpty()) {
                val checkUrl = "https://kitsu.io/api/edge/library-entries?filter[user_id]=$userId&filter[anime_id]=$remoteId"
                val checkReq = Request.Builder()
                    .url(checkUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.api+json")
                    .get()
                    .build()
                val checkResp = NetworkModule.okHttpClient.newCall(checkReq).execute()
                val checkBody = checkResp.body?.string() ?: ""
                checkResp.close()
                val checkJson = JSONObject(checkBody)
                val dataArr = checkJson.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    entryId = dataArr.getJSONObject(0).optString("id")
                }
            }

            val targetId = entryId ?: remoteId.toString()
            val payload = """
                {
                  "data": {
                    "id": "$targetId",
                    "type": "libraryEntries",
                    "attributes": {
                      "progress": $progress,
                      "status": "current"
                    }
                  }
                }
            """.trimIndent()

            val req = Request.Builder()
                .url("https://kitsu.io/api/edge/library-entries/$targetId")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/vnd.api+json")
                .header("Accept", "application/vnd.api+json")
                .patch(payload.toRequestBody("application/vnd.api+json".toMediaType()))
                .build()
            val resp = NetworkModule.okHttpClient.newCall(req).execute()
            val success = resp.isSuccessful
            resp.close()
            success
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun refresh(track: TrackEntity): TrackEntity {
        return track
    }

    override fun isLogged(): Boolean = settings.kitsuToken.isNotEmpty()
    
    override fun logout() {
        settings.kitsuToken = ""
    }
}
