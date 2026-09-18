package com.example.data.sources.torrent

import android.content.Context
import android.util.Log
import com.example.data.NetworkModule
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoSource
import com.example.data.sources.VideoStreamInfo
import com.example.data.torrent.TorrentStreamManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class TorrentSource(private val context: Context) : VideoSource {
    override val sourceId: String = "torrent_source"
    override val sourceName: String = "Magnet / Torrent"
    override val type: ContentType = ContentType.ANIME
    override val supportsSearch: Boolean = true

    private val aniListCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        TorrentStreamManager.init(context.applicationContext)
    }

    private fun cleanTitle(rawTitle: String): String {
        var title = rawTitle
        // Remove brackets like [SubsPlease] or [1080p] or [ABCD1234]
        title = title.replace(Regex("\\[[^\\]]*\\]"), "")
        // Remove parentheses like (1080p) or (Dual Audio)
        title = title.replace(Regex("\\([^\\)]*\\)"), "")
        // Remove file extension
        title = title.replace(Regex("\\.(mkv|mp4|avi)\$"), "")
        // Remove episode number patterns like " - 01" or " - 12" or " 01" at the end
        title = title.replace(Regex(" - \\d+\\s*\$"), "")
        title = title.replace(Regex(" \\d+\\s*\$"), "")
        // Trim and clean spaces
        title = title.replace(Regex("\\s+"), " ").trim()
        return title
    }

    private suspend fun fetchCoverFromAniList(cleanTitle: String): String {
        if (cleanTitle.isBlank()) return ""
        aniListCache[cleanTitle]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().apply {
                    put("query", """
                        query (${'$'}search: String) {
                          Media(search: ${'$'}search, type: ANIME) {
                            coverImage { large }
                          }
                        }
                    """.trimIndent())
                    put("variables", JSONObject().put("search", cleanTitle))
                }

                val request = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .header("Accept", "application/json")
                    .build()

                NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val resStr = response.body?.string() ?: ""
                        val json = JSONObject(resStr)
                        val cover = json.optJSONObject("data")
                            ?.optJSONObject("Media")
                            ?.optJSONObject("coverImage")
                            ?.optString("large") ?: ""
                        if (cover.isNotBlank()) {
                            aniListCache[cleanTitle] = cover
                            cover
                        } else ""
                    } else ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    override fun getRecommendations(page: Int): Flow<List<ContentItem>> = flow {
        val list = mutableListOf<ContentItem>()
        withContext(Dispatchers.IO) {
            try {
                // Fetch Nyaa.si Anime RSS
                val nyaaUrl = "https://nyaa.si/?page=rss&c=1_2&f=0"
                val doc = Jsoup.connect(nyaaUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .parser(Parser.xmlParser())
                    .get()
                val items = doc.select("item")
                for (item in items.take(15)) {
                    val rawTitle = item.select("title").text()
                    var magnet = item.select("link").text()
                    if (!magnet.startsWith("magnet:")) {
                        val infoHash = item.select("nyaa|infoHash").text().ifBlank { item.select("infoHash").text() }
                        if (infoHash.isNotBlank()) {
                            magnet = "magnet:?xt=urn:btih:$infoHash&dn=${java.net.URLEncoder.encode(rawTitle, "UTF-8")}"
                        }
                    }
                    if (magnet.startsWith("magnet:")) {
                        val clean = cleanTitle(rawTitle)
                        val cover = fetchCoverFromAniList(clean)
                        val desc = "Size: ${item.select("nyaa|size").text().ifBlank { "Unknown" }} | Seeders: ${item.select("nyaa|seeders").text().ifBlank { "0" }} | Leechers: ${item.select("nyaa|leechers").text().ifBlank { "0" }}"
                        list.add(
                            ContentItem(
                                id = magnet,
                                title = rawTitle,
                                source = "Nyaa.si",
                                imageUrl = cover,
                                type = ContentType.ANIME,
                                sourceId = sourceId,
                                description = desc
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TorrentSource", "Failed fetching Nyaa RSS", e)
            }

            try {
                // Fetch SubsPlease RSS
                val subsUrl = "https://subsplease.org/rss/?r=1080"
                val doc = Jsoup.connect(subsUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .parser(Parser.xmlParser())
                    .get()
                val items = doc.select("item")
                for (item in items.take(15)) {
                    val rawTitle = item.select("title").text()
                    val magnet = item.select("link").text()
                    if (magnet.startsWith("magnet:")) {
                        val clean = cleanTitle(rawTitle)
                        val cover = fetchCoverFromAniList(clean)
                        list.add(
                            ContentItem(
                                id = magnet,
                                title = rawTitle,
                                source = "SubsPlease",
                                imageUrl = cover,
                                type = ContentType.ANIME,
                                sourceId = sourceId,
                                description = "Source: SubsPlease 1080p Stream"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TorrentSource", "Failed fetching SubsPlease RSS", e)
            }
        }
        emit(list)
    }

    override fun getChapters(itemId: String): Flow<List<Chapter>> = flow {
        // itemId is the magnet link itself
        emit(listOf(
            Chapter(
                id = itemId,
                title = "Play P2P Video Stream",
                url = itemId
            )
        ))
    }

    override suspend fun getDetail(itemId: String): ContentItem? {
        val dnParam = "dn="
        val dnIndex = itemId.indexOf(dnParam)
        val dn = if (dnIndex != -1) {
            val start = dnIndex + dnParam.length
            var end = itemId.indexOf('&', start)
            if (end == -1) end = itemId.length
            val rawDn = itemId.substring(start, end)
            try {
                java.net.URLDecoder.decode(rawDn, "UTF-8")
            } catch (e: Exception) {
                "Torrent Stream"
            }
        } else {
            "Torrent Stream"
        }
        val clean = cleanTitle(dn)
        val cover = fetchCoverFromAniList(clean)
        return ContentItem(
            id = itemId,
            title = dn,
            source = "Magnet Link",
            imageUrl = cover,
            type = ContentType.ANIME,
            sourceId = sourceId,
            description = "Magnet link direct playback. Click 'Play Stream' below to start streaming."
        )
    }

    override fun search(query: String, page: Int): Flow<List<ContentItem>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val list = mutableListOf<ContentItem>()
        withContext(Dispatchers.IO) {
            try {
                // Search Nyaa.si
                val nyaaUrl = "https://nyaa.si/?page=rss&q=${java.net.URLEncoder.encode(query, "UTF-8")}&c=1_2&f=0"
                val doc = Jsoup.connect(nyaaUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .parser(Parser.xmlParser())
                    .get()
                val items = doc.select("item")
                for (item in items) {
                    val rawTitle = item.select("title").text()
                    var magnet = item.select("link").text()
                    if (!magnet.startsWith("magnet:")) {
                        val infoHash = item.select("nyaa|infoHash").text().ifBlank { item.select("infoHash").text() }
                        if (infoHash.isNotBlank()) {
                            magnet = "magnet:?xt=urn:btih:$infoHash&dn=${java.net.URLEncoder.encode(rawTitle, "UTF-8")}"
                        }
                    }
                    if (magnet.startsWith("magnet:")) {
                        val clean = cleanTitle(rawTitle)
                        val cover = fetchCoverFromAniList(clean)
                        val desc = "Size: ${item.select("nyaa|size").text().ifBlank { "Unknown" }} | Seeders: ${item.select("nyaa|seeders").text().ifBlank { "0" }} | Leechers: ${item.select("nyaa|leechers").text().ifBlank { "0" }}"
                        list.add(
                            ContentItem(
                                id = magnet,
                                title = rawTitle,
                                source = "Nyaa.si",
                                imageUrl = cover,
                                type = ContentType.ANIME,
                                sourceId = sourceId,
                                description = desc
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TorrentSource", "Failed searching Nyaa", e)
            }

            try {
                // Search SubsPlease
                val subsUrl = "https://subsplease.org/rss/?r=1080&q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                val doc = Jsoup.connect(subsUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .parser(Parser.xmlParser())
                    .get()
                val items = doc.select("item")
                for (item in items) {
                    val rawTitle = item.select("title").text()
                    val magnet = item.select("link").text()
                    if (magnet.startsWith("magnet:")) {
                        val clean = cleanTitle(rawTitle)
                        val cover = fetchCoverFromAniList(clean)
                        list.add(
                            ContentItem(
                                id = magnet,
                                title = rawTitle,
                                source = "SubsPlease",
                                imageUrl = cover,
                                type = ContentType.ANIME,
                                sourceId = sourceId,
                                description = "Source: SubsPlease 1080p Search"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("TorrentSource", "Failed searching SubsPlease", e)
            }
        }
        emit(list)
    }

    override fun getVideoStream(episodeId: String): Flow<VideoStreamInfo> = callbackFlow {
        TorrentStreamManager.startStream(episodeId)

        val info = VideoStreamInfo(
            episodeId = episodeId,
            title = "Torrent Stream",
            qualities = listOf(
                VideoQuality(
                    qualityLabel = "Source",
                    url = "torrent://stream",
                    isM3U8 = false
                )
            )
        )
        trySend(info)

        awaitClose {
            TorrentStreamManager.stopStream()
        }
    }
}
