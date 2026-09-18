package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.NetworkModule
import com.example.data.SettingsManager
import com.example.data.db.AppDatabase
import com.example.data.models.ContentType
import com.example.data.trackers.KitsuApi
import com.example.data.trackers.KitsuDataNode
import com.example.data.trackers.KitsuLibraryEntryAttributes
import com.example.data.trackers.KitsuLibraryEntryRelationships
import com.example.data.trackers.KitsuLibraryEntryRequest
import com.example.data.trackers.KitsuLibraryEntryRequestData
import com.example.data.trackers.KitsuLibraryEntryUpdateRequest
import com.example.data.trackers.KitsuLibraryEntryUpdateRequestData
import com.example.data.trackers.KitsuRelationshipData
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TrackerSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? com.example.OtakuHubApp
        val db = app?.database ?: return Result.failure()
        val settingsManager = SettingsManager(applicationContext)
        val trackerManager = app.trackerManager

        val queue = com.example.data.sync.PendingSyncQueue(
            pendingSyncDao = db.pendingSyncsDao(),
            trackerManager = trackerManager,
            context = applicationContext
        )

        try {
            queue.processQueue()
            val remaining = db.pendingSyncsDao().getAllPendingActions()
            return if (remaining.isEmpty()) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    private suspend fun syncAnilist(remoteId: Long, progress: Int, settingsManager: SettingsManager): Result {
        val token = settingsManager.anilistToken
        if (token.isEmpty()) return Result.success()
        try {
            val query = """
                mutation SaveMediaListEntry(${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                        id
                        progress
                    }
                }
            """.trimIndent()
            
            val flatQuery = query.replace("\n", " ").replace("\"", "\\\"")
            val payload = "{\"query\": \"${flatQuery}\", \"variables\": {\"mediaId\": $remoteId, \"progress\": $progress}}"
            
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
                
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            response.close()
            return if (response.isSuccessful) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private suspend fun syncMal(remoteId: Long, isAnime: Boolean, progress: Int, settingsManager: SettingsManager): Result {
        var token = settingsManager.malToken
        val refreshToken = settingsManager.malRefreshToken
        val expiresAt = settingsManager.malExpiresAt
        val clientId = "6114d00ca681b7701d1e15fe11a4987e"

        if (token.isEmpty()) return Result.success()

        // Check if token expired or about to expire in next 60 seconds
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis() + 60_000L && refreshToken.isNotEmpty()) {
            val refreshed = com.example.ui.screens.refreshMalOAuthToken(clientId, refreshToken)
            if (refreshed != null) {
                token = refreshed.accessToken
                settingsManager.malToken = refreshed.accessToken
                settingsManager.malRefreshToken = refreshed.refreshToken
                settingsManager.malExpiresAt = System.currentTimeMillis() + (refreshed.expiresIn * 1000L)
            }
        }

        try {
            val typeStr = if (isAnime) "anime" else "manga"
            val bodyStr = if (isAnime) "num_watched_episodes=$progress" else "num_chapters_read=$progress"
            
            var request = Request.Builder()
                .url("https://api.myanimelist.net/v2/$typeStr/$remoteId/my_list_status")
                .header("Authorization", "Bearer $token")
                .put(bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
                
            var response = NetworkModule.okHttpClient.newCall(request).execute()

            // If 401 Unauthorized, try refreshing token once
            if (response.code == 401 && refreshToken.isNotEmpty()) {
                response.close()
                val refreshed = com.example.ui.screens.refreshMalOAuthToken(clientId, refreshToken)
                if (refreshed != null) {
                    token = refreshed.accessToken
                    settingsManager.malToken = refreshed.accessToken
                    settingsManager.malRefreshToken = refreshed.refreshToken
                    settingsManager.malExpiresAt = System.currentTimeMillis() + (refreshed.expiresIn * 1000L)

                    request = Request.Builder()
                        .url("https://api.myanimelist.net/v2/$typeStr/$remoteId/my_list_status")
                        .header("Authorization", "Bearer $token")
                        .put(bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                        .build()
                    response = NetworkModule.okHttpClient.newCall(request).execute()
                }
            }

            val isSuccess = response.isSuccessful
            response.close()
            return if (isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private suspend fun syncKitsu(remoteId: Long, isAnime: Boolean, progress: Int, settingsManager: SettingsManager): Result {
        val token = settingsManager.kitsuToken
        val userId = settingsManager.kitsuUserId
        if (token.isEmpty() || userId.isEmpty()) {
            return Result.success()
        }
        try {
            val type = if (isAnime) "anime" else "manga"
            val retrofit = Retrofit.Builder()
                .baseUrl("https://kitsu.io/api/edge/")
                .client(NetworkModule.okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
                .build()

            val api = retrofit.create(KitsuApi::class.java)
            val authHeader = "Bearer $token"
            
            val mediaId = remoteId.toString()
            val entryResponse = if (type == "anime") {
                api.getLibraryEntry(authHeader, userId, mediaId, null)
            } else {
                api.getLibraryEntry(authHeader, userId, null, mediaId)
            }

            if (!entryResponse.isSuccessful) return Result.retry()

            val existingEntryId = entryResponse.body()?.data?.firstOrNull()?.id
            if (existingEntryId != null) {
                val updateReq = KitsuLibraryEntryUpdateRequest(
                    data = KitsuLibraryEntryUpdateRequestData(
                        id = existingEntryId,
                        attributes = KitsuLibraryEntryAttributes(progress = progress)
                    )
                )
                val updateRes = api.updateLibraryEntry(authHeader, existingEntryId, updateReq)
                if (!updateRes.isSuccessful) return Result.retry()
            } else {
                val createReq = KitsuLibraryEntryRequest(
                    data = KitsuLibraryEntryRequestData(
                        attributes = KitsuLibraryEntryAttributes(progress = progress),
                        relationships = KitsuLibraryEntryRelationships(
                            user = KitsuRelationshipData(KitsuDataNode(id = userId, type = "users")),
                            media = KitsuRelationshipData(KitsuDataNode(id = mediaId, type = type))
                        )
                    )
                )
                val createRes = api.createLibraryEntry(authHeader, createReq)
                if (!createRes.isSuccessful) return Result.retry()
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<TrackerSyncWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "TrackerSyncWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
