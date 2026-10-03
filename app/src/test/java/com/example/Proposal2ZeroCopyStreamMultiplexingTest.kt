package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.download.muxing.CustomNioFileAppender
import com.example.download.muxing.ExoPlayerDownloadHelperFactory
import com.example.download.muxing.HlsStreamMultiplexerEngine
import com.example.download.muxing.MoovAtomHeaderValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal2ZeroCopyStreamMultiplexingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testCustomNioFileAppenderSequentialAndRandomAccess() {
        val testFile = File(tempFolder.root, "test_nio_append.bin")
        val appender = CustomNioFileAppender(testFile)

        // 1. Append byte array
        val chunk1 = "HEADER_CHUNK_1_".toByteArray()
        val written1 = appender.appendBytes(chunk1)
        assertEquals(chunk1.size.toLong(), written1)

        // 2. Append ByteBuffer
        val chunk2 = ByteBuffer.wrap("PAYLOAD_CHUNK_2_".toByteArray())
        val written2 = appender.appendBuffer(chunk2)
        assertEquals(16L, written2)

        // 3. Write in-place at position 0 (Header patch)
        val patch = "PATCHED".toByteArray()
        appender.writeAtPosition(0, patch)

        appender.flush(true)
        appender.close()

        // Verify content on disk
        assertTrue(testFile.exists())
        val finalBytes = testFile.readBytes()
        val finalString = String(finalBytes)

        assertTrue(finalString.startsWith("PATCHED"))
        assertTrue(finalString.contains("PAYLOAD_CHUNK_2_"))
        assertEquals(chunk1.size + 16, finalBytes.size)
    }

    @Test
    fun testMoovAtomHeaderValidatorWithSyntheticMp4() {
        val mp4File = File(tempFolder.root, "valid_video.mp4")

        // Construct synthetic ISO MP4 container: [ftyp box] + [moov box with mvhd] + [mdat box]
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.BIG_ENDIAN)

        // 1. ftyp box (size: 24, type: "ftyp", major_brand: "mp42", minor_version: 0, compatible: "isom", "mp42")
        buffer.putInt(24) // size
        buffer.put("ftyp".toByteArray(Charsets.US_ASCII))
        buffer.put("mp42".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0)
        buffer.put("isom".toByteArray(Charsets.US_ASCII))
        buffer.put("mp42".toByteArray(Charsets.US_ASCII))

        // 2. moov box containing mvhd
        val moovStart = buffer.position()
        buffer.putInt(64) // moov size placeholder
        buffer.put("moov".toByteArray(Charsets.US_ASCII))

        // mvhd box inside moov (size: 56, type: "mvhd", version 0, times, timescale: 1000, duration: 30000 -> 30s)
        buffer.putInt(56)
        buffer.put("mvhd".toByteArray(Charsets.US_ASCII))
        buffer.put(0) // version 0
        buffer.put(0); buffer.put(0); buffer.put(0) // flags
        buffer.putInt(0) // creation time
        buffer.putInt(0) // modification time
        buffer.putInt(1000) // timescale = 1000 Hz
        buffer.putInt(30000) // duration = 30000 (30 seconds)
        buffer.put(ByteArray(28)) // matrix and pre-defined zeros

        // 3. mdat box (size: 64, type: "mdat", video/audio payload)
        buffer.putInt(64)
        buffer.put("mdat".toByteArray(Charsets.US_ASCII))
        buffer.put(ByteArray(56) { 0xAA.toByte() })

        FileOutputStream(mp4File).use { fos ->
            fos.write(buffer.array(), 0, buffer.position())
        }

        val validation = MoovAtomHeaderValidator.validateFileIntegrity(mp4File)

        assertTrue(validation.isValid)
        assertEquals(MoovAtomHeaderValidator.ContainerType.MP4, validation.containerType)
        assertTrue(validation.hasMoovAtom)
        assertTrue(validation.hasMdatAtom)
        assertTrue(validation.isFastStart)
        assertEquals(30000L, validation.durationMs)
        assertTrue(validation.isSeekable)
    }

    @Test
    fun testMoovAtomHeaderValidatorWithSyntheticMpegTs() {
        val tsFile = File(tempFolder.root, "stream.ts")

        // Construct synthetic MPEG-2 TS packets (188 bytes each with 0x47 sync byte at offset 0)
        val packetSize = 188
        val packetCount = 10
        val data = ByteArray(packetSize * packetCount)

        for (i in 0 until packetCount) {
            data[i * packetSize] = 0x47.toByte()
            for (j in 1 until packetSize) {
                data[i * packetSize + j] = (j % 255).toByte()
            }
        }

        FileOutputStream(tsFile).use { it.write(data) }

        val validation = MoovAtomHeaderValidator.validateFileIntegrity(tsFile)

        assertTrue(validation.isValid)
        assertEquals(MoovAtomHeaderValidator.ContainerType.MPEG_TS, validation.containerType)
        assertTrue(validation.isSeekable)
        assertFalse(validation.hasMoovAtom) // TS uses sync packets rather than moov atom
    }

    @Test
    fun testMoovAtomHeaderValidatorCorruptOrEmptyFile() {
        val emptyFile = File(tempFolder.root, "empty.mp4")
        emptyFile.createNewFile()

        val validation = MoovAtomHeaderValidator.validateFileIntegrity(emptyFile)
        assertFalse(validation.isValid)
        assertEquals(MoovAtomHeaderValidator.ContainerType.UNKNOWN, validation.containerType)
    }

    @Test
    fun testHlsStreamMultiplexerEngineStreamingAndFinalization() = runBlocking {
        val outputFile = File(tempFolder.root, "stream_output.ts")
        val engine = HlsStreamMultiplexerEngine(outputFile, expectedTotalSegments = 3)

        // 3 synthetic TS chunks streamed directly into engine
        for (segIndex in 0 until 3) {
            val chunkData = ByteArray(188 * 5) { 0x00 }
            for (p in 0 until 5) {
                chunkData[p * 188] = 0x47.toByte()
            }

            val inputStream = ByteArrayInputStream(chunkData)
            val bytesWritten = engine.appendChunkStream(
                inputStream = inputStream,
                expectedLength = chunkData.size.toLong(),
                ptsOffsetUs = segIndex * 2_000_000L
            )
            assertEquals(chunkData.size.toLong(), bytesWritten)
        }

        assertEquals(3, engine.getSegmentsAppendedCount())
        assertEquals((188 * 5 * 3).toLong(), engine.getTotalBytesMuxed())

        val validation = engine.finalizeMuxing()

        assertTrue(validation.isValid)
        assertEquals(MoovAtomHeaderValidator.ContainerType.MPEG_TS, validation.containerType)
        assertTrue(outputFile.exists())
        assertEquals((188 * 5 * 3).toLong(), outputFile.length())
    }

    @Test
    fun testHlsStreamMultiplexerEngineAbort() {
        val outputFile = File(tempFolder.root, "aborted_stream.mp4")
        val engine = HlsStreamMultiplexerEngine(outputFile, 5)

        runBlocking {
            engine.appendChunkBytes(ByteArray(100) { 0x11 })
        }
        assertTrue(outputFile.exists())

        engine.abort()
        assertFalse(outputFile.exists())
    }

    @Test
    fun testExoPlayerDownloadHelperFactoryConfiguration() {
        val hlsUri = Uri.parse("https://stream.example.com/anime/master.m3u8")
        val customHeaders = mapOf("Authorization" to "Bearer test-token-123")

        val helper = ExoPlayerDownloadHelperFactory.createDownloadHelper(
            context = context,
            mediaUri = hlsUri,
            headers = customHeaders
        )

        assertNotNull(helper)

        val downloadRequest = ExoPlayerDownloadHelperFactory.createDownloadRequest(
            contentId = "anime_ep_10",
            mediaUri = hlsUri
        )

        assertNotNull(downloadRequest)
        assertEquals("anime_ep_10", downloadRequest.id)
        assertEquals(hlsUri, downloadRequest.uri)
    }
}
