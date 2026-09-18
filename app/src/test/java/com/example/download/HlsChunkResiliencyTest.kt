package com.example.download

import com.example.download.resiliency.HlsChunkVerifier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class HlsChunkResiliencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var validTsFile: File
    private lateinit var corruptedTsFile: File
    private lateinit var validAacFile: File
    private lateinit var emptyFile: File

    @Before
    fun setUp() {
        // Buat file MPEG-TS sintetis yang valid (terdiri dari beberapa paket 188-byte dengan sync byte 0x47 di offset 0, 188, 376...)
        validTsFile = tempFolder.newFile("valid_segment.ts")
        FileOutputStream(validTsFile).use { fos ->
            val packet = ByteArray(188)
            packet[0] = 0x47.toByte() // TS Sync Byte
            packet[1] = 0x40.toByte() // Payload unit start
            packet[2] = 0x11.toByte()
            packet[3] = 0x10.toByte()
            // Tulis 5 paket TS
            repeat(5) {
                fos.write(packet)
            }
        }

        // Buat file TS korup (tidak diawali 0x47)
        corruptedTsFile = tempFolder.newFile("corrupted_segment.ts")
        FileOutputStream(corruptedTsFile).use { fos ->
            val junk = ByteArray(500) { 0x00.toByte() }
            fos.write(junk)
        }

        // Buat file audio AAC yang valid (diawali ADTS syncword 0xFFF1)
        validAacFile = tempFolder.newFile("valid_audio.aac")
        FileOutputStream(validAacFile).use { fos ->
            val aacData = ByteArray(256)
            aacData[0] = 0xFF.toByte()
            aacData[1] = 0xF1.toByte() // ADTS syncword (0xFFF...)
            fos.write(aacData)
        }

        emptyFile = tempFolder.newFile("empty_segment.ts")
    }

    @Test
    fun testValidMpegTsIntegrityVerification() {
        val isValid = HlsChunkVerifier.verifySegmentIntegrity(validTsFile)
        assertTrue("MPEG-TS valid harus lulus verifikasi integritas", isValid)

        val isSyncValid = HlsChunkVerifier.isMpegTsSyncValid(validTsFile)
        assertTrue("Sync byte MPEG-TS 0x47 harus valid", isSyncValid)
    }

    @Test
    fun testCorruptedMpegTsRejection() {
        val isValid = HlsChunkVerifier.verifySegmentIntegrity(corruptedTsFile)
        assertFalse("File TS tanpa sync byte 0x47 harus ditolak", isValid)

        val isSyncValid = HlsChunkVerifier.isMpegTsSyncValid(corruptedTsFile)
        assertFalse("Sync check harus menghasilkan false untuk data acak", isSyncValid)
    }

    @Test
    fun testEmptyFileRejection() {
        val isValid = HlsChunkVerifier.verifySegmentIntegrity(emptyFile)
        assertFalse("File kosong 0-byte harus ditolak", isValid)
    }

    @Test
    fun testExpectedByteSizeVerification() {
        val actualSize = validTsFile.length()
        assertTrue(
            "Verifikasi harus sukses jika expected size sesuai",
            HlsChunkVerifier.verifySegmentIntegrity(validTsFile, expectedByteSize = actualSize)
        )

        assertFalse(
            "Verifikasi harus gagal jika ukuran byte tidak sesuai ekspektasi",
            HlsChunkVerifier.verifySegmentIntegrity(validTsFile, expectedByteSize = actualSize + 100)
        )
    }

    @Test
    fun testValidAacAdtsVerification() {
        val isAacValid = HlsChunkVerifier.isAacAdtsValid(validAacFile)
        assertTrue("Berkas AAC ADTS dengan syncword 0xFFF harus valid", isAacValid)

        val isIntegrityValid = HlsChunkVerifier.verifySegmentIntegrity(validAacFile)
        assertTrue("Verifikasi integritas segmen AAC harus lulus", isIntegrityValid)
    }

    @Test
    fun testChecksumCalculation() {
        val checksum1 = HlsChunkVerifier.calculateChecksum(validTsFile)
        assertNotNull(checksum1)
        assertEquals("SHA-256 hex string harus memiliki panjang 64 karakter", 64, checksum1.length)

        val checksum2 = HlsChunkVerifier.calculateChecksum(validTsFile)
        assertEquals("Checksum untuk file yang sama harus konsisten dan identik", checksum1, checksum2)
    }
}
