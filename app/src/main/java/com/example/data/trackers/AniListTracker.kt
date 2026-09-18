package com.example.data.trackers

import com.example.data.NetworkModule
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
import com.example.data.SettingsManager

@JsonClass(generateAdapter = true)
data class AniListResponse(val data: AniListData?)

@JsonClass(generateAdapter = true)
data class AniListData(val Page: AniListPage?)

@JsonClass(generateAdapter = true)
data class AniListPage(val media: List<AniListMedia>?)

@JsonClass(generateAdapter = true)
data class AniListMedia(
    val id: Long,
    val title: AniListTitle?,
    val chapters: Int?,
    val episodes: Int?,
    val coverImage: AniListCover?,
    val description: String?
)

@JsonClass(generateAdapter = true)
data class AniListTitle(val romaji: String?, val english: String?, val native: String?)

@JsonClass(generateAdapter = true)
data class AniListCover(val large: String?)

class AniListTracker(private val settings: SettingsManager) : Tracker {
    override val id = "anilist"
    override val name = "AniList"
    override val logoUrl = "https://anilist.co/img/icons/android-chrome-512x512.png"

    override suspend fun search(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val jsonQuery = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 20) {
                media(search: ${'$'}search) {
                  id
                  title { romaji english native }
                  chapters
                  coverImage { large }
                  description
                }
              }
            }
        """.trimIndent()
        
        val flatQuery = jsonQuery.replace("\n", " ").replace("\"", "\\\"")
        val payload = "{\"query\": \"${flatQuery}\", \"variables\": {\"search\": \"${query}\"}}"

        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
            
        try {
            val bodyStr = NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string()
            } ?: return@withContext emptyList()
            
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(AniListResponse::class.java)
            val aniListResponse = adapter.fromJson(bodyStr)
            
            val results = mutableListOf<TrackItem>()
            val mediaList = aniListResponse?.data?.Page?.media ?: emptyList()
            for (media in mediaList) {
                val title = media.title?.english?.ifEmpty { media.title.romaji } ?: media.title?.romaji ?: "Unknown"
                results.add(
                    TrackItem(
                        trackerId = id,
                        id = media.id,
                        title = title,
                        totalChapters = media.chapters ?: media.episodes ?: 0,
                        coverUrl = media.coverImage?.large ?: "",
                        summary = media.description ?: ""
                    )
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
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
        val existing = db.pendingSyncsDao().getSync("anilist", track.remoteId)
        if (existing != null) {
            db.pendingSyncsDao().updateProgress(existing.id, track.lastChapterRead.toInt(), System.currentTimeMillis())
        } else {
            db.pendingSyncsDao().insert(
                com.example.data.models.PendingSyncEntity(
                    profileId = currentProfileId,
                    trackerType = "anilist",
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
        val token = settings.anilistToken
        if (token.isEmpty()) return@withContext null
        try {
            val query = """
                query (${'$'}mediaId: Int) {
                  MediaList(mediaId: ${'$'}mediaId) {
                    progress
                    updatedAt
                    status
                  }
                }
            """.trimIndent()
            val flatQuery = query.replace("\n", " ").replace("\"", "\\\"")
            val payload = "{\"query\": \"$flatQuery\", \"variables\": {\"mediaId\": $remoteId}}"
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val body = response.body?.string() ?: ""
            response.close()
            val json = org.json.JSONObject(body)
            val mediaList = json.optJSONObject("data")?.optJSONObject("MediaList") ?: return@withContext null
            val progress = mediaList.optInt("progress", 0)
            val updatedAtSec = mediaList.optLong("updatedAt", 0L)
            val updatedAtMillis = if (updatedAtSec > 0) updatedAtSec * 1000L else System.currentTimeMillis()
            val status = mediaList.optString("status")
            RemoteProgressState(progress = progress, updatedAt = updatedAtMillis, status = status)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun pushProgress(remoteId: Long, progress: Int): Boolean = withContext(Dispatchers.IO) {
        val token = settings.anilistToken
        if (token.isEmpty()) return@withContext false
        try {
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                        id
                        progress
                        updatedAt
                    }
                }
            """.trimIndent()
            val flatQuery = query.replace("\n", " ").replace("\"", "\\\"")
            val payload = "{\"query\": \"$flatQuery\", \"variables\": {\"mediaId\": $remoteId, \"progress\": $progress}}"
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val response = NetworkModule.okHttpClient.newCall(request).execute()
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

    override fun isLogged(): Boolean = settings.anilistToken.isNotEmpty()

    override fun logout() {
        settings.anilistToken = ""
    }
}
