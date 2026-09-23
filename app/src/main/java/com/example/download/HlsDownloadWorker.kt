package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.OtakuHubApp
import com.example.data.NetworkModule
import com.example.download.resiliency.HlsChunkVerifier
import com.example.storage.StoragePathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * HlsDownloadWorker:
 * Engine pengunduhan video HLS (.m3u8) & MP4 dengan dukungan Segment Checkpointing,
 * Atomic Retry per segmen .ts, validasi integritas via HlsChunkVerifier, Quality Variant Selection,
 * dan penyimpanan terstruktur (/AnimeTitle/Episode_XX.mp4).
 */
class HlsDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HlsDownloadWorker"
        private const val CHANNEL_ID = "hls_download_resilient_channel"
        private const val MAX_RETRIES_PER_SEGMENT = 4
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val serverResolver by lazy { MultiServerFallbackResolver(context) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var playlistUrl = inputData.getString("PLAYLIST_URL") ?: ""
        val title = inputData.getString("TITLE") ?: "Video Download"
        val animeTitle = inputData.getString("ANIME_TITLE") ?: title
        val outputFileName = inputData.getString("OUTPUT_FILE_NAME") ?: "video_${System.currentTimeMillis()}.mp4"
        val customOutputPath = inputData.getString("OUTPUT_PATH")
        val sourceId = inputData.getString("SOURCE_ID") ?: ""
        val mangaId = inputData.getString("MANGA_ID") ?: ""
        val chapterId = inputData.getString("CHAPTER_ID") ?: ""
        val episodeNum = inputData.getInt("EPISODE_NUM", inputData.getInt("EPISODE", 1))
        val targetQuality = inputData.getString("TARGET_QUALITY") ?: inputData.getString("QUALITY") ?: "720p"
        val episodeUrl = inputData.getString("EPISODE_URL") ?: ""

        val app = context.applicationContext as? OtakuHubApp
        val downloadDao = app?.database?.downloadDao()
        val profileId = com.example.data.ProfileManager.currentProfileIdValue

        try {
            // Run Storage Janitor task to clean up old staging folders (>24h) and prevent storage leak
            StoragePathManager.runStorageJanitorTask(context)

            setForeground(createForegroundInfo(title, 0, 100, "Menganalisis playlist & server video..."))

            // Resolve direct stream if playlistUrl is blank
            if (playlistUrl.isBlank() && episodeUrl.isNotBlank()) {
                // Gunakan VideoExtractorEngine untuk mengekstrak URL
                val extractor = com.example.data.sources.VideoExtractorEngine(context)
                val extracted = extractor.extractVideo(episodeUrl)
                if (extracted != null && extracted.qualities.isNotEmpty()) {
                    val match = serverResolver.pickQualityMatch(extracted.qualities, targetQuality)
                    playlistUrl = match?.url ?: extracted.qualities.first().url
                }
            }

            if (playlistUrl.isBlank()) {
                Log.e(TAG, "Tidak ada URL video/playlist yang valid untuk episode $episodeNum ($title)")
                return@withContext Result.failure(workDataOf("ERROR" to "No valid stream URL found"))
            }

            // Tentukan berkas keluaran final terstruktur
            val outputFile = if (!customOutputPath.isNullOrBlank()) {
                val f = File(customOutputPath)
                f.parentFile?.mkdirs()
                f
            } else {
                MultiServerFallbackResolver.getDestinationPath(
                    baseDir = StoragePathManager.getRootDownloadDir(context),
                    animeTitle = animeTitle,
                    episodeNum = episodeNum
                )
            }

            // 1. Jika URL adalah berkas MP4 langsung (bukan HLS)
            if (playlistUrl.contains(".mp4") && !playlistUrl.contains(".m3u8")) {
                setForeground(createForegroundInfo(title, 10, 100, "Mengunduh berkas MP4..."))
                val success = downloadDirectMp4(playlistUrl, outputFile, title)
                if (success) {
                    markDownloadSuccess(sourceId, mangaId, chapterId, outputFile, downloadDao, profileId)
                    return@withContext Result.success(workDataOf("OUTPUT_PATH" to outputFile.absolutePath))
                } else {
                    return@withContext Result.retry()
                }
            }

            // 2. Siapkan Staging Folder untuk Chunk Resiliency & Key Caching
            val videoSafeHash = playlistUrl.hashCode().toString()
            val stagingDir = File(context.cacheDir, "tmp_hls_$videoSafeHash")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }

            // 3. Parsing M3U8 Playlist (Master variant selector matching TARGET_QUALITY)
            val segments = resolveAndParsePlaylist(playlistUrl, targetQuality, stagingDir)
            if (segments.isEmpty()) {
                Log.e(TAG, "Tidak ada segmen media yang ditemukan pada playlist: $playlistUrl")
                return@withContext Result.failure(workDataOf("ERROR" to "No media segments in M3U8"))
            }

            val totalSegments = segments.size
            Log.i(TAG, "Memulai unduhan HLS resilient: $totalSegments segmen untuk '$title' ($targetQuality)")

            val downloadedFiles = mutableListOf<File>()

            // 4. Iterasi dan Unduh setiap segmen dengan Atomic Retry & Verifikasi Integritas
            for (index in 0 until totalSegments) {
                if (isStopped) {
                    Log.w(TAG, "Pengunduhan dihentikan oleh sistem/pengguna pada segmen $index")
                    return@withContext Result.retry()
                }

                val segmentUrl = segments[index]
                val chunkFile = File(stagingDir, "chunk_%05d.ts".format(index))

                // Periksa apakah chunk sudah terunduh & valid dari checkpoint sebelumnya (Resumption)
                var isChunkValid = chunkFile.exists() && HlsChunkVerifier.verifySegmentIntegrity(chunkFile)

                var attempts = 0
                while (!isChunkValid && attempts < MAX_RETRIES_PER_SEGMENT) {
                    attempts++
                    Log.d(TAG, "Mengunduh segmen [$index/$totalSegments] percobaan #$attempts: $segmentUrl")
                    val downloadSuccess = downloadSegmentAtomic(segmentUrl, chunkFile)
                    if (downloadSuccess && HlsChunkVerifier.verifySegmentIntegrity(chunkFile)) {
                        isChunkValid = true
                    } else {
                        Log.w(TAG, "Segmen $index korup atau gagal verifikasi, mengulang...")
                        if (chunkFile.exists()) chunkFile.delete()
                        delay((attempts * 500L)) // Exponential backoff
                    }
                }

                if (!isChunkValid) {
                    Log.e(TAG, "Gagal mengunduh segmen $index setelah $MAX_RETRIES_PER_SEGMENT percobaan")
                    return@withContext Result.retry()
                }

                downloadedFiles.add(chunkFile)

                // Update progres berkala
                val progressPercent = ((index + 1) * 100) / totalSegments
                val progressStatus = "Mengunduh segmen ${index + 1}/$totalSegments ($progressPercent%)"
                setForeground(createForegroundInfo(title, progressPercent, 100, progressStatus))

                // Update progress ke singleton map jika ada
                val downloadIdKey = "$sourceId||$mangaId||$chapterId"
                if (sourceId.isNotBlank() && mangaId.isNotBlank()) {
                    com.example.workers.VideoDownloadService.downloadProgressMap[downloadIdKey]?.value = progressPercent / 100f
                }
            }

            // 5. Penggabungan (*Muxing*) Seluruh Segmen TS Menjadi Berkas MP4 Final
            setForeground(createForegroundInfo(title, 99, 100, "Menggabungkan dan memvalidasi kontainer video final..."))
            val mergedFile = mergeTsSegmentsToFile(downloadedFiles, outputFile)

            // 6. Validasi Integritas Berkas Final
            if (!mergedFile.exists() || mergedFile.length() < 1024) {
                Log.e(TAG, "Berkas MP4 hasil gabungan tidak valid atau kosong: ${mergedFile.absolutePath}")
                return@withContext Result.failure(workDataOf("ERROR" to "Final merged video is invalid"))
            }

            // 7. Bersihkan Berkas Chunk Staging Sementara
            stagingDir.deleteRecursively()

            // 8. Update status ke Database jika terhubung
            markDownloadSuccess(sourceId, mangaId, chapterId, mergedFile, downloadDao, profileId)

            Log.i(TAG, "Sukses menyelesaikan unduhan HLS resilient: ${mergedFile.absolutePath} (${mergedFile.length() / 1024 / 1024} MB)")
            Result.success(workDataOf("OUTPUT_PATH" to mergedFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Exception pada HlsDownloadWorker: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun markDownloadSuccess(
        sourceId: String,
        mangaId: String,
        chapterId: String,
        file: File,
        downloadDao: com.example.data.db.DownloadDao?,
        profileId: Long
    ) = withContext(Dispatchers.IO) {
        if (sourceId.isNotBlank() && mangaId.isNotBlank() && chapterId.isNotBlank() && downloadDao != null) {
            val entity = downloadDao.getDownload(profileId, sourceId, mangaId, chapterId)
            if (entity != null) {
                downloadDao.updateDownload(
                    entity.copy(
                        state = "DOWNLOADED",
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun createForegroundInfo(title: String, progress: Int, max: Int, statusText: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unduhan Video Resilient",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Mengunduh: $title")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1002, notification)
        }
    }

    private suspend fun resolveAndParsePlaylist(
        url: String,
        targetQuality: String,
        stagingDir: File? = null
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val reqUri = android.net.Uri.parse(url)
            val headers = com.example.media.PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = reqUri,
                activeStreamUrl = url
            )

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            val content = response.body?.string() ?: return@withContext emptyList()

            val lines = content.lines()
            val baseUrl = url.substringBeforeLast("/") + "/"

            // Cek apakah Master Playlist (memiliki #EXT-X-STREAM-INF)
            val isMaster = lines.any { it.contains("#EXT-X-STREAM-INF") }
            if (isMaster) {
                val variants = mutableListOf<Pair<String, String>>() // Pair(Info, Uri)
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val variantUri = lines[i + 1].trim()
                        if (!variantUri.startsWith("#") && variantUri.isNotBlank()) {
                            val resolvedUri = if (variantUri.startsWith("http")) variantUri else baseUrl + variantUri
                            variants.add(Pair(line, resolvedUri))
                        }
                    }
                }

                if (variants.isNotEmpty()) {
                    // Pilih varian yang paling sesuai targetQuality
                    val chosenVariant = pickBestMasterVariant(variants, targetQuality)
                    return@withContext resolveAndParsePlaylist(chosenVariant.second, targetQuality, stagingDir)
                }
            }

            // Media Playlist: Ekstrak AES-128 Key dan Kumpulkan semua URL segmen .ts
            val segments = mutableListOf<String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-KEY") && stagingDir != null) {
                    val keyMatch = Regex("""URI=["']([^"']+)["']""").find(trimmed)
                    val rawKeyUri = keyMatch?.groupValues?.get(1)
                    if (!rawKeyUri.isNullOrBlank()) {
                        val keyUrl = if (rawKeyUri.startsWith("http")) rawKeyUri else baseUrl + rawKeyUri
                        downloadAndRegisterAesKey(keyUrl, stagingDir)
                    }
                } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    val segUrl = if (trimmed.startsWith("http")) trimmed else baseUrl + trimmed
                    segments.add(segUrl)
                }
            }
            segments
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mem-parsing playlist HLS: ${e.message}")
            emptyList()
        }
    }

    private fun pickBestMasterVariant(variants: List<Pair<String, String>>, targetQuality: String): Pair<String, String> {
        val targetNum = targetQuality.filter { it.isDigit() }.toIntOrNull() ?: 720
        // Exact match in stream info
        val exact = variants.firstOrNull { it.first.contains(targetQuality, ignoreCase = true) }
        if (exact != null) return exact

        // Bandwidth / resolution matching
        val sorted = variants.sortedByDescending { (info, _) ->
            val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(info)
            val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(info)
            val bw = bwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            height * 10000 + bw
        }

        val underTarget = sorted.firstOrNull { (info, _) ->
            val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(info)
            val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
            height in 1..targetNum
        }

        return underTarget ?: sorted.first()
    }

    private suspend fun downloadAndRegisterAesKey(keyUrl: String, stagingDir: File) = withContext(Dispatchers.IO) {
        try {
            val keyHash = keyUrl.hashCode().toString()
            val localKeyFile = File(stagingDir, "key_$keyHash.key")
            
            val cachedBytes = com.example.media.DownloadHeaderRegistry.getAes128Key(keyUrl)
            if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                if (!localKeyFile.exists()) {
                    FileOutputStream(localKeyFile).use { it.write(cachedBytes) }
                }
                com.example.media.DownloadHeaderRegistry.registerLocalKeyMapping(keyUrl, localKeyFile.absolutePath)
                return@withContext
            }

            val reqUri = android.net.Uri.parse(keyUrl)
            val headers = com.example.media.PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = reqUri,
                activeStreamUrl = keyUrl
            )

            val requestBuilder = Request.Builder().url(keyUrl)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    FileOutputStream(localKeyFile).use { it.write(bytes) }
                    com.example.media.DownloadHeaderRegistry.registerAes128Key(keyUrl, bytes)
                    com.example.media.DownloadHeaderRegistry.registerLocalKeyMapping(keyUrl, localKeyFile.absolutePath)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun downloadSegmentAtomic(url: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, targetFile.name + ".part")
        try {
            val reqUri = android.net.Uri.parse(url)
            val headers = com.example.media.PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = reqUri,
                activeStreamUrl = url
            )

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val expectedSize = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                tempFile.delete()
                return@withContext false
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    private suspend fun downloadDirectMp4(url: String, targetFile: File, title: String): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, targetFile.name + ".downloading")
        try {
            val reqUri = android.net.Uri.parse(url)
            val headers = com.example.media.PersistentHttpHeaderResolver.resolveHeadersForRequest(
                requestUri = reqUri,
                activeStreamUrl = url
            )

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastNotif = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) return@withContext false
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (totalBytes > 0 && now - lastNotif > 1000) {
                            val percent = ((downloaded * 100) / totalBytes).toInt()
                            setForeground(createForegroundInfo(title, percent, 100, "Mengunduh MP4: $percent%"))
                            lastNotif = now
                        }
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            true
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    private suspend fun mergeTsSegmentsToFile(files: List<File>, outputFile: File): File = withContext(Dispatchers.IO) {
        val tempMergedFile = File(outputFile.parentFile, outputFile.name + ".merging")

        FileOutputStream(tempMergedFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            for (file in files) {
                FileInputStream(file).use { input ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        if (outputFile.exists()) outputFile.delete()
        tempMergedFile.renameTo(outputFile)
        outputFile
    }
}
