package com.example.data.sources.anilist

import android.content.Context
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AniListSource(private val context: Context) : ContentSource {
    override val sourceId: String = "anilist"
    override val sourceName: String = "AniList"
    override val supportsVideo: Boolean = false
    override val language: String = "en"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val apiUrl = "https://graphql.anilist.co"

    private suspend fun graphql(query: String, variables: Map<String, Any> = emptyMap()): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("query", query)
                    put("variables", JSONObject(variables))
                }
                val request = Request.Builder()
                    .url(apiUrl)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    JSONObject(response.body!!.string())
                }
            } catch (e: Exception) { null }
        }
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val query = """
                query (${'$'}page: Int) {
                  Page(page: ${'$'}page, perPage: 20) {
                    media(type: ANIME, sort: TRENDING_DESC, isAdult: false) {
                      id title { romaji english } coverImage { large }
                      description genres averageScore
                    }
                  }
                }
            """
            val result = graphql(query, mapOf("page" to page))
            val mediaArray = result?.getJSONObject("data")?.getJSONObject("Page")
                ?.getJSONArray("media") ?: return@flow

            val items = (0 until mediaArray.length()).map { i ->
                val m = mediaArray.getJSONObject(i)
                val id = m.getInt("id").toString()
                val titleObj = m.getJSONObject("title")
                val title = titleObj.optString("romaji", titleObj.optString("english", ""))
                val img = m.getJSONObject("coverImage").optString("large", "")
                val desc = m.optString("description", "").replace(Regex("<[^>]+>"), "")
                ContentItem(
                    id = "https://anilist.co/anime/$id",
                    title = title, source = sourceName, imageUrl = img,
                    type = ContentType.ANIME, sourceId = sourceId,
                    description = desc
                )
            }
            emit(items)
        } catch (e: CancellationException) { throw e
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emit(emptyList()) }
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        val id = itemId.substringAfterLast("/").toIntOrNull() ?: return null
        return try {
            val query = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id, type: ANIME) {
                    id title { romaji english native } coverImage { extraLarge }
                    description genres averageScore episodes status
                    studios { nodes { name } }
                  }
                }
            """
            val result = graphql(query, mapOf("id" to id))
            val m = result?.getJSONObject("data")?.getJSONObject("Media") ?: return null
            val titleObj = m.getJSONObject("title")
            val title = titleObj.optString("romaji", titleObj.optString("english", ""))
            ContentItem(
                id = itemId, title = title, source = sourceName,
                imageUrl = m.getJSONObject("coverImage").optString("extraLarge", ""),
                type = ContentType.ANIME, sourceId = sourceId,
                description = m.optString("description", "").replace(Regex("<[^>]+>"), "")
            )
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { null }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val gqlQuery = """
                query (${'$'}search: String, ${'$'}page: Int) {
                  Page(page: ${'$'}page, perPage: 20) {
                    media(search: ${'$'}search, type: ANIME) {
                      id title { romaji } coverImage { large }
                    }
                  }
                }
            """
            val result = graphql(gqlQuery, mapOf("search" to query, "page" to page))
            val arr = result?.getJSONObject("data")?.getJSONObject("Page")?.getJSONArray("media") ?: return@flow
            val items = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                ContentItem(
                    id = "https://anilist.co/anime/${m.getInt("id")}",
                    title = m.getJSONObject("title").optString("romaji", ""),
                    source = sourceName,
                    imageUrl = m.getJSONObject("coverImage").optString("large", ""),
                    type = ContentType.ANIME, sourceId = sourceId
                )
            }
            emit(items)
        } catch (e: CancellationException) { throw e
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emit(emptyList()) }
    }

    override fun getChapters(itemId: String) = flow { emit(emptyList<Chapter>()) }
    override fun getPages(chapterId: String) = flow { emit(emptyList<Page>()) }
}
