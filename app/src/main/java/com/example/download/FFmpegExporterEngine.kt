package com.example.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegExporterEngine {

    private const val TAG = "FFmpegExporterEngine"

    /**
     * Builds the exact FFmpeg command for stream copying (-c copy).
     */
    fun buildExportCommand(inputPath: String, outputPath: String): String {
        return "-y -i \"$inputPath\" -c copy \"$outputPath\""
    }

    /**
     * Converts/remuxes HLS m3u8 or video chunks into a solid MP4 using FFmpegKit stream copying (-c copy)
     * without re-encoding CPU load.
     */
    suspend fun exportToGalleryMp4(
        context: Context,
        inputPath: String,
        outputTitle: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val safeTitle = outputTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val fileName = "$safeTitle.mp4"

            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "OtakuHub"
            )
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }

            val tempOutputFile = File(moviesDir, "${safeTitle}_temp_${System.currentTimeMillis()}.mp4")
            val finalOutputFile = File(moviesDir, fileName)

            val command = buildExportCommand(inputPath, tempOutputFile.absolutePath)
            Log.i(TAG, "Executing FFmpeg stream copy command: $command")

            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode)) {
                if (tempOutputFile.exists() && tempOutputFile.length() > 0) {
                    if (finalOutputFile.exists()) finalOutputFile.delete()
                    val renamed = tempOutputFile.renameTo(finalOutputFile)
                    val targetFile = if (renamed) finalOutputFile else tempOutputFile

                    // Register with MediaStore System Gallery
                    val galleryUri = registerWithMediaStore(context, targetFile, fileName)

                    Log.i(TAG, "FFmpeg export succeeded: ${targetFile.absolutePath}, galleryUri: $galleryUri")
                    return@withContext Pair(true, "Video berhasil diekspor ke Galeri Sistem: ${targetFile.name}")
                } else {
                    Log.e(TAG, "FFmpeg returned success but output file is empty")
                    return@withContext Pair(false, "Gagal memproses ekspor berkas video (File kosong)")
                }
            } else {
                val failLog = session.failStackTrace ?: session.output ?: "Unknown error"
                Log.e(TAG, "FFmpeg export failed with return code: $returnCode. Log: $failLog")
                return@withContext Pair(false, "Gagal mengonversi video dengan FFmpeg: ${session.state}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during FFmpeg export: ${e.message}", e)
            return@withContext Pair(false, "Terjadi kesalahan saat mengekspor: ${e.message}")
        }
    }

    /**
     * Registers an exported MP4 file into the System MediaStore database so it shows up in system gallery immediately.
     */
    fun registerWithMediaStore(context: Context, file: File, displayName: String): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.SIZE, file.length())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OtakuHub")
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                } else {
                    @Suppress("DEPRECATION")
                    put(MediaStore.Video.Media.DATA, file.absolutePath)
                }
            }

            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            resolver.insert(collection, contentValues)
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering file with MediaStore: ${e.message}")
            null
        }
    }
}
