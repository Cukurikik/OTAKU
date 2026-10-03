package com.example.storage

import android.content.Context
import java.io.File

/**
 * AppStorageDirectoryConfig:
 * Konfigurasi direktori sandboxed internal aplikasi (`noBackupFilesDir` / `filesDir`)
 * untuk menjamin isolasi total dari Android MediaStore scanner (bebas pencemaran galeri)
 * serta proteksi dari cleaner agresif sistem operasi.
 */
object AppStorageDirectoryConfig {

    private const val SECURE_VAULT_DIR_NAME = "media_vault"
    private const val SECURE_MANGA_DIR_NAME = "secure_manga"
    private const val SECURE_VIDEO_DIR_NAME = "secure_video_cache"
    private const val NO_MEDIA_FILE_NAME = ".nomedia"

    /**
     * Mengembalikan root direktori sandboxed privat aplikasi yang tidak dibackup ke cloud Google Drive.
     */
    fun getSandboxedBaseDir(context: Context): File {
        val baseDir = context.noBackupFilesDir ?: context.filesDir
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        ensureNoMediaFlag(baseDir)
        return baseDir
    }

    /**
     * Mengembalikan direktori penyimpanan vault terenkripsi umum.
     */
    fun getSecureVaultDir(context: Context): File {
        val vaultDir = File(getSandboxedBaseDir(context), SECURE_VAULT_DIR_NAME)
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        ensureNoMediaFlag(vaultDir)
        return vaultDir
    }

    /**
     * Mengembalikan direktori sandboxed untuk bab-bab manga/komik terenkripsi per sumber dan judul.
     */
    fun getSecureMangaDir(context: Context, sourceId: String, mangaId: String): File {
        val safeSource = sanitizePath(sourceId)
        val safeManga = sanitizePath(mangaId)
        val mangaDir = File(getSandboxedBaseDir(context), "$SECURE_MANGA_DIR_NAME/$safeSource/$safeManga")
        if (!mangaDir.exists()) {
            mangaDir.mkdirs()
        }
        ensureNoMediaFlag(mangaDir)
        mangaDir.parentFile?.let { ensureNoMediaFlag(it) }
        return mangaDir
    }

    /**
     * Mengembalikan direktori sandboxed untuk cache chunk video HLS terenkripsi.
     */
    fun getSecureVideoCacheDir(context: Context): File {
        val videoDir = File(getSandboxedBaseDir(context), SECURE_VIDEO_DIR_NAME)
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
        ensureNoMediaFlag(videoDir)
        return videoDir
    }

    /**
     * Memastikan berkas .nomedia selalu ada di dalam folder untuk mencegah MediaStore indexing.
     */
    fun ensureNoMediaFlag(directory: File) {
        try {
            if (directory.exists() && directory.isDirectory) {
                val noMediaFile = File(directory, NO_MEDIA_FILE_NAME)
                if (!noMediaFile.exists()) {
                    noMediaFile.createNewFile()
                }
            }
        } catch (_: Exception) {}
    }

    private fun sanitizePath(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").take(100)
    }
}
