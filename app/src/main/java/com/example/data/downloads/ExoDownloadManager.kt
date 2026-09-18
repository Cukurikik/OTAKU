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
import com.example.storage.StoragePathManager
import com.example.workers.VideoDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
object ExoDownloadManager {
    private var downloadManager: DownloadManager? = null
    private var downloadCache: SimpleCache? = null

    @Synchronized
    fun getManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val databaseProvider = StandaloneDatabaseProvider(context)
            val cacheDir = com.example.storage.StoragePathManager.getVideoCacheDir(context)
            com.example.storage.StoragePathManager.ensureNoMedia(cacheDir)
            
            downloadCache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
            
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(com.example.data.NetworkModule.userAgent)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)

            val resolvingDataSourceFactory = com.example.media.PersistentResolvingDataSourceFactory.create(
                upstreamFactory = dataSourceFactory
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
        contentId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(downloadUrl))
            .setMediaId(contentId)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val downloadRequest = try {
            val helper = DownloadHelper.forMediaItem(context, mediaItem)
            suspendCancellableCoroutine<DownloadRequest> { cont ->
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(h: DownloadHelper) {
                        try {
                            val req = h.getDownloadRequest(null)
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
                            val directReq = DownloadRequest.Builder(contentId, Uri.parse(downloadUrl))
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
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
            DownloadRequest.Builder(contentId, Uri.parse(downloadUrl))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
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
        context: Context = OtakuHubApp.instance
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
        if (downloadEntity == null) {
            downloadEntity = DownloadEntity(
                profileId = profileId,
                sourceId = sourceId,
                mangaId = mangaId,
                chapterId = chapterId,
                state = "DOWNLOADING",
                directUrl = downloadUrl,
                startedAt = System.currentTimeMillis()
            )
            downloadDao?.insertDownload(downloadEntity)
        } else {
            downloadDao?.updateDownload(
                downloadEntity.copy(state = "DOWNLOADING", startedAt = System.currentTimeMillis())
            )
        }

        // Check if stream is HLS
        if (isHlsStream(downloadUrl)) {
            return@withContext executeHlsDownload(context, downloadUrl, contentId)
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

        val persistentHeaders = com.example.media.PersistentHttpHeaderResolver.resolveHeadersForRequest(
            requestUri = android.net.Uri.parse(downloadUrl),
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
            return@withContext executeHlsDownload(context, downloadUrl, contentId)
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
                            return@withContext executeHlsDownload(context, downloadUrl, contentId)
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

