package com.example.download.compressor

import android.content.Context
import android.os.Environment
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class VideoStorageItem(
    val id: String,
    val title: String,
    val filePath: String,
    val currentBytes: Long,
    val isCompressed: Boolean,
    val originalBytes: Long,
    val savedBytes: Long,
    val profileUsed: CompressionProfile?,
    val isCompressing: Boolean = false,
    val progress: Float = 0f
)

data class StorageSavedStats(
    val totalVideoBytes: Long = 0L,
    val totalSavedBytes: Long = 0L,
    val potentialSavingsBytes: Long = 0L,
    val totalVideosCount: Int = 0,
    val compressedVideosCount: Int = 0,
    val uncompressedVideosCount: Int = 0,
    val activeCompressionsCount: Int = 0
) {
    val overallSavingsPercentage: Double
        get() {
            val totalBeforeCompression = totalVideoBytes + totalSavedBytes
            return if (totalBeforeCompression > 0L) {
                (totalSavedBytes.toDouble() / totalBeforeCompression.toDouble()) * 100.0
            } else 0.0
        }
}

object StorageSpaceSaverManager {

    private const val PREFS_NAME = "storage_space_saver_prefs"
    private const val KEY_COMPRESSION_PREFIX = "compressed_info_"

    private val _videoItems = MutableStateFlow<List<VideoStorageItem>>(emptyList())
    val videoItems: StateFlow<List<VideoStorageItem>> = _videoItems.asStateFlow()

    private val _stats = MutableStateFlow(StorageSavedStats())
    val stats: StateFlow<StorageSavedStats> = _stats.asStateFlow()

    private val _selectedProfile = MutableStateFlow(CompressionProfile.SPACE_SAVER)
    val selectedProfile: StateFlow<CompressionProfile> = _selectedProfile.asStateFlow()

    fun setSelectedProfile(profile: CompressionProfile) {
        _selectedProfile.value = profile
    }

    /**
     * Merekam metadata kompresi yang berhasil ke SharedPreferences.
     */
    fun recordCompression(
        context: Context,
        downloadId: String,
        filePath: String,
        profile: CompressionProfile,
        originalBytes: Long,
        compressedBytes: Long
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("downloadId", downloadId)
            put("filePath", filePath)
            put("profile", profile.name)
            put("originalBytes", originalBytes)
            put("compressedBytes", compressedBytes)
            put("savedBytes", (originalBytes - compressedBytes).coerceAtLeast(0L))
            put("timestamp", System.currentTimeMillis())
        }
        val key = KEY_COMPRESSION_PREFIX + File(filePath).name
        prefs.edit().putString(key, json.toString()).apply()
    }

    /**
     * Membaca rekaman metadata kompresi untuk file tertentu.
     */
    fun getCompressionInfo(context: Context, fileName: String): JSONObject? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_COMPRESSION_PREFIX + fileName, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Memindai direktori unduhan video lokal dan menyinkronkan state UI.
     */
    suspend fun refreshStorageData(context: Context) = withContext(Dispatchers.IO) {
        val moviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub")
        if (!moviesDir.exists()) {
            _videoItems.value = emptyList()
            _stats.value = StorageSavedStats()
            return@withContext
        }

        val videoFiles = moviesDir.listFiles { file ->
            file.isFile && (file.extension.equals("mp4", ignoreCase = true) ||
                    file.extension.equals("mkv", ignoreCase = true) ||
                    file.extension.equals("ts", ignoreCase = true)) &&
                    !file.name.contains("_temp_") &&
                    !file.name.contains("_compressed_") &&
                    !file.name.contains(".bak_")
        } ?: emptyArray()

        var totalBytes = 0L
        var totalSaved = 0L
        var potentialSaved = 0L
        var compressedCount = 0
        var uncompressedCount = 0

        val items = videoFiles.map { file ->
            val currentSize = file.length()
            totalBytes += currentSize

            val info = getCompressionInfo(context, file.name)
            val isCompressed = info != null
            val originalBytes = info?.optLong("originalBytes", currentSize) ?: currentSize
            val savedBytes = info?.optLong("savedBytes", 0L) ?: 0L
            val profileUsed = info?.optString("profile")?.let {
                try {
                    CompressionProfile.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }

            if (isCompressed) {
                compressedCount++
                totalSaved += savedBytes
            } else {
                uncompressedCount++
                potentialSaved += CompressionProfile.SPACE_SAVER.calculateEstimatedSavedBytes(currentSize)
            }

            // Bersihkan nama file untuk tampilan rapi
            val cleanTitle = file.nameWithoutExtension
                .replace("_", " ")
                .replace("-", " • ")

            VideoStorageItem(
                id = file.name,
                title = cleanTitle,
                filePath = file.absolutePath,
                currentBytes = currentSize,
                isCompressed = isCompressed,
                originalBytes = originalBytes,
                savedBytes = savedBytes,
                profileUsed = profileUsed
            )
        }

        _videoItems.value = items.sortedByDescending { it.currentBytes }
        _stats.value = StorageSavedStats(
            totalVideoBytes = totalBytes,
            totalSavedBytes = totalSaved,
            potentialSavingsBytes = potentialSaved,
            totalVideosCount = items.size,
            compressedVideosCount = compressedCount,
            uncompressedVideosCount = uncompressedCount
        )
    }

    /**
     * Memasukkan tugas kompresi video ke antrean WorkManager.
     */
    fun enqueueCompression(
        context: Context,
        filePath: String,
        title: String,
        profile: CompressionProfile = _selectedProfile.value
    ) {
        val file = File(filePath)
        val workName = "compress_video_${file.name.hashCode()}"

        val inputData = workDataOf(
            VideoCompressionWorker.KEY_INPUT_PATH to filePath,
            VideoCompressionWorker.KEY_PROFILE to profile.name,
            VideoCompressionWorker.KEY_TITLE to title,
            VideoCompressionWorker.KEY_DOWNLOAD_ID to file.name
        )

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<VideoCompressionWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("video_compression")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Mengompresi seluruh video yang belum terkompresi secara batch.
     */
    fun enqueueBatchCompression(
        context: Context,
        profile: CompressionProfile = _selectedProfile.value
    ) {
        val currentItems = _videoItems.value
        currentItems.filter { !it.isCompressed }.forEach { item ->
            enqueueCompression(
                context = context,
                filePath = item.filePath,
                title = item.title,
                profile = profile
            )
        }
    }
}
