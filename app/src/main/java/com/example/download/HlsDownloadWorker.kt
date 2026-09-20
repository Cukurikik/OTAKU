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
 * Engine pengunduhan video HLS (.m3u8) dengan dukungan Segment Checkpointing,
 * Atomic Retry per segmen .ts, validasi integritas via HlsChunkVerifier, dan penggabungan aman.
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val playlistUrl = inputData.getString("PLAYLIST_URL")
            ?: return@withContext Result.failure(workDataOf("ERROR" to "No playlist URL provided"))
        val title = inputData.getString("TITLE") ?: "Video Download"
        val outputFileName = inputData.getString("OUTPUT_FILE_NAME") ?: "video_${System.currentTimeMillis()}.mp4"
        val sourceId = inputData.getString("SOURCE_ID") ?: ""
        val mangaId = inputData.getString("MANGA_ID") ?: ""
        val chapterId = inputData.getString("CHAPTER_ID") ?: ""

        val app = context.applicationContext as? OtakuHubApp
        val downloadDao = app?.database?.downloadDao()
        val profileId = com.example.data.ProfileManager.currentProfileIdValue

        try {
            // Run Storage Janitor task to clean up old staging folders (>24h) and prevent storage leak
            StoragePathManager.runStorageJanitorTask(context)

            setForeground(createForegroundInfo(title, 0, 100, "Menganalisis playlist HLS..."))

            // 1. Siapkan Staging Folder untuk Chunk Resiliency & Key Caching
            val videoSafeHash = playlistUrl.hashCode().toString()
            val stagingDir = File(context.cacheDir, "tmp_hls_$videoSafeHash")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }

            // 2. Parsing M3U8 Playlist (mendukung Master playlist atau Media playlist langsung & AES-128 keys)
            val segments = resolveAndParsePlaylist(playlistUrl, stagingDir)
            if (segments.isEmpty()) {
                Log.e(TAG, "Tidak ada segmen media yang ditemukan pada playlist: $playlistUrl")
                return@withContext Result.failure(workDataOf("ERROR" to "No media segments in M3U8"))
            }

            val totalSegments = segments.size
            Log.i(TAG, "Memulai unduhan HLS resilient: $totalSegments segmen untuk '$title'")

            val downloadedFiles = mutableListOf<File>()

            // 3. Iterasi dan Unduh setiap segmen dengan Atomic Retry & Verifikasi Integritas
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

            // 4. Penggabungan (*Muxing*) Seluruh Segmen TS Menjadi Berkas MP4 Final
            setForeground(createForegroundInfo(title, 99, 100, "Menggabungkan dan memvalidasi kontainer video final..."))
            val outputFile = mergeTsSegments(downloadedFiles, outputFileName)

            // 5. Validasi Integritas Berkas Final
            if (!outputFile.exists() || outputFile.length() < 1024) {
                Log.e(TAG, "Berkas MP4 hasil gabungan tidak valid atau kosong: ${outputFile.absolutePath}")
                return@withContext Result.failure(workDataOf("ERROR" to "Final merged video is invalid"))
            }

            // 6. Bersihkan Berkas Chunk Staging Sementara
            stagingDir.deleteRecursively()

            // 7. Update status ke Database jika terhubung
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

            Log.i(TAG, "Sukses menyelesaikan unduhan HLS resilient: ${outputFile.absolutePath} (${outputFile.length() / 1024 / 1024} MB)")
            Result.success(workDataOf("OUTPUT_PATH" to outputFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Exception pada HlsDownloadWorker: ${e.message}", e)
            Result.retry()
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

    private suspend fun resolveAndParsePlaylist(url: String, stagingDir: File? = null): List<String> = withContext(Dispatchers.IO) {
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
                var bestVariantUrl = ""
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF") && i + 1 < lines.size) {
                        val variantUri = lines[i + 1].trim()
                        if (!variantUri.startsWith("#") && variantUri.isNotBlank()) {
                            bestVariantUrl = if (variantUri.startsWith("http")) variantUri else baseUrl + variantUri
                            // Prioritaskan variant beresolusi tinggi jika ada
                            if (line.contains("1080") || line.contains("720")) break
                        }
                    }
                }
                if (bestVariantUrl.isNotBlank()) {
                    return@withContext resolveAndParsePlaylist(bestVariantUrl, stagingDir)
                }
            }

            // Media Playlist: Ekstrak AES-128 Key dan Kumpulkan semua URL segmen .ts
            val segments = mutableListOf<String>()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-KEY") && stagingDir != null) {
                    // Extract URI="keyUrl"
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

    private suspend fun downloadAndRegisterAesKey(keyUrl: String, stagingDir: File) = withContext(Dispatchers.IO) {
        try {
            val keyHash = keyUrl.hashCode().toString()
            val localKeyFile = File(stagingDir, "key_$keyHash.key")
            
            // Check if already cached in memory or file
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
                val keyBytes = response.body?.bytes()
                if (keyBytes != null && keyBytes.isNotEmpty()) {
                    FileOutputStream(localKeyFile).use { it.write(keyBytes) }
                    com.example.media.DownloadHeaderRegistry.registerAes128Key(keyUrl, keyBytes)
                    com.example.media.DownloadHeaderRegistry.registerLocalKeyMapping(keyUrl, localKeyFile.absolutePath)
                    Log.d(TAG, "Successfully downloaded & registered AES-128 key for: $keyUrl")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download AES-128 key from $keyUrl: ${e.message}")
        }
    }

    private suspend fun downloadSegmentAtomic(url: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")
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

            // Verifikasi ukuran sebelum rename
            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                tempFile.delete()
                return@withContext false
            }

            // Atomic rename
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    private suspend fun mergeTsSegments(files: List<File>, outputFileName: String): File = withContext(Dispatchers.IO) {
        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val outputFile = File(downloadsDir, outputFileName)
        val tempMergedFile = File(downloadsDir, outputFileName + ".merging")

        FileOutputStream(tempMergedFile).use { output ->
            val buffer = ByteArray(64 * 1024) // 64KB stream buffer
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
