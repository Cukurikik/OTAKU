package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.SettingsManager
import java.io.File
import java.io.FileOutputStream

/**
 * StoragePathManager manages download storage locations (Internal and SAF-based external storage)
 * and enforces media privacy protection using .nomedia isolation and file extension obfuscation.
 */
object StoragePathManager {

    private const val TAG = "StoragePathManager"
    const val NO_MEDIA_FILE_NAME = ".nomedia"
    const val OBFUSCATED_MEDIA_EXTENSION = ".dat"
    const val OBFUSCATED_BINARY_EXTENSION = ".bin"

    /**
     * Resolves the root downloads folder on local storage.
     * Always ensures the folder and .nomedia exist.
     */
    fun getRootDownloadDir(context: Context): File {
        val settings = SettingsManager(context)
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val rootDir = File(baseDir, "downloads")
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        if (settings.galleryIsolationEnabled) {
            ensureNoMedia(rootDir)
        }
        return rootDir
    }

    /**
     * Gets the storage directory for manga chapters under a specific source.
     */
    fun getMangaDownloadDir(context: Context, sourceId: String, mangaId: String): File {
        val root = getRootDownloadDir(context)
        val mangaDir = File(root, "manga/$sourceId/$mangaId")
        if (!mangaDir.exists()) {
            mangaDir.mkdirs()
        }
        val settings = SettingsManager(context)
        if (settings.galleryIsolationEnabled) {
            ensureNoMedia(mangaDir)
            mangaDir.parentFile?.let { ensureNoMedia(it) }
        }
        return mangaDir
    }

    /**
     * Gets the storage directory for ExoPlayer video cache and downloaded media streams.
     */
    fun getVideoCacheDir(context: Context): File {
        val root = getRootDownloadDir(context)
        val videoDir = File(root, "video_cache")
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
        val settings = SettingsManager(context)
        if (settings.galleryIsolationEnabled) {
            ensureNoMedia(videoDir)
        }
        return videoDir
    }

    /**
     * Gets the custom SAF DocumentFile if configured by the user.
     * Returns null if using default internal storage or if URI is invalid.
     */
    fun getCustomSafDirectory(context: Context): DocumentFile? {
        val settings = SettingsManager(context)
        val uriStr = settings.downloadStorageUri
        if (uriStr.isBlank()) return null

        return try {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.exists() && docFile.isDirectory) {
                if (settings.galleryIsolationEnabled) {
                    prepareSecureDirectory(context, docFile)
                }
                docFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing SAF directory: ${e.message}")
            null
        }
    }

    /**
     * Injects .nomedia file into a standard File directory to prevent Android MediaScanner from scanning.
     */
    fun ensureNoMedia(dir: File) {
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val noMediaFile = File(dir, NO_MEDIA_FILE_NAME)
            if (!noMediaFile.exists()) {
                noMediaFile.createNewFile()
                FileOutputStream(noMediaFile).use { it.write(0) }
                Log.d(TAG, "Created .nomedia in: ${dir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create .nomedia in ${dir.absolutePath}: ${e.message}")
        }
    }

    /**
     * Injects .nomedia file into a SAF DocumentFile directory.
     */
    fun prepareSecureDirectory(context: Context, targetDir: DocumentFile) {
        try {
            val existing = targetDir.findFile(NO_MEDIA_FILE_NAME)
            if (existing == null) {
                targetDir.createFile("application/octet-stream", NO_MEDIA_FILE_NAME)
                Log.d(TAG, "Created .nomedia in SAF folder: ${targetDir.uri}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create .nomedia in SAF folder: ${e.message}")
        }
    }

    /**
     * Applies .nomedia recursively to all download directories.
     */
    fun applyNoMediaToAll(context: Context) {
        try {
            val root = getRootDownloadDir(context)
            ensureNoMedia(root)
            root.walkTopDown().filter { it.isDirectory }.forEach { dir ->
                ensureNoMedia(dir)
            }

            // Also secure legacy / alternative cache folders
            val internalFiles = File(context.filesDir, "downloads")
            if (internalFiles.exists()) {
                internalFiles.walkTopDown().filter { it.isDirectory }.forEach { ensureNoMedia(it) }
            }

            val safDir = getCustomSafDirectory(context)
            if (safDir != null) {
                prepareSecureDirectory(context, safDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying .nomedia to all: ${e.message}")
        }
    }

    /**
     * Obfuscates file names by swapping known media extensions (.jpg, .png, .mp4, .m3u8, etc.) to .dat
     */
    fun getObfuscatedFileName(originalName: String, fallbackExtension: String = "dat"): String {
        val nameWithoutExt = originalName.substringBeforeLast(".")
        return "$nameWithoutExt.$fallbackExtension"
    }

    /**
     * Checks if a file has an obfuscated media extension.
     */
    fun isObfuscatedMedia(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".dat") || lower.endsWith(".bin")
    }

    /**
     * Human-readable label for the currently configured storage location.
     */
    fun getStorageLocationDisplay(context: Context): String {
        val settings = SettingsManager(context)
        val uriStr = settings.downloadStorageUri
        if (uriStr.isNotBlank()) {
            val safDoc = getCustomSafDirectory(context)
            if (safDoc != null) {
                return "SD Card / SAF: ${safDoc.name ?: "Folder Eksternal"}"
            }
        }
        val root = getRootDownloadDir(context)
        return "Internal App: ${root.absolutePath}"
    }

    /**
     * Persists SAF URI permission and updates SettingsManager.
     */
    fun setSafDirectory(context: Context, treeUri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: Exception) {
            Log.w(TAG, "Persistable permission error: ${e.message}")
        }

        val settings = SettingsManager(context)
        settings.downloadStorageUri = treeUri.toString()

        val docFile = DocumentFile.fromTreeUri(context, treeUri)
        if (docFile != null && settings.galleryIsolationEnabled) {
            prepareSecureDirectory(context, docFile)
        }
    }

    /**
     * Resets storage location to default internal app storage.
     */
    fun resetToDefaultStorage(context: Context) {
        val settings = SettingsManager(context)
        settings.downloadStorageUri = ""
        applyNoMediaToAll(context)
    }
}
