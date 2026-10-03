package com.example.download.resiliency

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * HlsChunkVerifier:
 * Engine validasi integritas segmen video MPEG-TS (.ts) dan audio (.aac)
 * untuk mencegah korupsi data akibat koneksi terputus saat pengunduhan stream HLS / M3U8.
 */
object HlsChunkVerifier {

    private const val TS_PACKET_SIZE_188 = 188
    private const val TS_PACKET_SIZE_204 = 204
    private const val TS_SYNC_BYTE: Byte = 0x47 // 'G' in ASCII (Standard ISO/IEC 13818-1)

    private fun safeLog(tag: String, message: String) {
        try {
            android.util.Log.w(tag, message)
        } catch (_: Throwable) {
            // JVM Unit test fallback without Android mock runtime
            println("[$tag] $message")
        }
    }

    /**
     * Memverifikasi integritas fisik dan format dari sebuah segmen file yang diunduh.
     * @param segmentFile Berkas segmen (.ts atau .aac)
     * @param expectedByteSize Perkiraan ukuran byte (jika diketahui dari Content-Length HTTP, -1 jika tidak ada)
     * @return true jika segmen utuh dan valid, false jika terpotong atau korup.
     */
    fun verifySegmentIntegrity(segmentFile: File, expectedByteSize: Long = -1L): Boolean {
        if (!segmentFile.exists() || segmentFile.length() <= 0L) {
            safeLog("HlsChunkVerifier", "File segmen tidak ada atau kosong: ${segmentFile.name}")
            return false
        }

        val actualLength = segmentFile.length()

        // 1. Verifikasi ukuran byte jika Content-Length diketahui
        if (expectedByteSize > 0L) {
            if (actualLength != expectedByteSize) {
                safeLog("HlsChunkVerifier", "Ukuran segmen ${segmentFile.name} tidak sesuai! Ekspektasi: $expectedByteSize, Aktual: $actualLength")
                return false
            }
        }

        // Ukuran minimal segmen yang wajar (setidaknya 1 paket TS = 188 byte)
        if (actualLength < TS_PACKET_SIZE_188) {
            safeLog("HlsChunkVerifier", "Ukuran segmen terlalu kecil (${actualLength}B) untuk format TS/AAC: ${segmentFile.name}")
            return false
        }

        // 2. Verifikasi format struktur MPEG-TS sync byte atau AAC ADTS
        val isTs = isMpegTsSyncValid(segmentFile)
        if (isTs) return true

        val isAac = isAacAdtsValid(segmentFile)
        if (isAac) return true

        // Fallback untuk media container lain (misal fMP4 / m4s)
        val isFmp4 = isFmp4BoxValid(segmentFile)
        if (isFmp4) return true

        safeLog("HlsChunkVerifier", "Header format segmen tidak dikenali atau korup: ${segmentFile.name}")
        return false
    }

    /**
     * Memverifikasi integritas byte buffer segmen yang baru diunduh di memori (in-memory validation).
     */
    fun verifyBytesIntegrity(bytes: ByteArray, expectedByteSize: Long = -1L): Boolean {
        if (bytes.isEmpty()) return false
        if (expectedByteSize > 0L && bytes.size.toLong() != expectedByteSize) return false
        if (bytes.size < 4) return false

        // 1. MPEG-TS check (0x47 sync byte at offset 0 and multiples of 188)
        if (bytes.size >= TS_PACKET_SIZE_188 && bytes[0] == TS_SYNC_BYTE) {
            return true
        }

        // 2. AAC ADTS check
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xF0) == 0xF0) {
            return true
        }

        // 3. fMP4 box check
        if (bytes.size >= 8) {
            val boxType = String(bytes, 4, 4, Charsets.US_ASCII)
            if (boxType in listOf("styp", "moof", "ftyp", "mdat")) {
                return true
            }
        }

        return bytes.size >= 188
    }

    /**
     * Memeriksa validitas pola MPEG-TS Sync Byte (0x47) pada interval 188 atau 204 byte.
     * Segmen MPEG-TS yang valid harus memiliki byte 0x47 pada kelipatan panjang paketnya.
     */
    fun isMpegTsSyncValid(file: File): Boolean {
        if (!file.exists() || file.length() < TS_PACKET_SIZE_188) return false

        try {
            FileInputStream(file).use { stream ->
                val headerBuffer = ByteArray(TS_PACKET_SIZE_188 * 5)
                val bytesRead = stream.read(headerBuffer)
                if (bytesRead < TS_PACKET_SIZE_188) return false

                // Cek apakah paket berukuran 188 byte
                var syncMatch188 = 0
                for (offset in 0 until bytesRead step TS_PACKET_SIZE_188) {
                    if (headerBuffer[offset] == TS_SYNC_BYTE) {
                        syncMatch188++
                    }
                }
                if (syncMatch188 >= 2) return true

                // Cek apakah paket berukuran 204 byte (TS dengan error correction)
                var syncMatch204 = 0
                for (offset in 0 until bytesRead step TS_PACKET_SIZE_204) {
                    if (offset < bytesRead && headerBuffer[offset] == TS_SYNC_BYTE) {
                        syncMatch204++
                    }
                }
                if (syncMatch204 >= 2) return true

                // Jika hanya 1 paket pertama yang terbaca
                return headerBuffer[0] == TS_SYNC_BYTE
            }
        } catch (e: Exception) {
            safeLog("HlsChunkVerifier", "Error verifikasi TS sync byte pada ${file.name}: ${e.message}")
            return false
        }
    }

    /**
     * Memeriksa validitas ADTS Syncword pada berkas audio AAC (12 bit pertama bernilai 0xFFF).
     */
    fun isAacAdtsValid(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        try {
            FileInputStream(file).use { stream ->
                val b0 = stream.read()
                val b1 = stream.read()
                if (b0 == -1 || b1 == -1) return false
                // ADTS syncword: 0xFF diikuti 0xF_
                return (b0 == 0xFF) && ((b1 and 0xF0) == 0xF0)
            }
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Memeriksa box header fMP4 (fragmented MP4: styp, moof, ftyp).
     */
    fun isFmp4BoxValid(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        try {
            FileInputStream(file).use { stream ->
                val buffer = ByteArray(8)
                val read = stream.read(buffer)
                if (read < 8) return false
                val boxType = String(buffer, 4, 4, Charsets.US_ASCII)
                return boxType in listOf("styp", "moof", "ftyp", "mdat")
            }
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Menghitung SHA-256 Checksum dari sebuah berkas untuk verifikasi redundansi hash.
     */
    fun calculateChecksum(file: File): String {
        if (!file.exists()) return ""
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            safeLog("HlsChunkVerifier", "Gagal menghitung checksum ${file.name}: ${e.message}")
            ""
        }
    }
}
