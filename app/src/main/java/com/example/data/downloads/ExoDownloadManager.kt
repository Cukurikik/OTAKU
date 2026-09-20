package com.example.data.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.data.models.DownloadEntity
import com.example.media.DownloadHeaderRegistry
import com.example.media.PersistentHttpHeaderResolver
import com.example.media.PersistentResolvingDataSourceFactory
import com.example.storage.StoragePathManager
import com.example.workers.VideoDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class ExoDownloadManager(private val context: Context) {

    fun buildDownloadRequest(
        mediaUri: Uri,
        headers: Map<String, String>
    ): DownloadRequest {
        return Companion.buildDownloadRequest(mediaUri = mediaUri, headers = headers)
    }

    companion object {
        private var downloadManager: DownloadManager? = null
        private var downloadCache: SimpleCache? = null

        /**
         * Serializes custom HTTP headers (e.g. Referer, User-Agent, Cookie) into byte array for DownloadRequest.
         */
        fun serializeHeaders(headers: Map<String, String>): ByteArray {
            if (headers.isEmpty()) return ByteArray(0)
            val str = headers.entries
                .filter { it.value.isNotBlank() }
                .joinToString("\n") { "${it.key}:${it.value}" }
            return str.toByteArray(Charsets.UTF_8)
        }

        /**
         * Deserializes custom HTTP headers from DownloadRequest byte array data.
         */
        fun deserializeHeaders(data: ByteArray?): Map<String, String> {
            if (data == null || data.isEmpty()) return emptyMap()
            val str = String(data, Charsets.UTF_8)
            val map = mutableMapOf<String, String>()
            str.lines().forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim()
                    val v = line.substring(colon + 1).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        map[k] = v
                    }
                }
            }
            return map
        }

        /**
         * Builds a Media3 DownloadRequest with custom headers injected and registered.
         */
        fun buildDownloadRequest(
            mediaUri: Uri,
            headers: Map<String, String> = emptyMap(),
            contentId: String = mediaUri.toString(),
            mimeType: String? = null
        ): DownloadRequest {
            val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = mediaUri,
                customHeaders = headers,
                activeStreamUrl = mediaUri.toString()
            )

            // Register headers in registry for immediate upstream data source access during chunk/key downloads
            DownloadHeaderRegistry.register(contentId, resolvedHeaders, mediaUri.toString())

            val mime = mimeType ?: if (isHlsStream(mediaUri.toString())) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4
            val serialized = serializeHeaders(resolvedHeaders)

            return DownloadRequest.Builder(contentId, mediaUri)
                .setMimeType(mime)
                .setData(serialized)
                .build()
        }

        @Synchronized
        fun getManager(context: Context): DownloadManager {
            if (downloadManager == null) {
                val databaseProvider = StandaloneDatabaseProvider(context)
                val cacheDir = StoragePathManager.getVideoCacheDir(context)
                StoragePathManager.ensureNoMedia(cacheDir)
                
                downloadCache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
                
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(NetworkModule.userAgent)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setAllowCrossProtocolRedirects(true)

                val resolvingDataSourceFactory = PersistentResolvingDataSourceFactory.create(
                    upstreamFactory = dataSourceFactory,
                    headersProvider = {
                        emptyMap()
                    }
                )

                downloadManager = DownloadManager(
                    context,
                    databaseProvider,
                    downloadCache!!,
                    resolvingDataSourceFactory,
                    Executors.newFixedThreadPool(6)
                ).apply {
                    maxParallelDownloads = 3
                    minRetryCount = 5
                    addListener(MediaDownloadListener(context.applicationContext))
                }
            }
            return downloadManager!!
        }

        fun getCache(context: Context): SimpleCache {
            if (downloadCache == null) {
                getManager(context)
            }
            return downloadCache!!
        }

        /**
         * Checks if the given URL is an HLS streaming playlist (.m3u8).
         */
        fun isHlsStream(url: String): Boolean {
            val cleanUrl = url.lowercase().substringBefore("?")
            return cleanUrl.endsWith(".m3u8") ||
                   url.contains(".m3u8", ignoreCase = true) ||
                   url.contains("application/x-mpegurl", ignoreCase = true) ||
                   url.contains("application/vnd.apple.mpegurl", ignoreCase = true) ||
                   url.contains("m3u8=true", ignoreCase = true)
        }

        /**
         * Executes resilient HLS download preparation and queues through ExoPlayer's DownloadService.
         */
        suspend fun executeHlsDownload(
            context: Context,
            downloadUrl: String,
            contentId: String,
            headers: Map<String, String> = emptyMap()
        ): Boolean = withContext(Dispatchers.IO) {
            val mediaUri = Uri.parse(downloadUrl)
            val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = mediaUri,
                customHeaders = headers,
                activeStreamUrl = downloadUrl
            )
            DownloadHeaderRegistry.register(contentId, resolvedHeaders, downloadUrl)

            val mediaItem = MediaItem.Builder()
                .setUri(mediaUri)
                .setMediaId(contentId)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

            val httpDataSourceFactory = PersistentResolvingDataSourceFactory.createDefaultHttpDataSourceFactory(
                headers = resolvedHeaders,
                userAgent = resolvedHeaders["User-Agent"] ?: NetworkModule.userAgent
            )

            val downloadRequest = try {
                val helper = DownloadHelper.forMediaItem(
                    context,
                    mediaItem,
                    null,
                    httpDataSourceFactory
                )
                suspendCancellableCoroutine<DownloadRequest> { cont ->
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(h: DownloadHelper) {
                            try {
                                val req = h.getDownloadRequest(contentId, serializeHeaders(resolvedHeaders))
                                if (cont.isActive) {
                                    cont.resume(req)
                                }
                            } catch (ex: Exception) {
                                if (cont.isActive) {
                                    cont.resumeWithException(ex)
                                }
                            } finally {
                                h.release()
                            }
                        }

                        override fun onPrepareError(h: DownloadHelper, e: IOException) {
                            try {
                                val directReq = buildDownloadRequest(
                                    mediaUri = mediaUri,
                                    headers = resolvedHeaders,
                                    contentId = contentId,
                                    mimeType = MimeTypes.APPLICATION_M3U8
                                )
                                if (cont.isActive) {
                                    cont.resume(directReq)
                                }
                            } catch (ex: Exception) {
                                if (cont.isActive) {
                                    cont.resumeWithException(e)
                                }
                            } finally {
                                h.release()
                            }
                        }
                    })
                    cont.invokeOnCancellation {
                        helper.release()
                    }
                }
            } catch (e: Exception) {
                buildDownloadRequest(
                    mediaUri = mediaUri,
                    headers = resolvedHeaders,
                    contentId = contentId,
                    mimeType = MimeTypes.APPLICATION_M3U8
                )
            }

            try {
                DownloadService.sendAddDownload(
                    context,
                    VideoDownloadService::class.java,
                    downloadRequest,
                    false
                )
            } catch (e: Exception) {
                getManager(context).addDownload(downloadRequest)
            }
            true
        }

        /**
         * Executes a resilient download for background workers with chunked streaming and DB updates.
         * Accurately distinguishes between HLS (.m3u8) playlists and progressive MP4/MKV media.
         * Throws IOException on network failures so WorkManager triggers exponential backoff retry.
         */
        suspend fun executeDownload(
            downloadUrl: String,
            contentId: String,
            context: Context = OtakuHubApp.instance,
            headers: Map<String, String> = emptyMap()
        ): Boolean = withContext(Dispatchers.IO) {
            val parts = contentId.split("||")
            val sourceId = if (parts.size >= 3) parts[0] else "direct_download"
            val mangaId = if (parts.size >= 3) parts[1] else contentId.substringBeforeLast("_", contentId)
            val chapterId = if (parts.size >= 3) parts[2] else contentId.substringAfterLast("_", contentId)

            val app = context.applicationContext as? OtakuHubApp
            val db = app?.database
            val downloadDao = db?.downloadDao()
            val chapterDao = db?.chapterDao()
            val profileId = com.example.data.ProfileManager.currentProfileIdValue

            var downloadEntity = downloadDao?.getDownload(profileId, sourceId, mangaId, chapterId)
            val effectiveHeaders = if (headers.isNotEmpty()) {
                headers
            } else if (!downloadEntity?.headersJson.isNullOrEmpty()) {
                try {
                    Json.decodeFromString<Map<String, String>>(downloadEntity!!.headersJson!!)
                } catch (_: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            val resolvedHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = Uri.parse(downloadUrl),
                customHeaders = effectiveHeaders,
                activeStreamUrl = downloadUrl
            )
            DownloadHeaderRegistry.register(contentId, resolvedHeaders, downloadUrl)

            if (downloadEntity == null) {
                downloadEntity = DownloadEntity(
                    profileId = profileId,
                    sourceId = sourceId,
                    mangaId = mangaId,
                    chapterId = chapterId,
                    state = "DOWNLOADING",
                    directUrl = downloadUrl,
                    headersJson = if (resolvedHeaders.isNotEmpty()) Json.encodeToString(resolvedHeaders) else null,
                    startedAt = System.currentTimeMillis()
                )
                downloadDao?.insertDownload(downloadEntity)
            } else {
                downloadDao?.updateDownload(
                    downloadEntity.copy(
                        state = "DOWNLOADING",
                        startedAt = System.currentTimeMillis(),
                        headersJson = if (resolvedHeaders.isNotEmpty()) Json.encodeToString(resolvedHeaders) else downloadEntity.headersJson
                    )
                )
            }

            // Check if stream is HLS
            if (isHlsStream(downloadUrl)) {
                return@withContext executeHlsDownload(context, downloadUrl, contentId, resolvedHeaders)
            }

            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            StoragePathManager.ensureNoMedia(targetDir)

            val safeName = "${mangaId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}-${chapterId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
            val finalFile = File(targetDir, "$safeName.mp4")
            val tempFile = File(targetDir, "$safeName.tmp")

            // If file already exists and valid, mark complete
            if (finalFile.exists() && finalFile.length() > 1024) {
                downloadDao?.updateDownload(
                    downloadEntity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())
                )
                val chapterEntry = chapterDao?.getChapter(profileId, sourceId, mangaId, chapterId)
                if (chapterEntry != null) {
                    chapterDao.updateChapter(chapterEntry.copy(downloaded = true))
                }
                return@withContext true
            }

            val client = NetworkModule.okHttpClient
            val existingLength = if (tempFile.exists()) tempFile.length() else 0L

            val persistentHeaders = PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = Uri.parse(downloadUrl),
                customHeaders = resolvedHeaders,
                activeStreamUrl = downloadUrl
            )

            val requestBuilder = Request.Builder()
                .url(downloadUrl)

            persistentHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            if (existingLength > 0L) {
                requestBuilder.header("Range", "bytes=$existingLength-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                response.close()
                throw IOException("Download request failed with HTTP code: ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body from download URL")
            val isAppend = response.code == 206 && existingLength > 0L

            // Check if content-type or initial stream is actually an HLS playlist
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.contains("mpegurl") || contentType.contains("application/x-mpegurl")) {
                response.close()
                return@withContext executeHlsDownload(context, downloadUrl, contentId, persistentHeaders)
            }

            FileOutputStream(tempFile, isAppend).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var firstChunk = true
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (firstChunk && !isAppend && bytesRead >= 7) {
                            val headerSample = String(buffer, 0, minOf(bytesRead, 128))
                            if (headerSample.startsWith("#EXTM3U") || headerSample.contains("#EXT-X-VERSION")) {
                                output.close()
                                tempFile.delete()
                                response.close()
                                return@withContext executeHlsDownload(context, downloadUrl, contentId, persistentHeaders)
                            }
                            firstChunk = false
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                val renamed = tempFile.renameTo(finalFile)
                if (!renamed) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                downloadDao?.updateDownload(
                    downloadEntity.copy(
                        state = "DOWNLOADED",
                        completedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )
                val chapterEntry = chapterDao?.getChapter(profileId, sourceId, mangaId, chapterId)
                if (chapterEntry != null) {
                    chapterDao.updateChapter(chapterEntry.copy(downloaded = true))
                }
                return@withContext true
            } else {
                throw IOException("Downloaded file is empty or corrupted")
            }
        }
    }
}


