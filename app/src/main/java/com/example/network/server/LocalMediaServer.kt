package com.example.network.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.data.ProfileManager
import com.example.data.models.ContentType
import com.example.data.models.HistoryItem
import com.example.data.sources.VideoExtractorEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Representasi media yang dapat disajikan oleh LocalMediaServer ke Smart TV atau Web Companion.
 */
sealed class ServedMedia {
    data class LocalFile(
        val file: File,
        val mimeType: String
    ) : ServedMedia()

    data class ComicPage(
        val cbzFile: File,
        val pageIndex: Int,
        val mimeType: String = "image/jpeg"
    ) : ServedMedia()

    data class RemoteRelay(
        val remoteUrl: String,
        val mimeType: String,
        val headers: Map<String, String> = emptyMap()
    ) : ServedMedia()
}

/**
 * LocalMediaServer:
 * Server HTTP lokal berbasis NanoHTTPD (default port 8080).
 *
 * Mendukung:
 * 1. Web Companion Dashboard (Single Page Application untuk Remote PC Streaming & Reading).
 * 2. REST API untuk Sinkronisasi Library, Riwayat Tonton, Pencarian Katalog, dan Update Progres.
 * 3. Proxy Video Stream & Image Anti-Hotlink / Cloudflare Bypass untuk PC Browser.
 * 4. Protokol DLNA / UPnP ke Smart TV (Samsung Tizen, LG WebOS, Roku, Chromecast).
 */
class LocalMediaServer(
    private val defaultPort: Int = DEFAULT_PORT,
    private var appContext: Context? = null
) : NanoHTTPD(defaultPort) {

    companion object {
        const val DEFAULT_PORT = 8080
        private const val TAG = "LocalMediaServer"

        @Volatile
        private var instance: LocalMediaServer? = null

        fun getInstance(context: Context? = null, port: Int = DEFAULT_PORT): LocalMediaServer {
            return instance ?: synchronized(this) {
                instance ?: LocalMediaServer(port, context?.applicationContext).also { instance = it }
            }
        }

        /**
         * Mendapatkan alamat IPv4 lokal perangkat Android dalam jaringan Wi-Fi lokal.
         */
        fun getLocalIpAddress(context: Context? = null): String {
            try {
                // 1. Coba melalui WifiManager jika context tersedia
                if (context != null) {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                    if (ipInt != 0) {
                        return String.format(
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            ipInt shr 8 and 0xff,
                            ipInt shr 16 and 0xff,
                            ipInt shr 24 and 0xff
                        )
                    }
                }

                // 2. Fallback iterasi interface jaringan aktif
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val addresses = Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
            return "127.0.0.1"
        }
    }

    fun setContext(context: Context) {
        this.appContext = context.applicationContext
    }

    private val mediaRegistry = ConcurrentHashMap<String, ServedMedia>()

    /**
     * Mendaftarkan file media lokal untuk disajikan via URL lokal.
     */
    fun registerLocalFile(file: File, mimeType: String = "video/mp4"): String {
        val id = UUID.randomUUID().toString()
        mediaRegistry[id] = ServedMedia.LocalFile(file, mimeType)
        return id
    }

    /**
     * Mendaftarkan halaman komik CBZ untuk diekstraksi dan disajikan via URL lokal.
     */
    fun registerComicPage(cbzFile: File, pageIndex: Int): String {
        val id = UUID.randomUUID().toString()
        mediaRegistry[id] = ServedMedia.ComicPage(cbzFile, pageIndex)
        return id
    }

    /**
     * Mendaftarkan stream jarak jauh (remote stream) dengan custom headers untuk di-relay ke TV atau PC.
     */
    fun registerRemoteRelay(remoteUrl: String, mimeType: String, headers: Map<String, String>): String {
        val id = UUID.randomUUID().toString()
        mediaRegistry[id] = ServedMedia.RemoteRelay(remoteUrl, mimeType, headers)
        return id
    }

    /**
     * Menghapus media dari registri server.
     */
    fun unregisterMedia(id: String) {
        mediaRegistry.remove(id)
    }

    /**
     * Menghasilkan URL lengkap yang dapat diakses oleh TV atau browser PC di jaringan lokal.
     */
    fun getMediaUrl(context: Context?, id: String): String {
        val ip = getLocalIpAddress(context ?: appContext)
        val ext = when (val item = mediaRegistry[id]) {
            is ServedMedia.LocalFile -> if (item.mimeType.contains("mpegurl", ignoreCase = true)) ".m3u8" else ".mp4"
            is ServedMedia.ComicPage -> ".jpg"
            is ServedMedia.RemoteRelay -> if (item.mimeType.contains("mpegurl", ignoreCase = true) || item.remoteUrl.contains(".m3u8")) ".m3u8" else ".mp4"
            null -> ".bin"
        }
        val port = if (listeningPort > 0) listeningPort else defaultPort
        return "http://$ip:$port/media/$id$ext"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // 1. Tangani CORS Preflight Request
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            addCorsHeaders(response)
            return response
        }

        // 2. Routing Web Companion Dashboard SPA (HTML / JS / CSS)
        if (uri == "/" || uri == "/index.html" || uri == "/dashboard") {
            return serveWebDashboard(session)
        }

        // 3. REST API Routing untuk Web Companion & Remote Control
        if (uri.startsWith("/api/")) {
            val apiResponse = handleApiRequest(session)
            addCorsHeaders(apiResponse)
            return apiResponse
        }

        // 4. Proxy Stream Video (M3U8 / MP4 Relay dengan Cloudflare / Anti-Hotlink Bypass)
        if (uri.startsWith("/proxy/stream") || uri.startsWith("/proxy/video")) {
            val targetUrl = session.parameters["url"]?.firstOrNull()
            val sourceId = session.parameters["sourceId"]?.firstOrNull()
            if (!targetUrl.isNullOrBlank()) {
                val decodedUrl = URLDecoder.decode(targetUrl, "UTF-8")
                return serveProxiedStream(session, decodedUrl, sourceId)
            }
        }

        // 5. Proxy Image Gambar Komik / Poster dengan Injeksi Header Referer
        if (uri.startsWith("/proxy/image")) {
            val targetUrl = session.parameters["url"]?.firstOrNull()
            val sourceId = session.parameters["sourceId"]?.firstOrNull()
            if (!targetUrl.isNullOrBlank()) {
                val decodedUrl = URLDecoder.decode(targetUrl, "UTF-8")
                return serveProxiedImage(decodedUrl, sourceId)
            }
        }

        // 6. UPnP / DLNA Media Serving
        if (uri.startsWith("/media/")) {
            val cleanId = uri.removePrefix("/media/")
                .substringBefore(".")
                .substringBefore("/")

            val media = mediaRegistry[cleanId] ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "404 Media Not Found"
            )

            return when (media) {
                is ServedMedia.LocalFile -> serveLocalFile(session, media)
                is ServedMedia.ComicPage -> serveComicPage(session, media)
                is ServedMedia.RemoteRelay -> serveRemoteRelay(session, media)
            }
        }

        // 7. Sub-request segmen untuk HLS proxy relay
        if (uri.startsWith("/proxy_segment")) {
            val targetUrl = session.parameters["url"]?.firstOrNull()
            if (!targetUrl.isNullOrBlank()) {
                val decodedUrl = URLDecoder.decode(targetUrl, "UTF-8")
                return serveRelayedSegment(session, decodedUrl)
            }
        }

        if (uri == "/ping") {
            val resp = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "LocalMediaServer is running")
            addCorsHeaders(resp)
            return resp
        }

        val resp = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        addCorsHeaders(resp)
        return resp
    }

    /**
     * Menyajikan berkas Web Companion Dashboard SPA dari Android Assets.
     */
    private fun serveWebDashboard(session: IHTTPSession): Response {
        val context = appContext ?: OtakuHubApp.instance
        return try {
            val inputStream = context.assets.open("web_dashboard/index.html")
            val htmlContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", htmlContent)
            addCorsHeaders(response)
            response
        } catch (e: Exception) {
            Log.w(TAG, "Asset web_dashboard/index.html not loaded from assets: ${e.message}")
            val fallbackHtml = buildFallbackDashboardHtml()
            val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", fallbackHtml)
            addCorsHeaders(response)
            response
        }
    }

    /**
     * Menangani panggilan REST API dari Web Companion Dashboard di PC.
     */
    private fun isFileInAllowedDirectory(file: File, context: Context?): Boolean {
        if (context == null) return false
        val allowedDirs = listOfNotNull(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)?.absolutePath,
            context.getExternalFilesDir(null)?.absolutePath
        )
        return allowedDirs.any { file.absolutePath.startsWith(it) }
    }

    private fun scanSharedLanFiles(context: Context?): JSONArray {
        val results = JSONArray()
        if (context == null) return results
        
        try {
            // 1. Scan Movies/OtakuHub for exported MP4s and M3U8s
            val moviesDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub")
            if (moviesDir.exists() && moviesDir.isDirectory) {
                moviesDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.name.endsWith(".mp4") || file.name.endsWith(".m3u8"))) {
                        val obj = JSONObject().apply {
                            put("name", file.name)
                            put("path", file.absolutePath)
                            put("size", file.length())
                            put("type", if (file.name.endsWith(".mp4")) "video/mp4" else "application/vnd.apple.mpegurl")
                        }
                        results.put(obj)
                    }
                }
            }
            
            // 2. Scan externalFilesDir/comics for CBZ files
            val comicsDir = java.io.File(context.getExternalFilesDir(null), "comics")
            if (comicsDir.exists() && comicsDir.isDirectory) {
                comicsDir.listFiles()?.forEach { folder ->
                    if (folder.isDirectory) {
                        folder.listFiles()?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".cbz")) {
                                val obj = JSONObject().apply {
                                    put("name", "${folder.name} - ${file.name}")
                                    put("path", file.absolutePath)
                                    put("size", file.length())
                                    put("type", "application/zip")
                                }
                                results.put(obj)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error scanning LAN files: ${e.message}")
        }
        
        return results
    }

    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri
        val ctx = appContext ?: try { OtakuHubApp.instance } catch (_: Exception) { null }

        return try {
            when {
                // Status Server & IP
                uri == "/api/status" -> {
                    val json = JSONObject().apply {
                        put("status", "online")
                        put("name", "OtakuHub Web Companion")
                        put("version", "1.0")
                        put("ip", getLocalIpAddress(ctx))
                        put("port", if (listeningPort > 0) listeningPort else defaultPort)
                        put("profileId", ProfileManager.currentProfileIdValue)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", json.toString())
                }

                // Fitur P2P LAN Share
                uri == "/api/lan/shared_files" -> {
                    val sharedFiles = scanSharedLanFiles(ctx)
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", sharedFiles.toString())
                }
                
                uri.startsWith("/api/lan/download") -> {
                    val path = session.parameters["path"]?.firstOrNull()
                    if (!path.isNullOrBlank()) {
                        val file = File(URLDecoder.decode(path, "UTF-8"))
                        if (file.exists() && isFileInAllowedDirectory(file, ctx)) {
                            val mime = if (file.name.endsWith(".cbz")) "application/zip" else "video/mp4"
                            return serveLocalFile(session, ServedMedia.LocalFile(file, mime))
                        }
                    }
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File Not Found")
                }

                // Data Library Pengguna dari Room Database
                uri == "/api/library" -> {
                    val profileId = ProfileManager.currentProfileIdValue
                    val items = try {
                        val db = OtakuHubApp.instance.database
                        runBlocking(Dispatchers.IO) {
                            db.libraryDao().getAllEntriesSync(profileId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching library for web companion: ${e.message}")
                        emptyList()
                    }

                    val jsonArray = JSONArray()
                    items.forEach { entry ->
                        val obj = JSONObject().apply {
                            put("id", entry.mangaId)
                            put("mangaId", entry.mangaId)
                            put("title", entry.title)
                            put("imageUrl", entry.thumbnailUrl ?: "")
                            put("thumbnailUrl", entry.thumbnailUrl ?: "")
                            put("sourceId", entry.sourceId)
                            put("unreadCount", entry.unreadCount)
                            put("status", entry.status)
                            put("lastUpdated", entry.lastUpdated)
                        }
                        jsonArray.put(obj)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", jsonArray.toString())
                }

                // Data Riwayat Tonton & Baca
                uri == "/api/history" -> {
                    val profileId = ProfileManager.currentProfileIdValue
                    val historyItems = try {
                        val db = OtakuHubApp.instance.database
                        runBlocking(Dispatchers.IO) {
                            db.historyDao().getAllHistorySync(profileId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching history for web companion: ${e.message}")
                        emptyList()
                    }

                    val jsonArray = JSONArray()
                    historyItems.forEach { item ->
                        val obj = JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            put("imageUrl", item.imageUrl)
                            put("type", item.type.name)
                            put("source", item.source)
                            put("sourceId", item.sourceId)
                            put("lastChapterOrEpisode", item.lastChapterOrEpisode)
                            put("progress", item.progress)
                            put("timestamp", item.timestamp)
                        }
                        jsonArray.put(obj)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", jsonArray.toString())
                }

                // Pencarian Katalog Global / Multi-Source
                uri.startsWith("/api/search") -> {
                    val query = session.parameters["q"]?.firstOrNull() ?: ""
                    val sourceId = session.parameters["sourceId"]?.firstOrNull()
                    val sourceIds = if (!sourceId.isNullOrBlank()) listOf(sourceId) else null

                    val searchResults = if (query.isNotBlank()) {
                        try {
                            val repo = OtakuHubApp.instance.repository
                            runBlocking(Dispatchers.IO) {
                                val list = mutableListOf<com.example.data.models.ContentItem>()
                                repo.search(query, sourceIds, 1).collect { items ->
                                    list.addAll(items)
                                }
                                list.distinctBy { it.id }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error performing search in web companion: ${e.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    val jsonArray = JSONArray()
                    searchResults.forEach { item ->
                        val obj = JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            put("imageUrl", item.imageUrl)
                            put("type", item.type.name)
                            put("source", item.source)
                            put("sourceId", item.sourceId)
                            put("description", item.description ?: "")
                        }
                        jsonArray.put(obj)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", jsonArray.toString())
                }

                // Detail Item & Daftar Bab / Episode
                uri.startsWith("/api/detail") -> {
                    val itemId = session.parameters["id"]?.firstOrNull() ?: ""
                    val sourceId = session.parameters["sourceId"]?.firstOrNull()

                    val (detail, chapters) = try {
                        val repo = OtakuHubApp.instance.repository
                        runBlocking(Dispatchers.IO) {
                            val det = repo.getDetail(itemId, sourceId)
                            val chaps = repo.getChapters(itemId, sourceId).firstOrNull() ?: emptyList()
                            Pair(det, chaps)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching detail in web companion: ${e.message}")
                        Pair(null, emptyList())
                    }

                    val json = JSONObject().apply {
                        put("id", detail?.id ?: itemId)
                        put("title", detail?.title ?: "Unknown Title")
                        put("imageUrl", detail?.imageUrl ?: "")
                        put("type", detail?.type?.name ?: "ANIME")
                        put("source", detail?.source ?: "")
                        put("sourceId", detail?.sourceId ?: (sourceId ?: ""))
                        put("description", detail?.description ?: "")
                        put("status", detail?.status ?: "Ongoing")

                        val genresArray = JSONArray()
                        detail?.genres?.forEach { genresArray.put(it) }
                        put("genres", genresArray)

                        val chaptersArray = JSONArray()
                        chapters.forEach { ch ->
                            val chObj = JSONObject().apply {
                                put("id", ch.id)
                                put("title", ch.title)
                                put("url", ch.url)
                                put("date", ch.date)
                            }
                            chaptersArray.put(chObj)
                        }
                        put("chapters", chaptersArray)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", json.toString())
                }

                // Ekstraksi Video Stream & Resolusi URL Asli
                uri.startsWith("/api/stream") -> {
                    val sourceId = session.parameters["sourceId"]?.firstOrNull()
                    val episodeId = session.parameters["episodeId"]?.firstOrNull() ?: ""
                    val targetUrl = session.parameters["url"]?.firstOrNull() ?: episodeId

                    val streamInfo = try {
                        val repo = OtakuHubApp.instance.repository
                        runBlocking(Dispatchers.IO) {
                            // Coba lewat getVideoStream repository
                            val stream = repo.getVideoStream(episodeId, sourceId).firstOrNull()
                            if (stream != null && (stream.url.isNotBlank() || stream.qualities.isNotEmpty())) {
                                stream
                            } else {
                                // Fallback lewat VideoExtractorEngine
                                val extractor = VideoExtractorEngine(ctx)
                                extractor.extractVideo(targetUrl, episodeId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting stream for web companion: ${e.message}")
                        null
                    }

                    val json = JSONObject().apply {
                        put("videoUrl", streamInfo?.url ?: "")
                        put("embedUrl", streamInfo?.embedUrl ?: "")
                        val headersObj = JSONObject()
                        streamInfo?.headers?.forEach { (k, v) -> headersObj.put(k, v) }
                        put("headers", headersObj)

                        val qualitiesArray = JSONArray()
                        streamInfo?.qualities?.forEach { q ->
                            val qObj = JSONObject().apply {
                                put("label", q.qualityLabel)
                                put("url", q.url)
                            }
                            qualitiesArray.put(qObj)
                        }
                        put("qualities", qualitiesArray)

                        val subsArray = JSONArray()
                        streamInfo?.subtitles?.forEach { s ->
                            val sObj = JSONObject().apply {
                                put("label", s.language)
                                put("url", s.url)
                                put("language", s.language)
                            }
                            subsArray.put(sObj)
                        }
                        put("subtitles", subsArray)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", json.toString())
                }

                // Ekstraksi Gambar Halaman Komik (Manga Reader)
                uri.startsWith("/api/manga/pages") -> {
                    val sourceId = session.parameters["sourceId"]?.firstOrNull()
                    val chapterId = session.parameters["chapterId"]?.firstOrNull() ?: ""

                    val pages = try {
                        val repo = OtakuHubApp.instance.repository
                        runBlocking(Dispatchers.IO) {
                            repo.getPages(chapterId, sourceId).firstOrNull() ?: emptyList()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting pages for web companion: ${e.message}")
                        emptyList()
                    }

                    val jsonArray = JSONArray()
                    pages.forEachIndexed { idx, p ->
                        val pObj = JSONObject().apply {
                            put("index", idx)
                            put("imageUrl", p.url)
                            put("url", p.url)
                        }
                        jsonArray.put(pObj)
                    }
                    newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", jsonArray.toString())
                }

                // POST Sinkronisasi Progres Tonton / Baca dari PC ke Database HP
                uri == "/api/progress" && session.method == Method.POST -> {
                    val map = HashMap<String, String>()
                    session.parseBody(map)
                    val postData = map["postData"] ?: ""

                    if (postData.isNotBlank()) {
                        val bodyJson = JSONObject(postData)
                        val sourceId = bodyJson.optString("sourceId")
                        val mangaId = bodyJson.optString("mangaId")
                        val itemId = bodyJson.optString("itemId")
                        val position = bodyJson.optLong("position", 0L)
                        val duration = bodyJson.optLong("duration", 0L)
                        val timestamp = bodyJson.optLong("timestamp", System.currentTimeMillis())
                        val profileId = ProfileManager.currentProfileIdValue

                        try {
                            val db = OtakuHubApp.instance.database
                            runBlocking(Dispatchers.IO) {
                                // Simpan ke History Table
                                if (itemId.isNotBlank()) {
                                    val progressFloat = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 1f
                                    val historyItem = HistoryItem(
                                        profileId = profileId,
                                        id = itemId,
                                        title = mangaId.ifBlank { itemId },
                                        source = sourceId,
                                        imageUrl = "",
                                        type = if (duration > 0) com.example.data.models.ContentType.ANIME else com.example.data.models.ContentType.COMIC,
                                        progress = progressFloat,
                                        lastChapterOrEpisode = itemId,
                                        timestamp = timestamp,
                                        sourceId = sourceId
                                    )
                                    db.historyDao().insertHistory(historyItem)
                                }

                                // Update Chapter Reading Progress jika komik
                                if (duration == 0L && mangaId.isNotBlank() && itemId.isNotBlank()) {
                                    db.chapterDao().updateProgress(
                                        profileId = profileId,
                                        sourceId = sourceId,
                                        mangaId = mangaId,
                                        chapterId = itemId,
                                        lastPageRead = position.toInt(),
                                        lastReadAt = timestamp,
                                        isRead = true
                                    )
                                    db.libraryDao().updateUnreadCount(profileId, sourceId, mangaId)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving progress from web companion: ${e.message}")
                        }
                    }

                    val resp = newFixedLengthResponse(Response.Status.OK, "application/json; charset=UTF-8", "{\"status\":\"ok\"}")
                    return resp
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"Endpoint not found\"}")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"${e.message}\"}")
        }
    }

    /**
     * Relay Video Stream dengan Cloudflare Bypass dan penulisan ulang playlist HLS M3U8.
     */
    private fun serveProxiedStream(session: IHTTPSession, targetUrl: String, sourceId: String?): Response {
        return try {
            val requestBuilder = Request.Builder().url(targetUrl)

            // Injeksi header Anti-Hotlinking dan Referer otomatis
            val host = try { java.net.URI(targetUrl).host } catch (_: Exception) { null }
            if (host != null) {
                requestBuilder.header("Referer", "https://$host/")
                requestBuilder.header("Origin", "https://$host")
            }
            requestBuilder.header("User-Agent", NetworkModule.userAgent)

            // Teruskan Range header dari browser PC (untuk seeking video)
            val clientRange = session.headers["range"]
            if (!clientRange.isNullOrBlank()) {
                requestBuilder.header("Range", clientRange)
            }

            val okHttpResp = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = okHttpResp.body ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty response from remote stream")

            val status = if (okHttpResp.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val rawContentType = okHttpResp.header("Content-Type") ?: "video/mp4"

            // Jika file M3U8 Playlist, tulis ulang URL segmen agar melalui proxy lokal
            if (targetUrl.contains(".m3u8") || rawContentType.contains("mpegurl", true) || rawContentType.contains("application/vnd.apple.mpegurl", true)) {
                val originalText = body.string()
                val rewritten = rewriteM3u8PlaylistForWeb(originalText, targetUrl, sourceId)
                val bytes = rewritten.toByteArray(Charsets.UTF_8)
                val resp = newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", ByteArrayInputStream(bytes), bytes.size.toLong())
                addCorsHeaders(resp)
                return resp
            }

            val inputStream = body.byteStream()
            val contentLength = body.contentLength()
            val resp = if (contentLength >= 0) {
                newFixedLengthResponse(status, rawContentType, inputStream, contentLength)
            } else {
                newChunkedResponse(status, rawContentType, inputStream)
            }

            okHttpResp.header("Content-Range")?.let { resp.addHeader("Content-Range", it) }
            resp.addHeader("Accept-Ranges", "bytes")
            addCorsHeaders(resp)
            resp
        } catch (e: Exception) {
            val resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Stream Proxy Error: ${e.message}")
            addCorsHeaders(resp)
            resp
        }
    }

    /**
     * Proxy Gambar Halaman Komik & Poster dengan Header Referer untuk mencegah 403 Forbidden di browser PC.
     */
    private fun serveProxiedImage(targetUrl: String, sourceId: String?): Response {
        return try {
            val requestBuilder = Request.Builder().url(targetUrl)
            val host = try { java.net.URI(targetUrl).host } catch (_: Exception) { null }
            if (host != null) {
                requestBuilder.header("Referer", "https://$host/")
            }
            requestBuilder.header("User-Agent", NetworkModule.userAgent)

            val okHttpResp = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = okHttpResp.body ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty image response")

            val contentType = okHttpResp.header("Content-Type") ?: "image/jpeg"
            val bytes = body.bytes()

            val resp = newFixedLengthResponse(Response.Status.OK, contentType, ByteArrayInputStream(bytes), bytes.size.toLong())
            resp.addHeader("Cache-Control", "public, max-age=86400")
            addCorsHeaders(resp)
            resp
        } catch (e: Exception) {
            val resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Image Proxy Error: ${e.message}")
            addCorsHeaders(resp)
            resp
        }
    }

    /**
     * Menulis ulang segmen pada M3U8 Playlist untuk Web Companion.
     */
    private fun rewriteM3u8PlaylistForWeb(m3u8Content: String, basePlaylistUrl: String, sourceId: String?): String {
        val lines = m3u8Content.lines()
        val baseUri = try {
            java.net.URI(basePlaylistUrl)
        } catch (_: Exception) {
            null
        }

        return lines.joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line
            } else {
                val absoluteUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    trimmed
                } else if (baseUri != null) {
                    baseUri.resolve(trimmed).toString()
                } else {
                    trimmed
                }
                val encodedUrl = URLEncoder.encode(absoluteUrl, "UTF-8")
                val encodedSource = if (sourceId != null) "&sourceId=${URLEncoder.encode(sourceId, "UTF-8")}" else ""
                "/proxy/stream?url=$encodedUrl$encodedSource"
            }
        }
    }

    /**
     * Menambahkan Header CORS ke seluruh respons server.
     */
    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Range, Authorization, X-Requested-With")
        response.addHeader("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")
    }

    /**
     * Menyajikan file lokal dengan dukungan HTTP 206 Partial Content untuk Range request video seeking.
     */
    private fun serveLocalFile(session: IHTTPSession, media: ServedMedia.LocalFile): Response {
        val file = media.file
        if (!file.exists() || !file.canRead()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File missing or unreadable")
        }

        val totalLength = file.length()
        val rangeHeader = session.headers["range"]

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.substringAfter("bytes=").trim()
            val parts = rangeVal.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotBlank()) {
                parts[1].toLongOrNull() ?: (totalLength - 1)
            } else {
                totalLength - 1
            }.coerceAtMost(totalLength - 1)

            if (start > end || start >= totalLength) {
                val errorResp = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                errorResp.addHeader("Content-Range", "bytes */$totalLength")
                addCorsHeaders(errorResp)
                return errorResp
            }

            val rangeLength = end - start + 1
            val fis = FileInputStream(file)
            fis.skip(start)

            val resp = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, media.mimeType, fis, rangeLength)
            resp.addHeader("Content-Range", "bytes $start-$end/$totalLength")
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Content-Length", rangeLength.toString())
            addCorsHeaders(resp)
            return resp
        }

        // Standard Full File Response
        val fis = FileInputStream(file)
        val resp = newFixedLengthResponse(Response.Status.OK, media.mimeType, fis, totalLength)
        resp.addHeader("Accept-Ranges", "bytes")
        resp.addHeader("Content-Length", totalLength.toString())
        addCorsHeaders(resp)
        return resp
    }

    /**
     * Ekstraksi gambar komik dari file .cbz / .zip dan menyajikannya sebagai image/jpeg.
     */
    private fun serveComicPage(session: IHTTPSession, media: ServedMedia.ComicPage): Response {
        val cbzFile = media.cbzFile
        if (!cbzFile.exists() || !cbzFile.canRead()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "CBZ file not found")
        }

        return try {
            val zip = ZipFile(cbzFile)
            val imageEntries = zip.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory && (
                        entry.name.endsWith(".jpg", true) ||
                        entry.name.endsWith(".jpeg", true) ||
                        entry.name.endsWith(".png", true) ||
                        entry.name.endsWith(".webp", true)
                    )
                }
                .sortedBy { it.name }
                .toList()

            if (media.pageIndex !in imageEntries.indices) {
                zip.close()
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Page index out of bounds")
            }

            val targetEntry = imageEntries[media.pageIndex]
            val inputStream = zip.getInputStream(targetEntry)
            val buffer = ByteArrayOutputStream()
            inputStream.copyTo(buffer)
            zip.close()

            val bytes = buffer.toByteArray()
            val resp = newFixedLengthResponse(
                Response.Status.OK,
                media.mimeType,
                ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
            resp.addHeader("Content-Length", bytes.size.toString())
            addCorsHeaders(resp)
            resp
        } catch (e: Exception) {
            val resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error extracting comic page: ${e.message}")
            addCorsHeaders(resp)
            resp
        }
    }

    /**
     * Me-relay remote stream (.m3u8 atau .mp4) dengan header otentikasi/anti-bot.
     */
    private fun serveRemoteRelay(session: IHTTPSession, media: ServedMedia.RemoteRelay): Response {
        return try {
            val requestBuilder = Request.Builder().url(media.remoteUrl)
            media.headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            // Forward range header dari TV/Browser jika ada
            val clientRange = session.headers["range"]
            if (!clientRange.isNullOrBlank()) {
                requestBuilder.header("Range", clientRange)
            }

            val okHttpResp = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = okHttpResp.body ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty response from remote source")

            val status = if (okHttpResp.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val contentType = okHttpResp.header("Content-Type") ?: media.mimeType

            // Jika file merupakan playlist M3U8, tulis ulang URL segmen agar tetap melalui proxy lokal
            if (media.remoteUrl.contains(".m3u8") || contentType.contains("mpegurl", true)) {
                val originalText = body.string()
                val rewritten = rewriteM3u8Playlist(originalText, media.remoteUrl)
                val bytes = rewritten.toByteArray(Charsets.UTF_8)
                val resp = newFixedLengthResponse(Response.Status.OK, "application/x-mpegURL", ByteArrayInputStream(bytes), bytes.size.toLong())
                addCorsHeaders(resp)
                return resp
            }

            val inputStream = body.byteStream()
            val contentLength = body.contentLength()
            val resp = if (contentLength >= 0) {
                newFixedLengthResponse(status, contentType, inputStream, contentLength)
            } else {
                newChunkedResponse(status, contentType, inputStream)
            }

            okHttpResp.header("Content-Range")?.let { resp.addHeader("Content-Range", it) }
            resp.addHeader("Accept-Ranges", "bytes")
            addCorsHeaders(resp)
            resp
        } catch (e: Exception) {
            val resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Relay Error: ${e.message}")
            addCorsHeaders(resp)
            resp
        }
    }

    /**
     * Menyajikan segmen TS yang direlay dari M3U8 playlist.
     */
    private fun serveRelayedSegment(session: IHTTPSession, targetUrl: String): Response {
        return try {
            val requestBuilder = Request.Builder().url(targetUrl)
            session.headers["range"]?.let { requestBuilder.header("Range", it) }
            val okHttpResp = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val body = okHttpResp.body ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Empty segment")

            val status = if (okHttpResp.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            val contentType = okHttpResp.header("Content-Type") ?: "video/MP2T"
            val contentLength = body.contentLength()

            val resp = if (contentLength >= 0) {
                newFixedLengthResponse(status, contentType, body.byteStream(), contentLength)
            } else {
                newChunkedResponse(status, contentType, body.byteStream())
            }
            addCorsHeaders(resp)
            resp
        } catch (e: Exception) {
            val resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Segment relay error: ${e.message}")
            addCorsHeaders(resp)
            resp
        }
    }

    /**
     * Memodifikasi URL segmen TS pada M3U8 agar diteruskan melalui server lokal.
     */
    private fun rewriteM3u8Playlist(m3u8Content: String, basePlaylistUrl: String): String {
        val lines = m3u8Content.lines()
        val baseUri = try {
            java.net.URI(basePlaylistUrl)
        } catch (_: Exception) {
            null
        }

        return lines.joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line
            } else {
                val absoluteSegmentUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    trimmed
                } else if (baseUri != null) {
                    baseUri.resolve(trimmed).toString()
                } else {
                    trimmed
                }
                val encoded = URLEncoder.encode(absoluteSegmentUrl, "UTF-8")
                "/proxy_segment?url=$encoded"
            }
        }
    }

    /**
     * HTML Fallback sederhana jika aset tidak terbaca.
     */
    private fun buildFallbackDashboardHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>OtakuHub Web Companion</title>
                <style>body{background:#090d16;color:#fff;font-family:sans-serif;padding:40px;text-align:center;}</style>
            </head>
            <body>
                <h1>OtakuHub Web Companion Running</h1>
                <p>Silakan gunakan antarmuka lengkap melalui URL utama.</p>
            </body>
            </html>
        """.trimIndent()
    }
}

