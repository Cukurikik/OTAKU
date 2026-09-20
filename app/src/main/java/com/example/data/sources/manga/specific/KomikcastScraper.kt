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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

class KomikcastScraper(private val context: Context) : ContentSource, ConfigurableSource {
    override val sourceId: String = "komikcast"
    override val sourceName: String = "Komikcast"
    override val defaultBaseUrl: String = "https://komikcast.app"
    private val baseUrl: String get() = SourceBaseUrlStore.get(context, sourceId, defaultBaseUrl)

    private fun fetchInertiaProps(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "$baseUrl/")
            .build()

        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} from $url")
            }
            val html = response.body?.string() ?: return null
            val pattern = Pattern.compile("data-page=[\"']([\\s\\S]*?)[\"']")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val group1 = matcher.group(1) ?: return null
                val rawJson = group1
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#039;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                val pageObj = JSONObject(rawJson)
                return pageObj.optJSONObject("props")
            }
        }
        return null
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val url = if (page <= 1) baseUrl else "$baseUrl/manga?page=$page"
            val props = fetchInertiaProps(url)
            val items = mutableListOf<ContentItem>()

            if (props != null) {
                if (page <= 1) {
                    // Props home contains trendingManga and latestMangaUpdates
                    val trending = props.optJSONArray("trendingManga")
                    val latest = props.optJSONArray("latestMangaUpdates")
                    parseMangaArray(trending, items)
                    parseMangaArray(latest, items)
                } else {
                    val mangas = props.optJSONObject("mangas")
                    val dataArray = mangas?.optJSONArray("data")
                    parseMangaArray(dataArray, items)
                }
            }
            SourceStatusBus.clear(sourceId)
            emit(items.distinctBy { it.id })
        } catch (e: Exception) {
            Log.e("KomikcastScraper", "getRecommendations failed: ${e.message}")
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/manga?search=$q&page=$page"
            val props = fetchInertiaProps(url)
            val items = mutableListOf<ContentItem>()

            if (props != null) {
                val mangas = props.optJSONObject("mangas")
                val dataArray = mangas?.optJSONArray("data")
                parseMangaArray(dataArray, items)
            }
            SourceStatusBus.clear(sourceId)
            emit(items.distinctBy { it.id })
        } catch (e: Exception) {
            Log.e("KomikcastScraper", "search failed: ${e.message}")
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private fun parseMangaArray(array: JSONArray?, output: MutableList<ContentItem>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val slug = item.optString("slug")
            if (slug.isNullOrBlank()) continue

            val title = item.optString("title").ifBlank { "Manga" }
            val poster = item.optString("poster")
            val synopsis = item.optString("synopsis")

            val genres = mutableListOf<String>()
            val genreArray = item.optJSONArray("genres")
            if (genreArray != null) {
                for (g in 0 until genreArray.length()) {
                    val gObj = genreArray.optJSONObject(g)
                    val gName = gObj?.optString("name")
                    if (!gName.isNullOrBlank()) genres.add(gName)
                }
            }

            val itemUrl = "$baseUrl/manga/$slug"

            output.add(
                ContentItem(
                    id = itemUrl,
                    title = title,
                    source = sourceName,
                    imageUrl = poster,
                    type = ContentType.COMIC,
                    sourceId = sourceId,
                    description = synopsis,
                    genres = genres
                )
            )
        }
    }

    override suspend fun getDetail(itemId: String): ContentItem? = withContext(Dispatchers.IO) {
        try {
            val url = if (itemId.startsWith("http")) itemId else "$baseUrl/manga/$itemId"
            val props = fetchInertiaProps(url) ?: return@withContext null
            val manga = props.optJSONObject("manga") ?: return@withContext null

            val title = manga.optString("title").ifBlank { "Komikcast Manga" }
            val poster = manga.optString("poster")
            val synopsis = manga.optString("synopsis")

            val genres = mutableListOf<String>()
            val genreArray = manga.optJSONArray("genres")
            if (genreArray != null) {
                for (g in 0 until genreArray.length()) {
                    val gObj = genreArray.optJSONObject(g)
                    val gName = gObj?.optString("name")
                    if (!gName.isNullOrBlank()) genres.add(gName)
                }
            }

            ContentItem(
                id = url,
                title = title,
                source = sourceName,
                imageUrl = poster,
                type = ContentType.COMIC,
                sourceId = sourceId,
                description = synopsis,
                genres = genres
            )
        } catch (e: Exception) {
            Log.e("KomikcastScraper", "getDetail failed: ${e.message}")
            null
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val url = if (itemId.startsWith("http")) itemId else "$baseUrl/manga/$itemId"
            val slug = url.substringAfterLast("/").substringBefore("?")
            val props = fetchInertiaProps(url)
            val chapters = mutableListOf<Chapter>()

            if (props != null) {
                val manga = props.optJSONObject("manga")
                val chapterArray = manga?.optJSONArray("chapters")
                if (chapterArray != null) {
                    for (i in 0 until chapterArray.length()) {
                        val ch = chapterArray.optJSONObject(i) ?: continue
                        val chNumber = ch.optDouble("chapter_number", 0.0)
                        val chTitle = ch.optString("title")
                        val createdAt = ch.optString("created_at")

                        val numStr = if (chNumber % 1.0 == 0.0) chNumber.toInt().toString() else chNumber.toString()
                        val displayTitle = if (chTitle.isNotBlank()) chTitle else "Chapter $numStr"
                        val chapterUrl = "$baseUrl/manga/$slug/chapter/$numStr"

                        chapters.add(
                            Chapter(
                                id = chapterUrl,
                                title = displayTitle,
                                url = chapterUrl,
                                date = createdAt
                            )
                        )
                    }
                }
            }
            emit(chapters)
        } catch (e: Exception) {
            Log.e("KomikcastScraper", "getChapters failed: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPages(chapterId: String): Flow<List<Page>> = flow {
        try {
            val props = fetchInertiaProps(chapterId)
            val pages = mutableListOf<Page>()

            if (props != null) {
                val ch = props.optJSONObject("chapter")
                val imagesArray = ch?.optJSONArray("images") ?: props.optJSONArray("images")
                if (imagesArray != null) {
                    for (i in 0 until imagesArray.length()) {
                        val imgObj = imagesArray.optJSONObject(i)
                        val imgPath = imgObj?.optString("image_path")
                        if (!imgPath.isNullOrBlank()) {
                            pages.add(Page(url = imgPath))
                        }
                    }
                }
            }
            emit(pages)
        } catch (e: Exception) {
            Log.e("KomikcastScraper", "getPages failed: ${e.message}")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
