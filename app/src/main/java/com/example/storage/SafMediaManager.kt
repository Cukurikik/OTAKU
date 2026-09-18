package com.example.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.OtakuHubApp
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentType
import com.example.data.models.DownloadEntity
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Storage Access Framework (SAF) Media Manager.
 * Provides resilient export and import functionality for comic (.cbz/.zip)
 * and video (.mp4/.m3u8 offline packages) files to/from external user-chosen storage.
 */
object SafMediaManager {

    /**
     * Exports a downloaded comic (.cbz / .zip) to the target SAF document URI.
     */
    suspend fun exportComicToSaf(
        context: Context,
        sourceId: String,
        mangaId: String,
        chapterId: String,
        targetUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Find local CBZ file
            val mangaDir = StoragePathManager.getMangaDownloadDir(context, sourceId, mangaId)
            var sourceFile = File(mangaDir, "$chapterId.cbz")
            if (!sourceFile.exists()) {
                val altFile = File(context.filesDir, "downloads/$sourceId/$mangaId/$chapterId.cbz")
                if (altFile.exists()) {
                    sourceFile = altFile
                }
            }

            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext Result.failure(IOException("Berkas komik lokal tidak ditemukan: ${sourceFile.absolutePath}"))
            }

            var bytesCopied = 0L
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IOException("Gagal membuka stream output SAF target URI"))

            Result.success(bytesCopied)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a downloaded anime or drama video (.mp4 / .ts) to the target SAF document URI.
     */
    suspend fun exportVideoToSaf(
        context: Context,
        sourceId: String,
        mangaId: String,
        chapterId: String,
        targetUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val safeName = "${mangaId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}-${chapterId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}"
            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub")
            var sourceFile = File(targetDir, "$safeName.mp4")
            
            if (!sourceFile.exists()) {
                val tsFile = File(targetDir, "$safeName.ts")
                if (tsFile.exists()) {
                    sourceFile = tsFile
                } else {
                    val cacheFile = File(StoragePathManager.getVideoCacheDir(context), "$safeName.mp4")
                    if (cacheFile.exists()) {
                        sourceFile = cacheFile
                    }
                }
            }

            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext Result.failure(IOException("Berkas video lokal tidak ditemukan: ${sourceFile.absolutePath}"))
            }

            var bytesCopied = 0L
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IOException("Gagal membuka stream output SAF target URI"))

            Result.success(bytesCopied)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dispatches export based on DownloadEntity type.
     */
    suspend fun exportMediaEntityToSaf(
        context: Context,
        entity: DownloadEntity,
        targetUri: Uri
    ): Result<Long> {
        val isVideo = entity.sourceId.lowercase().let { 
            it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") || 
            it.contains("anichin") || it.contains("kura") || it.contains("kaze") || 
            it.contains("anoboy") || it.contains("nime") || it.contains("drama") || 
            it.contains("drakor") || it.contains("viki") || it.contains("licil") || 
            it.contains("film") || it.contains("jucelebrity") 
        } || entity.mimeType?.contains("video") == true || entity.mimeType?.contains("m3u8") == true

        return if (isVideo) {
            exportVideoToSaf(context, entity.sourceId, entity.mangaId, entity.chapterId, targetUri)
        } else {
            exportComicToSaf(context, entity.sourceId, entity.mangaId, entity.chapterId, targetUri)
        }
    }

    /**
     * Imports a comic (.cbz / .zip) from SAF source URI into local downloads and indexes it in Room DB.
     */
    suspend fun importComicFromSaf(
        context: Context,
        sourceUri: Uri,
        customTitle: String? = null
    ): Result<DownloadEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(context, sourceUri) ?: "imported_comic_${System.currentTimeMillis()}.cbz"
            val title = customTitle ?: fileName.substringBeforeLast(".")
            val safeMangaId = "saf_comic_" + title.replace("[^a-zA-Z0-9_-]".toRegex(), "_").lowercase()
            val chapterId = "ch_1"
            val sourceId = "saf_import"
            val profileId = com.example.data.ProfileManager.currentProfileIdValue

            val targetDir = StoragePathManager.getMangaDownloadDir(context, sourceId, safeMangaId)
            val destFile = File(targetDir, "$chapterId.cbz")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IOException("Gagal membaca berkas dari SAF URI"))

            // Register in database
            val app = context.applicationContext as? OtakuHubApp
            val db = app?.database
            if (db != null) {
                val libraryEntry = LibraryEntry(
                    profileId = profileId,
                    mangaId = safeMangaId,
                    sourceId = sourceId,
                    title = title,
                    thumbnailUrl = "cbz://${destFile.absolutePath}::cover",
                    addedAt = System.currentTimeMillis(),
                    favorite = true,
                    unreadCount = 0
                )
                db.libraryDao().insertLibraryEntry(libraryEntry)

                val chapterEntry = ChapterEntry(
                    profileId = profileId,
                    mangaId = safeMangaId,
                    sourceId = sourceId,
                    chapterId = chapterId,
                    name = title,
                    url = "file://${destFile.absolutePath}",
                    dateUpload = System.currentTimeMillis(),
                    number = 1.0f,
                    downloaded = true
                )
                db.chapterDao().insertChapters(listOf(chapterEntry))

                val downloadEntity = DownloadEntity(
                    profileId = profileId,
                    sourceId = sourceId,
                    mangaId = safeMangaId,
                    chapterId = chapterId,
                    state = "DOWNLOADED",
                    completedAt = System.currentTimeMillis()
                )
                db.downloadDao().insertDownload(downloadEntity)

                return@withContext Result.success(downloadEntity)
            }

            val fallbackEntity = DownloadEntity(
                profileId = profileId,
                sourceId = sourceId,
                mangaId = safeMangaId,
                chapterId = chapterId,
                state = "DOWNLOADED",
                completedAt = System.currentTimeMillis()
            )
            Result.success(fallbackEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports a video (.mp4 / .mkv / .ts) from SAF source URI into local movies and indexes it in Room DB.
     */
    suspend fun importVideoFromSaf(
        context: Context,
        sourceUri: Uri,
        customTitle: String? = null
    ): Result<DownloadEntity> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(context, sourceUri) ?: "imported_video_${System.currentTimeMillis()}.mp4"
            val title = customTitle ?: fileName.substringBeforeLast(".")
            val safeMangaId = "saf_video_" + title.replace("[^a-zA-Z0-9_-]".toRegex(), "_").lowercase()
            val chapterId = "ep_1"
            val sourceId = "saf_import_anime"
            val profileId = com.example.data.ProfileManager.currentProfileIdValue

            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub")
            if (!targetDir.exists()) targetDir.mkdirs()
            StoragePathManager.ensureNoMedia(targetDir)

            val safeName = "${safeMangaId}-${chapterId}"
            val destFile = File(targetDir, "$safeName.mp4")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IOException("Gagal membaca berkas video dari SAF URI"))

            // Register in database
            val app = context.applicationContext as? OtakuHubApp
            val db = app?.database
            if (db != null) {
                val libraryEntry = LibraryEntry(
                    profileId = profileId,
                    mangaId = safeMangaId,
                    sourceId = sourceId,
                    title = title,
                    thumbnailUrl = "",
                    addedAt = System.currentTimeMillis(),
                    favorite = true,
                    unreadCount = 0
                )
                db.libraryDao().insertLibraryEntry(libraryEntry)

                val chapterEntry = ChapterEntry(
                    profileId = profileId,
                    mangaId = safeMangaId,
                    sourceId = sourceId,
                    chapterId = chapterId,
                    name = title,
                    url = "file://${destFile.absolutePath}",
                    dateUpload = System.currentTimeMillis(),
                    number = 1.0f,
                    downloaded = true
                )
                db.chapterDao().insertChapters(listOf(chapterEntry))

                val downloadEntity = DownloadEntity(
                    profileId = profileId,
                    sourceId = sourceId,
                    mangaId = safeMangaId,
                    chapterId = chapterId,
                    state = "DOWNLOADED",
                    mimeType = "video/mp4",
                    completedAt = System.currentTimeMillis()
                )
                db.downloadDao().insertDownload(downloadEntity)

                return@withContext Result.success(downloadEntity)
            }

            val fallbackEntity = DownloadEntity(
                profileId = profileId,
                sourceId = sourceId,
                mangaId = safeMangaId,
                chapterId = chapterId,
                state = "DOWNLOADED",
                mimeType = "video/mp4",
                completedAt = System.currentTimeMillis()
            )
            Result.success(fallbackEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back to URI last path segment
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
