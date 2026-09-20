package com.example.data.sources.viki

import android.content.Context
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.SourceStatusBus
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.sources.VideoStreamInfo
import com.example.data.sources.base.BaseDramaSource
import com.example.data.sources.base.DramaEpisode
import com.example.data.sources.base.DramaSeason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

open class VikiSource(context: Context) : BaseDramaSource(context) {
    override val sourceId: String = "viki"
    override val sourceName: String = "Viki"
    override val baseUrl: String = "https://www.viki.com"
    override val contentType: ContentType = ContentType.DRAMA

    companion object {
        const val API_BASE = "https://api.viki.io/v4"
        const val APP_ID = "100005a"
    }

    protected open fun executeApiGet(endpoint: String): String {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$API_BASE$endpoint"
        val sep = if (fullUrl.contains("?")) "&" else "?"
        val urlWithApp = if (fullUrl.contains("app=")) fullUrl else "$fullUrl${sep}app=$APP_ID"

        val request = Request.Builder()
            .url(urlWithApp)
            .header("User-Agent", NetworkModule.userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://www.viki.com/")
            .header("Origin", "https://www.viki.com")
            .build()

        val response = NetworkModule.okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("Viki API error with HTTP $code for $urlWithApp")
        }
        return response.body?.string() ?: throw java.io.IOException("Empty response from Viki API")
    }

    fun parseContainersJson(json: JSONObject): List<ContentItem> {
        val list = mutableListOf<ContentItem>()
        val responseArray = json.optJSONArray("response") ?: return list
        for (i in 0 until responseArray.length()) {
            val item = responseArray.optJSONObject(i) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val titles = item.optJSONObject("titles")
            val title = titles?.optString("id")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("en")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("default")?.takeIf { it.isNotBlank() }
                ?: item.optString("title").takeIf { it.isNotBlank() }
                ?: "Drama $id"

            val images = item.optJSONObject("images")
            val posterUrl = images?.optJSONObject("poster")?.optString("url")
                ?: images?.optJSONObject("thumbnail")?.optString("url")
                ?: images?.optJSONObject("atv_cover")?.optString("url")
                ?: ""

            val descriptions = item.optJSONObject("descriptions")
            val description = descriptions?.optString("id")?.takeIf { it.isNotBlank() }
                ?: descriptions?.optString("en")?.takeIf { it.isNotBlank() }
                ?: descriptions?.optString("default")
                ?: ""

            val genresList = mutableListOf<String>()
            val genresArr = item.optJSONArray("genres")
            if (genresArr != null) {
                for (g in 0 until genresArr.length()) {
                    val genreStr = genresArr.optString(g)
                    if (genreStr.isNotBlank()) genresList.add(genreStr.replaceFirstChar { it.uppercase() })
                }
            }
            val originCountry = item.optJSONObject("origin")?.optString("country")?.uppercase()
            if (!originCountry.isNullOrBlank()) {
                genresList.add(originCountry)
            }

            val isBlocked = item.optBoolean("blocked", false)
            val status = if (isBlocked) "Restricted" else "Active"
            val year = item.optString("created_at").take(4)

            list.add(
                ContentItem(
                    id = id,
                    title = title,
                    source = sourceName,
                    imageUrl = posterUrl,
                    type = ContentType.DRAMA,
                    description = description,
                    genres = genresList,
                    sourceId = sourceId,
                    status = status,
                    year = year
                )
            )
        }
        return list
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val jsonStr = executeApiGet("/containers.json?page=$page&per_page=20&sort=views&direction=desc&type=series")
            val items = parseContainersJson(JSONObject(jsonStr))
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    fun getPopular(page: Int): Flow<List<ContentItem>> = getRecommendations(page)

    fun getLatestUpdates(page: Int): Flow<List<ContentItem>> = flow {
        try {
            val jsonStr = executeApiGet("/containers.json?page=$page&per_page=20&sort=views_recent&direction=desc&type=series")
            val items = parseContainersJson(JSONObject(jsonStr))
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val jsonStr = try {
                executeApiGet("/search.json?term=$encoded&page=$page&per_page=20&type=series,film")
            } catch (e: Exception) {
                executeApiGet("/containers.json?keyword=$encoded&page=$page&per_page=20")
            }
            val items = parseContainersJson(JSONObject(jsonStr))
            SourceStatusBus.clear(sourceId)
            emit(items)
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    fun extractContainerId(raw: String): String {
        if (raw.matches(Regex("^[0-9]+[a-z]$"))) return raw
        val urlRegex = Regex("""(?:tv|containers)/([0-9]+[a-z])""")
        val match = urlRegex.find(raw)
        if (match != null) return match.groupValues[1]
        val idRegex = Regex("""([0-9]+[a-z])""")
        return idRegex.find(raw)?.groupValues?.get(1) ?: raw
    }

    fun extractVideoId(raw: String): String {
        if (raw.matches(Regex("^[0-9]+v$"))) return raw
        val urlRegex = Regex("""videos/([0-9]+v)""")
        val match = urlRegex.find(raw)
        if (match != null) return match.groupValues[1]
        val idRegex = Regex("""([0-9]+v)""")
        return idRegex.find(raw)?.groupValues?.get(1) ?: raw
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        return try {
            val containerId = extractContainerId(itemId)
            val jsonStr = executeApiGet("/containers/$containerId.json")
            val obj = JSONObject(jsonStr)

            val titles = obj.optJSONObject("titles")
            val title = titles?.optString("id")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("en")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("default")?.takeIf { it.isNotBlank() }
                ?: obj.optString("title")

            val images = obj.optJSONObject("images")
            val posterUrl = images?.optJSONObject("poster")?.optString("url")
                ?: images?.optJSONObject("thumbnail")?.optString("url")
                ?: images?.optJSONObject("atv_cover")?.optString("url")
                ?: ""

            val descriptions = obj.optJSONObject("descriptions")
            val description = descriptions?.optString("id")?.takeIf { it.isNotBlank() }
                ?: descriptions?.optString("en")?.takeIf { it.isNotBlank() }
                ?: descriptions?.optString("default")
                ?: ""

            val genresList = mutableListOf<String>()
            val genresArr = obj.optJSONArray("genres")
            if (genresArr != null) {
                for (g in 0 until genresArr.length()) {
                    genresList.add(genresArr.optString(g).replaceFirstChar { it.uppercase() })
                }
            }

            val originCountry = obj.optJSONObject("origin")?.optString("country")?.uppercase()
            if (!originCountry.isNullOrBlank()) {
                genresList.add(originCountry)
            }

            val castList = mutableListOf<String>()
            val castArr = obj.optJSONArray("cast") ?: obj.optJSONArray("actors") ?: obj.optJSONArray("stars")
            if (castArr != null) {
                for (c in 0 until castArr.length()) {
                    val actorObj = castArr.optJSONObject(c)
                    val actorName = actorObj?.optString("name")?.ifBlank { actorObj.optString("person_name") }
                        ?: castArr.optString(c)
                    if (actorName.isNotBlank()) castList.add(actorName.trim())
                }
            }

            val subtitleStatus = if (obj.optJSONObject("subtitles")?.has("id") == true || 
                (obj.optJSONObject("subtitle_completions")?.optInt("id", 0) ?: 0) > 40) {
                "Sub Indo"
            } else {
                "Eng Sub"
            }

            val seasonNumber = Regex("""(?i)(?:Season|S)\s*0*(\d+)""").find(title)?.groupValues?.get(1)?.toIntOrNull()

            SourceStatusBus.clear(sourceId)
            ContentItem(
                id = containerId,
                title = title,
                source = sourceName,
                imageUrl = posterUrl,
                type = ContentType.DRAMA,
                description = description,
                genres = genresList,
                sourceId = sourceId,
                status = if (obj.optBoolean("blocked", false)) "Restricted" else "Active",
                year = obj.optString("created_at").take(4),
                castList = castList,
                subtitleStatus = subtitleStatus,
                seasonNumber = seasonNumber
            )
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            null
        }
    }

    fun parseVideosJson(json: JSONObject): List<DramaSeason> {
        val responseArray = json.optJSONArray("response") ?: return emptyList()
        val episodes = mutableListOf<DramaEpisode>()
        for (i in 0 until responseArray.length()) {
            val video = responseArray.optJSONObject(i) ?: continue
            val videoId = video.optString("id").takeIf { it.isNotBlank() } ?: continue
            val number = video.optInt("number", i + 1)
            val titles = video.optJSONObject("titles")
            val epTitle = titles?.optString("id")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("en")?.takeIf { it.isNotBlank() }
                ?: titles?.optString("default")?.takeIf { it.isNotBlank() }
                ?: "Episode $number"

            val webUrl = video.optJSONObject("url")?.optString("web")
                ?: "https://www.viki.com/videos/$videoId"

            episodes.add(
                DramaEpisode(
                    episodeNumber = number,
                    url = webUrl,
                    title = epTitle,
                    date = video.optString("created_at").take(10)
                )
            )
        }
        return if (episodes.isNotEmpty()) {
            listOf(DramaSeason(seasonNumber = 1, title = "Season 1", episodes = episodes))
        } else {
            emptyList()
        }
    }

    override suspend fun fetchSeasonsAndEpisodes(dramaUrl: String): List<DramaSeason> {
        return try {
            val containerId = extractContainerId(dramaUrl)
            val jsonStr = executeApiGet("/videos.json?container_id=$containerId&page=1&per_page=100&sort=number&direction=asc&type=episode")
            parseVideosJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        try {
            val containerId = extractContainerId(itemId)
            val jsonStr = executeApiGet("/videos.json?container_id=$containerId&page=1&per_page=100&sort=number&direction=asc&type=episode")
            val seasons = parseVideosJson(JSONObject(jsonStr))
            val chapters = mutableListOf<Chapter>()
            for (season in seasons) {
                for (ep in season.episodes) {
                    chapters.add(Chapter(id = ep.url, title = ep.title, url = ep.url, date = ep.date))
                }
            }
            if (chapters.isNotEmpty()) {
                SourceStatusBus.clear(sourceId)
                emit(chapters)
            } else {
                throw java.io.IOException("Tidak ada episode ditemukan untuk drama ini pada Viki API.")
            }
        } catch (e: Exception) {
            SourceStatusBus.reportFromException(sourceId, sourceName, e)
            throw e
        }
    }

    override suspend fun fetchVideoStream(episodeUrl: String): Result<VideoStreamInfo> {
        return runCatching {
            val videoId = extractVideoId(episodeUrl)
            val webEmbedUrl = "https://www.viki.com/videos/$videoId"

            val subtitles = listOf(
                Subtitle(language = "Indonesian", url = "$API_BASE/videos/$videoId/subtitles/id.vtt?app=$APP_ID", format = "vtt"),
                Subtitle(language = "English", url = "$API_BASE/videos/$videoId/subtitles/en.vtt?app=$APP_ID", format = "vtt")
            )

            // 1. Try Viki API streams.json
            val streamsJsonStr = runCatching {
                executeApiGet("/videos/$videoId/streams.json")
            }.getOrNull()

            if (!streamsJsonStr.isNullOrBlank()) {
                val streamsObj = JSONObject(streamsJsonStr)
                val qualities = mutableListOf<VideoQuality>()

                val keys = streamsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val streamItem = streamsObj.optJSONObject(key) ?: continue
                    val streamUrl = streamItem.optString("url")
                    if (streamUrl.isNotBlank()) {
                        val label = when {
                            key.contains("1080", ignoreCase = true) -> "1080p"
                            key.contains("720", ignoreCase = true) -> "720p"
                            key.contains("480", ignoreCase = true) -> "480p"
                            key.contains("360", ignoreCase = true) -> "360p"
                            else -> key.uppercase()
                        }
                        qualities.add(
                            VideoQuality(
                                qualityLabel = label,
                                url = streamUrl,
                                isM3U8 = streamUrl.contains(".m3u8"),
                                headers = mapOf("Referer" to "https://www.viki.com/", "User-Agent" to NetworkModule.userAgent)
                            )
                        )
                    }
                }

                if (qualities.isNotEmpty()) {
                    return@runCatching VideoStreamInfo(
                        episodeId = episodeUrl,
                        embedUrl = webEmbedUrl,
                        qualities = qualities,
                        subtitles = subtitles,
                        headers = mapOf("Referer" to "https://www.viki.com/", "User-Agent" to NetworkModule.userAgent),
                        servers = listOf(
                            VideoServer(
                                name = "Viki Official API Stream",
                                embedUrl = webEmbedUrl,
                                qualities = qualities,
                                subtitles = subtitles,
                                headers = mapOf("Referer" to "https://www.viki.com/")
                            )
                        )
                    )
                }
            }

            // 2. Headless network interception fallback for web playback
            val directInterception = videoUrlInterceptor.interceptVideo(webEmbedUrl, webEmbedUrl, timeoutMs = 15000L, headers = mapOf("Referer" to "https://www.viki.com/"))
            if (directInterception != null && directInterception.qualities.isNotEmpty()) {
                return@runCatching directInterception.copy(
                    subtitles = if (directInterception.subtitles.isEmpty()) subtitles else directInterception.subtitles
                )
            }

            // 3. Fallback VideoStreamInfo with web embed and subtitles
            VideoStreamInfo(
                episodeId = episodeUrl,
                embedUrl = webEmbedUrl,
                qualities = listOf(
                    VideoQuality("Auto", webEmbedUrl, isM3U8 = false, headers = mapOf("Referer" to "https://www.viki.com/"))
                ),
                subtitles = subtitles,
                headers = mapOf("Referer" to "https://www.viki.com/", "User-Agent" to NetworkModule.userAgent),
                servers = listOf(
                    VideoServer("Viki Web Player", webEmbedUrl, emptyList(), subtitles, mapOf("Referer" to "https://www.viki.com/"))
                )
            )
        }
    }
}

