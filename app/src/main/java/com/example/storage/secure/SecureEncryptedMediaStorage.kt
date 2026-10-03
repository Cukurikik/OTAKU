package com.example.storage.secure

import android.content.Context
import com.example.storage.AppStorageDirectoryConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * SecureEncryptedMediaStorage:
 * Engine penyimpanan sandboxed terenkripsi menggunakan Magic Header XOR bytes
 * pada direktori privat aplikasi (`noBackupFilesDir`).
 *
 * Formulasi:
 * Header_enc = MagicBytes XOR SecretKey_app (16 bytes)
 * Storage_overhead = 16 bytes / 2097152 bytes ≈ 0.000763%
 * DecryptedStream = f_stream(EncryptedFile, Header_enc) -> BitmapPool
 */
class SecureEncryptedMediaStorage(private val context: Context) {

    companion object {
        const val HEADER_SIZE_BYTES = 16
        const val ENCPACK_EXTENSION = ".encpack"
        const val ENCRYPTED_CBZ_EXTENSION = ".cbz"

        // 16-byte Magic Signature String ("SECURE_MEDIA_V1_")
        private val RAW_MAGIC_BYTES = byteArrayOf(
            0x53, 0x45, 0x43, 0x55, 0x52, 0x45, 0x5F, 0x4D, // "SECURE_M"
            0x45, 0x44, 0x49, 0x41, 0x5F, 0x56, 0x31, 0x5F  // "EDIA_V1_"
        )

        // 16-byte Secret App Key Salt
        private val APP_SECRET_KEY = byteArrayOf(
            0x7A, 0x3F, 0x1B, 0x99.toByte(), 0x4D, 0x82.toByte(), 0x11, 0xEF.toByte(),
            0x33, 0x5C, 0xA1.toByte(), 0x77, 0x24, 0x9B.toByte(), 0xDE.toByte(), 0x05
        )

        // Header terenkripsi: Header_enc = MagicBytes ^ SecretKey_app
        val ENCRYPTED_HEADER_SIGNATURE: ByteArray = ByteArray(HEADER_SIZE_BYTES) { i ->
            (RAW_MAGIC_BYTES[i].toInt() xor APP_SECRET_KEY[i].toInt()).toByte()
        }

        private fun getScrambleKeyByte(index: Int): Int {
            return (ENCRYPTED_HEADER_SIGNATURE[index % HEADER_SIZE_BYTES].toInt() xor
                    APP_SECRET_KEY[index % HEADER_SIZE_BYTES].toInt()) and 0xFF
        }
    }

    /**
     * Memeriksa apakah berkas memiliki signature Magic Header XOR yang valid.
     */
    fun isEncryptedMedia(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_SIZE_BYTES) return false
        return try {
            FileInputStream(file).use { input ->
                val headerBuf = ByteArray(HEADER_SIZE_BYTES)
                val read = input.read(headerBuf)
                if (read < HEADER_SIZE_BYTES) return false
                headerBuf.contentEquals(ENCRYPTED_HEADER_SIGNATURE)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menyimpan raw byte array menjadi berkas terenkripsi ke target File tertentu.
     */
    fun saveEncryptedMedia(targetFile: File, rawBytes: ByteArray): File {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { output ->
            // 1. Tulis 16-byte Magic Header
            output.write(ENCRYPTED_HEADER_SIGNATURE)

            // 2. Tulis scrambled payload
            val buffer = ByteArray(rawBytes.size)
            for (i in rawBytes.indices) {
                val key = getScrambleKeyByte(i)
                buffer[i] = (rawBytes[i].toInt() xor key).toByte()
            }
            output.write(buffer)
            output.flush()
        }
        return targetFile
    }

    /**
     * Menyimpan raw byte array menjadi berkas terenkripsi dengan Magic Header XOR di direktori vault.
     */
    fun saveEncryptedMedia(fileName: String, rawBytes: ByteArray, subDir: String = "vault"): File {
        val baseDir = if (subDir == "vault") {
            AppStorageDirectoryConfig.getSecureVaultDir(context)
        } else {
            File(AppStorageDirectoryConfig.getSandboxedBaseDir(context), subDir).apply { mkdirs() }
        }
        val cleanName = if (fileName.endsWith(ENCPACK_EXTENSION) || fileName.endsWith(ENCRYPTED_CBZ_EXTENSION)) fileName else "$fileName$ENCPACK_EXTENSION"
        val targetFile = File(baseDir, cleanName)
        return saveEncryptedMedia(targetFile, rawBytes)
    }

    /**
     * Mengalirkan InputStream dan mengenkripsi langsung ke target File tanpa alokasi memori berlebih.
     */
    fun saveEncryptedStream(targetFile: File, inputStream: InputStream): File {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { output ->
            // 1. Tulis Magic Header
            output.write(ENCRYPTED_HEADER_SIGNATURE)

            // 2. Stream chunk scrambling
            val readBuffer = ByteArray(8192)
            var bytesRead: Int
            var totalIndex = 0L

            while (inputStream.read(readBuffer).also { bytesRead = it } != -1) {
                for (i in 0 until bytesRead) {
                    val key = getScrambleKeyByte((totalIndex + i).toInt())
                    readBuffer[i] = (readBuffer[i].toInt() xor key).toByte()
                }
                output.write(readBuffer, 0, bytesRead)
                totalIndex += bytesRead
            }
            output.flush()
        }
        return targetFile
    }

    /**
     * Mengalirkan InputStream dan mengenkripsi langsung ke direktori vault.
     */
    fun saveEncryptedStream(fileName: String, inputStream: InputStream, subDir: String = "vault"): File {
        val baseDir = if (subDir == "vault") {
            AppStorageDirectoryConfig.getSecureVaultDir(context)
        } else {
            File(AppStorageDirectoryConfig.getSandboxedBaseDir(context), subDir).apply { mkdirs() }
        }
        val cleanName = if (fileName.endsWith(ENCPACK_EXTENSION) || fileName.endsWith(ENCRYPTED_CBZ_EXTENSION)) fileName else "$fileName$ENCPACK_EXTENSION"
        val targetFile = File(baseDir, cleanName)
        return saveEncryptedStream(targetFile, inputStream)
    }

    /**
     * Membuka stream dekripsi langsung ke memori (Zero-Disk-Leak) untuk diproses oleh Coil / Bitmap decoder.
     */
    fun getDecryptedInputStream(encryptedFile: File): InputStream {
        if (!encryptedFile.exists()) {
            throw IllegalArgumentException("File not found: ${encryptedFile.absolutePath}")
        }

        val fis = FileInputStream(encryptedFile)
        val headerBuf = ByteArray(HEADER_SIZE_BYTES)
        val read = fis.read(headerBuf)

        // Jika file tidak memiliki magic header, kembalikan stream biasa (fallback kompatibilitas)
        if (read < HEADER_SIZE_BYTES || !headerBuf.contentEquals(ENCRYPTED_HEADER_SIGNATURE)) {
            fis.close()
            return FileInputStream(encryptedFile)
        }

        return DecryptedCipherInputStream(fis)
    }

    /**
     * Membaca seluruh berkas dan mengembalikan payload byte terdekripsi.
     */
    fun getDecryptedBytes(encryptedFile: File): ByteArray {
        getDecryptedInputStream(encryptedFile).use { input ->
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                bos.write(buf, 0, len)
            }
            return bos.toByteArray()
        }
    }

    /**
     * Mengemas sejumlah gambar/halaman menjadi kontainer arsip (.encpack / .cbz) terenkripsi dengan XOR header.
     */
    fun createEncryptedArchive(targetArchiveFile: File, entries: Map<String, ByteArray>): File {
        targetArchiveFile.parentFile?.mkdirs()
        val tempRawZip = File(targetArchiveFile.parentFile, targetArchiveFile.name + ".rawtmp")

        try {
            // 1. Buat zip buffer di memori atau temp internal
            ZipOutputStream(FileOutputStream(tempRawZip)).use { zos ->
                entries.forEach { (name, bytes) ->
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }

            // 2. Scramble dan tulis Magic Header ke target final
            FileInputStream(tempRawZip).use { fis ->
                saveEncryptedStream(targetArchiveFile, fis)
            }
        } finally {
            if (tempRawZip.exists()) tempRawZip.delete()
        }
        return targetArchiveFile
    }

    /**
     * Membaca entri gambar tertentu dari dalam arsip terenkripsi secara in-memory streaming.
     */
    fun readEncryptedArchiveEntry(archiveFile: File, entryName: String): ByteArray? {
        if (!archiveFile.exists()) return null

        getDecryptedInputStream(archiveFile).use { decryptedStream ->
            ZipInputStream(decryptedStream).use { zis ->
                var entry = zis.nextEntry
                val cleanTargetBase = entryName.substringBeforeLast(".")

                while (entry != null) {
                    val currentName = entry.name
                    val currentBase = currentName.substringBeforeLast(".")

                    if (currentName == entryName || (cleanTargetBase.isNotBlank() && currentBase == cleanTargetBase)) {
                        val bos = ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        var read: Int
                        while (zis.read(buf).also { read = it } != -1) {
                            bos.write(buf, 0, read)
                        }
                        return bos.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return null
    }

    /**
     * Mendaftar semua nama entri di dalam arsip terenkripsi tanpa menulis ke disk.
     */
    fun listEncryptedArchiveEntries(archiveFile: File): List<String> {
        if (!archiveFile.exists()) return emptyList()
        val entryList = mutableListOf<String>()

        try {
            getDecryptedInputStream(archiveFile).use { decryptedStream ->
                ZipInputStream(decryptedStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            entryList.add(entry.name)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Exception) {}
        return entryList
    }

    /**
     * InputStream wrapper yang men-descramble byte stream secara on-the-fly.
     */
    private class DecryptedCipherInputStream(private val rawStream: InputStream) : InputStream() {
        private var byteIndex = 0L

        override fun read(): Int {
            val raw = rawStream.read()
            if (raw == -1) return -1
            val key = getScrambleKeyByte((byteIndex++).toInt())
            return (raw xor key) and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = rawStream.read(b, off, len)
            if (bytesRead == -1) return -1

            for (i in 0 until bytesRead) {
                val key = getScrambleKeyByte((byteIndex + i).toInt())
                b[off + i] = (b[off + i].toInt() xor key).toByte()
            }
            byteIndex += bytesRead
            return bytesRead
        }

        override fun close() {
            rawStream.close()
        }
    }
}
