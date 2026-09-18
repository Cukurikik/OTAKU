package com.example.data.sources.manga.specific

import android.content.Context
import android.util.Log
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.Page
import com.example.data.sources.ConfigurableSource
import com.example.data.sources.ContentSource
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.SourceStatusBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class ShinigamiScraper(private val context: Context) : ContentSource, ConfigurableSource {
    override val sourceId: String = "shinigami"
    override val sourceName: String = "Shinigami"
    override val defaultBaseUrl: String = "https://g.shinigami.asia"
    private val apiBase = "https://api.shngm.io/v1"
    private val webBase: String get() = SourceBaseUrlStore.get(context, sourceId, defaultBaseUrl)

    private fun executeJsonRequest(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "$webBase/")
            .header("Origin", webBase)
            .build()

        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} from $url")
            }
            val body = response.body?.string() ?: throw Exception("Empty response from $url")
            return JSONObject(body)
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$apiBase/manga/list?search=$q&page=$page&page_size=20"
            val root = executeJsonRequest(url)
            val items = parseMangaList(root)
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            Log.e("ShinigamiScraper", "search failed: ${e.message}")
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = "$apiBase/manga/list?page=$page&page_size=20&sort=latest&sort_order=desc"
            val root = executeJsonRequest(url)
            val items = parseMangaList(root)
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            Log.e("ShinigamiScraper", "getRecommendations failed: ${e.message}")
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private fun parseMangaList(root: JSONObject): List<ContentItem> {
        val list = mutableListOf<ContentItem>()
        val dataArray = root.optJSONArray("data") ?: return emptyList()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val mangaId = item.optString("manga_id")
            if (mangaId.isNullOrBlank()) continue

            val title = item.optString("title").ifBlank { "Untitled" }
            val cover = item.optString("cover_image_url")
            val desc = item.optString("description")

            val genres = mutableListOf<String>()
            val taxonomy = item.optJSONObject("taxonomy")
            val genreArray = taxonomy?.optJSONArray("Genre")
            if (genreArray != null) {
                for (g in 0 until genreArray.length()) {
                    val gObj = genreArray.optJSONObject(g)
                    val gName = gObj?.optString("name")
                    if (!gName.isNullOrBlank()) genres.add(gName)
                }
            }

            // Gunakan format id yang bisa dibedakan: manga_id atau full URL
            val itemId = "$webBase/series/$mangaId"

            list.add(
                ContentItem(
                    id = itemId,
                    title = title,
                    source = sourceName,
                    imageUrl = cover,
                    type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = desc,
                    genres = genres
                )
            )
        }
        return list
    }

    private fun extractMangaId(itemId: String): String {
        return itemId.removePrefix("$webBase/series/")
            .removePrefix("https://g.shinigami.asia/series/")
            .substringBefore("/")
            .substringBefore("?")
            .trim()
    }

    override suspend fun getDetail(itemId: String): ContentItem? = withContext(Dispatchers.IO) {
        try {
            val mangaId = extractMangaId(itemId)
            val url = "$apiBase/manga/detail/$mangaId"
            val root = executeJsonRequest(url)
            val data = root.optJSONObject("data") ?: return@withContext null

            val title = data.optString("title").ifBlank { "Shinigami Manga" }
            val cover = data.optString("cover_image_url")
            val desc = data.optString("description")

            val genres = mutableListOf<String>()
            val taxonomy = data.optJSONObject("taxonomy")
            val genreArray = taxonomy?.optJSONArray("Genre")
            if (genreArray != null) {
                for (g in 0 until genreArray.length()) {
                    val gObj = genreArray.optJSONObject(g)
                    val gName = gObj?.optString("name")
                    if (!gName.isNullOrBlank()) genres.add(gName)
                }
            }

            ContentItem(
                id = itemId,
                title = title,
                source = sourceName,
                imageUrl = cover,
                type = ContentType.COMIC,
                sourceId = sourceId,
                description = desc,
                genres = genres
            )
        } catch (e: Exception) {
            Log.e("ShinigamiScraper", "getDetail failed: ${e.message}")
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val mangaId = extractMangaId(itemId)
            val url = "$apiBase/chapter/$mangaId/list?page=1&page_size=250"
            val root = executeJsonRequest(url)
            val dataArray = root.optJSONArray("data")
            val chapters = mutableListOf<Chapter>()

            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val ch = dataArray.optJSONObject(i) ?: continue
                    val chapterId = ch.optString("chapter_id")
                    if (chapterId.isNullOrBlank()) continue

                    val chapterNumber = ch.optDouble("chapter_number", 0.0)
                    val chapterTitle = ch.optString("chapter_title")
                    val releaseDate = ch.optString("release_date").ifBlank { ch.optString("created_at") }

                    val numStr = if (chapterNumber % 1.0 == 0.0) chapterNumber.toInt().toString() else chapterNumber.toString()
                    val displayTitle = if (chapterTitle.isNotBlank()) "Chapter $numStr: $chapterTitle" else "Chapter $numStr"

                    chapters.add(
                        Chapter(
                            id = "$apiBase/chapter/detail/$chapterId",
                            title = displayTitle,
                            url = "$apiBase/chapter/detail/$chapterId",
                            date = releaseDate
                        )
                    )
                }
            }
            emit(chapters)
        } catch (e: Exception) {
            Log.e("ShinigamiScraper", "getChapters failed: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        try {
            // URL chapterId bisa berupa URL lengkap $apiBase/chapter/detail/$chapterId atau hanya UUID
            val cleanUrl = if (chapterId.startsWith("http")) {
                chapterId
            } else {
                "$apiBase/chapter/detail/$chapterId"
            }

            val root = executeJsonRequest(cleanUrl)
            val data = root.optJSONObject("data") ?: throw Exception("Missing data in chapter detail")
            val baseUrl = data.optString("base_url").trimEnd('/')
            val chapterObj = data.optJSONObject("chapter") ?: throw Exception("Missing chapter object in chapter detail")
            val path = chapterObj.optString("path")
            val filesArray = chapterObj.optJSONArray("data") ?: throw Exception("Missing image files array in chapter detail")

            val pages = mutableListOf<Page>()
            for (i in 0 until filesArray.length()) {
                val filename = filesArray.optString(i)
                if (filename.isNotBlank()) {
                    val fullUrl = "$baseUrl$path$filename"
                    pages.add(Page(url = fullUrl))
                }
            }

            emit(pages)
        } catch (e: Exception) {
            Log.e("ShinigamiScraper", "getPages failed: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
