package com.example.storage.secure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * EncryptedBitmapPool:
 * Pool memori dan in-memory stream decoder untuk merender media terenkripsi
 * langsung ke Bitmap tanpa pernah menulis berkas unencrypted ke flash/disk storage.
 */
class EncryptedBitmapPool(private val context: Context) {

    private val secureStorage = SecureEncryptedMediaStorage(context)

    // LRU Cache untuk Bitmap memori (maksimum 25% dari total memori aplikasi)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Mendekode berkas terenkripsi langsung menjadi Bitmap dengan bounds sizing.
     */
    fun decodeDecryptedBitmap(
        encryptedFile: File,
        maxWidth: Int = 2048,
        maxHeight: Int = 4096,
        cacheKey: String? = null
    ): Bitmap? {
        val key = cacheKey ?: encryptedFile.absolutePath
        memoryCache.get(key)?.let { return it }

        return try {
            val decryptedBytes = secureStorage.getDecryptedBytes(encryptedFile)
            if (decryptedBytes.isEmpty()) return null

            val bitmap = decodeBytesToBitmap(decryptedBytes, maxWidth, maxHeight)
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Mendekode entri dari dalam arsip .encpack / .cbz terenkripsi langsung ke Bitmap.
     */
    fun decodeArchiveEntryToBitmap(
        archiveFile: File,
        entryName: String,
        maxWidth: Int = 2048,
        maxHeight: Int = 4096
    ): Bitmap? {
        val cacheKey = "${archiveFile.absolutePath}::$entryName"
        memoryCache.get(cacheKey)?.let { return it }

        return try {
            val entryBytes = secureStorage.readEncryptedArchiveEntry(archiveFile, entryName) ?: return null
            val bitmap = decodeBytesToBitmap(entryBytes, maxWidth, maxHeight)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Mendekode Stream terdekripsi ke Bitmap dengan efisiensi inSampleSize.
     */
    fun decodeStreamToBitmap(
        inputStream: InputStream,
        maxWidth: Int = 2048,
        maxHeight: Int = 4096
    ): Bitmap? {
        val bytes = inputStream.readBytes()
        return decodeBytesToBitmap(bytes, maxWidth, maxHeight)
    }

    private fun decodeBytesToBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (bytes.isEmpty()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
