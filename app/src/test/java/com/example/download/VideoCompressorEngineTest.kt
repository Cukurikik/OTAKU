package com.example.download

import android.content.Context
import com.example.download.compressor.CompressionProfile
import com.example.download.compressor.StorageSpaceSaverManager
import com.example.download.compressor.VideoCompressorEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VideoCompressorEngineTest {

    private lateinit var context: Context
    private lateinit var engine: VideoCompressorEngine

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        engine = VideoCompressorEngine(context)
    }

    @Test
    fun testBuildFFmpegCommandSpaceSaver() {
        val input = "/storage/emulated/0/Movies/OtakuHub/anime-ep1.mp4"
        val output = "/storage/emulated/0/Movies/OtakuHub/anime-ep1_compressed.mp4"

        val command = engine.buildFFmpegCommand(input, output, CompressionProfile.SPACE_SAVER)

        // Sesuai requirement: libx265, CRF 28, libopus 96k
        assertTrue("Command must contain input path", command.contains("-i \"$input\""))
        assertTrue("Command must contain output path", command.contains("\"$output\""))
        assertTrue("Command must specify libx265", command.contains("-c:v libx265"))
        assertTrue("Command must specify CRF 28", command.contains("-crf 28"))
        assertTrue("Command must specify libopus audio", command.contains("-c:a libopus"))
        assertTrue("Command must specify 96k audio bitrate", command.contains("-b:a 96k"))
    }

    @Test
    fun testBuildFFmpegCommandOriginalAndUltraCompact() {
        val input = "/path/in.mp4"
        val output = "/path/out.mp4"

        val origCommand = engine.buildFFmpegCommand(input, output, CompressionProfile.ORIGINAL)
        assertTrue(origCommand.contains("-c copy"))

        val ultraCommand = engine.buildFFmpegCommand(input, output, CompressionProfile.ULTRA_COMPACT)
        assertTrue(ultraCommand.contains("-c:v libx265"))
        assertTrue(ultraCommand.contains("-crf 32"))
        assertTrue(ultraCommand.contains("-b:a 64k"))
    }

    @Test
    fun testStorageSavedCalculationFormulation() {
        // Formulasi: Storage Saved (%) = (1 - Size_HEVC / Size_H.264) * 100%
        // 1GB (1000MB) -> 300MB = 70% penghematan
        val savedPercent70 = VideoCompressorEngine.calculateStorageSavedPercent(1000L, 300L)
        assertEquals(70.0, savedPercent70, 0.01)

        // 1GB (1000MB) -> 200MB = 80% penghematan
        val savedPercent80 = VideoCompressorEngine.calculateStorageSavedPercent(1000L, 200L)
        assertEquals(80.0, savedPercent80, 0.01)

        // Asli (1000MB -> 1000MB = 0% penghematan)
        val savedPercent0 = VideoCompressorEngine.calculateStorageSavedPercent(1000L, 1000L)
        assertEquals(0.0, savedPercent0, 0.01)

        // Edge case: originalBytes = 0
        val edgeCase = VideoCompressorEngine.calculateStorageSavedPercent(0L, 0L)
        assertEquals(0.0, edgeCase, 0.01)
    }

    @Test
    fun testCompressionProfileEstimations() {
        val original1GB = 1_000_000_000L

        val savedSpaceSaver = CompressionProfile.SPACE_SAVER.calculateEstimatedSavedBytes(original1GB)
        assertEquals(700_000_000L, savedSpaceSaver)

        val savedUltra = CompressionProfile.ULTRA_COMPACT.calculateEstimatedSavedBytes(original1GB)
        assertEquals(800_000_000L, savedUltra)

        val savedOriginal = CompressionProfile.ORIGINAL.calculateEstimatedSavedBytes(original1GB)
        assertEquals(0L, savedOriginal)
    }

    @Test
    fun testFileIntegrityVerification() {
        val validFile = File(context.cacheDir, "valid_video.mp4").apply {
            writeBytes(ByteArray(2048) { 1 })
        }
        val emptyFile = File(context.cacheDir, "empty_video.mp4").apply {
            writeBytes(ByteArray(0))
        }

        assertTrue(engine.verifyIntegrity(validFile, 1024L))
        assertFalse(engine.verifyIntegrity(emptyFile, 1024L))
    }

    @Test
    fun testReplaceOriginalWithCompressed() {
        val originalFile = File(context.cacheDir, "original_ep.mp4").apply {
            writeBytes(ByteArray(4096) { 0x01 })
        }
        val compressedFile = File(context.cacheDir, "compressed_temp.mp4").apply {
            writeBytes(ByteArray(2048) { 0x02 })
        }

        val success = engine.replaceOriginalWithCompressed(originalFile, compressedFile)
        assertTrue("Replace must succeed", success)
        assertTrue("Original file must still exist with new compressed content", originalFile.exists())
        assertEquals(2048L, originalFile.length())
        assertFalse("Compressed temp file should have been renamed/moved", compressedFile.exists())
    }

    @Test
    fun testStorageSpaceSaverManagerRecordAndRetrieve() = runBlocking {
        val downloadId = "anime_ep_01"
        val filePath = "/storage/emulated/0/Movies/OtakuHub/anime_ep_01.mp4"

        StorageSpaceSaverManager.recordCompression(
            context = context,
            downloadId = downloadId,
            filePath = filePath,
            profile = CompressionProfile.SPACE_SAVER,
            originalBytes = 1_000_000_000L,
            compressedBytes = 300_000_000L
        )

        val info = StorageSpaceSaverManager.getCompressionInfo(context, "anime_ep_01.mp4")
        assertNotNull("Compression info must be found", info)
        assertEquals(downloadId, info!!.optString("downloadId"))
        assertEquals(CompressionProfile.SPACE_SAVER.name, info.optString("profile"))
        assertEquals(1_000_000_000L, info.optLong("originalBytes"))
        assertEquals(300_000_000L, info.optLong("compressedBytes"))
        assertEquals(700_000_000L, info.optLong("savedBytes"))
    }
}
