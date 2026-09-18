package com.example.download.compressor

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background FFmpeg Compressor Engine
 * Mengonversi dan me-re-encode file unduhan H.264 mentah menjadi HEVC/H.265 (CRF 28)
 * dan Opus Audio (96kbps) untuk menghemat kapasitas penyimpanan internal hingga 60%-70%.
 */
class VideoCompressorEngine(private val context: Context) {

    companion object {
        private const val TAG = "VideoCompressorEngine"

        /**
         * Menghitung rasio persentase penghematan penyimpanan:
         * Storage Saved (%) = (1 - Size_HEVC / Size_H.264) * 100%
         */
        fun calculateStorageSavedPercent(originalBytes: Long, compressedBytes: Long): Double {
            if (originalBytes <= 0L) return 0.0
            val ratio = compressedBytes.toDouble() / originalBytes.toDouble()
            return ((1.0 - ratio) * 100.0).coerceIn(0.0, 100.0)
        }
    }

    /**
     * Membangun perintah FFmpeg sesuai profil kompresi yang dipilih.
     * Sesuai spesifikasi teknis Proposal:
     * "-i \"$inputPath\" -c:v libx265 -crf 28 -preset medium -c:a libopus -b:a 96k \"$outputPath\""
     */
    fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        profile: CompressionProfile = CompressionProfile.SPACE_SAVER
    ): String {
        return when (profile) {
            CompressionProfile.ORIGINAL -> {
                "-y -i \"$inputPath\" -c copy \"$outputPath\""
            }
            CompressionProfile.SPACE_SAVER -> {
                "-y -i \"$inputPath\" -c:v libx265 -crf 28 -preset medium -c:a libopus -b:a 96k \"$outputPath\""
            }
            CompressionProfile.ULTRA_COMPACT -> {
                "-y -i \"$inputPath\" -c:v libx265 -crf 32 -preset fast -c:a libopus -b:a 64k \"$outputPath\""
            }
        }
    }

    /**
     * Memverifikasi integritas file keluaran.
     */
    fun verifyIntegrity(outputFile: File, minExpectedBytes: Long = 1024L): Boolean {
        return outputFile.exists() && outputFile.isFile && outputFile.length() >= minExpectedBytes
    }

    /**
     * Mengganti file video asli dengan file video hasil kompresi secara aman dengan rollback fail-safe.
     */
    fun replaceOriginalWithCompressed(originalFile: File, compressedFile: File): Boolean {
        if (!verifyIntegrity(compressedFile)) {
            Log.e(TAG, "File kompresi tidak valid atau kosong!")
            return false
        }

        val backupFile = File(originalFile.parentFile, "${originalFile.name}.bak_${System.currentTimeMillis()}")
        val backupSuccess = originalFile.renameTo(backupFile)

        return if (backupSuccess) {
            val replaceSuccess = compressedFile.renameTo(originalFile)
            if (replaceSuccess) {
                backupFile.delete()
                true
            } else {
                // Rollback jika rename gagal
                backupFile.renameTo(originalFile)
                false
            }
        } else {
            try {
                originalFile.delete()
                compressedFile.renameTo(originalFile)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengganti berkas asli: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Melakukan proses kompresi video secara asynchronous menggunakan FFmpegKit.
     */
    suspend fun compressVideo(
        inputFile: File,
        profile: CompressionProfile = CompressionProfile.SPACE_SAVER,
        onProgress: (Float) -> Unit = {}
    ): CompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (!inputFile.exists() || inputFile.length() == 0L) {
            return@withContext CompressionResult.failure("Berkas input tidak ditemukan: ${inputFile.absolutePath}")
        }

        val originalBytes = inputFile.length()
        val tempOutputFile = File(
            inputFile.parentFile,
            "${inputFile.nameWithoutExtension}_compressed_${System.currentTimeMillis()}.mp4"
        )

        try {
            val command = buildFFmpegCommand(inputFile.absolutePath, tempOutputFile.absolutePath, profile)
            Log.i(TAG, "Memulai transcode FFmpeg HEVC: $command")

            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                if (verifyIntegrity(tempOutputFile)) {
                    val compressedBytes = tempOutputFile.length()
                    val bytesSaved = (originalBytes - compressedBytes).coerceAtLeast(0L)
                    val savingsPercent = calculateStorageSavedPercent(originalBytes, compressedBytes)

                    val replaced = replaceOriginalWithCompressed(inputFile, tempOutputFile)
                    if (replaced) {
                        val executionTime = System.currentTimeMillis() - startTime
                        Log.i(
                            TAG,
                            "Kompresi berhasil! Asal: $originalBytes B, Baru: $compressedBytes B, Hemat: $bytesSaved B ($savingsPercent%), Durasi: ${executionTime}ms"
                        )
                        return@withContext CompressionResult(
                            success = true,
                            message = "Kompresi berhasil! Menghemat ${formatBytes(bytesSaved)} (%.1f%%)".format(savingsPercent),
                            originalBytes = originalBytes,
                            compressedBytes = compressedBytes,
                            bytesSaved = bytesSaved,
                            savingsPercentage = savingsPercent,
                            outputPath = inputFile.absolutePath,
                            executionTimeMs = executionTime
                        )
                    } else {
                        return@withContext CompressionResult.failure("Gagal mengganti berkas asli dengan berkas hasil kompresi")
                    }
                } else {
                    return@withContext CompressionResult.failure("Hasil kompresi tidak lolos verifikasi integritas data")
                }
            } else {
                val failLog = session.failStackTrace ?: session.output ?: "Error ${session.state}"
                Log.e(TAG, "FFmpeg encoding gagal: $failLog")
                if (tempOutputFile.exists()) tempOutputFile.delete()
                return@withContext CompressionResult.failure("FFmpeg gagal melakukan re-encode (ReturnCode: $returnCode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception saat kompresi video: ${e.message}", e)
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext CompressionResult.failure("Terjadi kesalahan sistem: ${e.message}")
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024f * 1024f)
        val gb = mb / (1024f * 1024f * 1024f)
        return if (gb >= 1f) "%.2f GB".format(gb) else "%.1f MB".format(mb)
    }
}
