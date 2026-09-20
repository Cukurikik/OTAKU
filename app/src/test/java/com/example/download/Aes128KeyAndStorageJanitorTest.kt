package com.example.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.DownloadHeaderRegistry
import com.example.storage.StoragePathManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Aes128KeyAndStorageJanitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DownloadHeaderRegistry.clear()
    }

    @Test
    fun testStorageJanitorPurgesStagingDirectoriesOlderThan24Hours() {
        val cacheDir = context.cacheDir
        val dayInMs = 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        // 1. Create a stale staging directory (>24 hours old)
        val oldStagingDir = File(cacheDir, "tmp_hls_old_12345")
        oldStagingDir.mkdirs()
        val oldChunk = File(oldStagingDir, "chunk_00001.ts")
        FileOutputStream(oldChunk).use { it.write("old_ts_data".toByteArray()) }
        oldStagingDir.setLastModified(currentTime - (dayInMs + 3600000L)) // 25 hours old

        // 2. Create an active staging directory (<24 hours old)
        val activeStagingDir = File(cacheDir, "tmp_hls_active_67890")
        activeStagingDir.mkdirs()
        val activeChunk = File(activeStagingDir, "chunk_00001.ts")
        FileOutputStream(activeChunk).use { it.write("active_ts_data".toByteArray()) }
        activeStagingDir.setLastModified(currentTime - 10000L) // 10 seconds old

        // 3. Create a stale .tmp file in downloads
        val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
        downloadsDir.mkdirs()
        val staleTmpFile = File(downloadsDir, "drama_ep01.tmp")
        FileOutputStream(staleTmpFile).use { it.write("stale_tmp".toByteArray()) }
        staleTmpFile.setLastModified(currentTime - (dayInMs + 5000L))

        // 4. Run Storage Janitor Task
        val purgedCount = StoragePathManager.runStorageJanitorTask(context, dayInMs)

        // 5. Assert that old staging folder and stale tmp file are purged, active directory is retained
        assertTrue("Storage Janitor should have purged at least 2 stale entities", purgedCount >= 2)
        assertFalse("Old staging folder should be deleted", oldStagingDir.exists())
        assertFalse("Stale .tmp file should be deleted", staleTmpFile.exists())
        assertTrue("Active staging folder should be retained", activeStagingDir.exists())
        assertTrue("Active chunk should be retained", activeChunk.exists())

        // Cleanup
        activeStagingDir.deleteRecursively()
    }

    @Test
    fun testAes128KeyRegistryAndLocalMapping() {
        val keyUrl = "https://stream.jucelebrity.com/hls/aes128.key"
        val mockKeyBytes = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
        )

        // 1. Register AES-128 key bytes in memory
        DownloadHeaderRegistry.registerAes128Key(keyUrl, mockKeyBytes)
        val retrievedKey = DownloadHeaderRegistry.getAes128Key(keyUrl)

        assertNotNull("Retrieved AES-128 key must not be null", retrievedKey)
        assertArrayEquals("Key bytes must match registered data", mockKeyBytes, retrievedKey)

        // 2. Register local file mapping
        val localFilePath = "/data/user/0/com.example/files/staging/key_12345.key"
        DownloadHeaderRegistry.registerLocalKeyMapping(keyUrl, localFilePath)
        val retrievedMapping = DownloadHeaderRegistry.getLocalKeyMapping(keyUrl)

        assertEquals("Local key mapping path must match", localFilePath, retrievedMapping)

        // 3. Clear and verify
        DownloadHeaderRegistry.clear()
        assertNull("Key must be null after clear", DownloadHeaderRegistry.getAes128Key(keyUrl))
        assertNull("Mapping must be null after clear", DownloadHeaderRegistry.getLocalKeyMapping(keyUrl))
    }

    @Test
    fun testHlsExtXKeyRegexParsing() {
        val samplePlaylistLine = "#EXT-X-KEY:METHOD=AES-128,URI=\"https://example.com/hls/key.php?id=9988\",IV=0x1234567890ABCDEF"
        val keyRegex = Regex("""URI=["']([^"']+)["']""")
        val match = keyRegex.find(samplePlaylistLine)

        assertNotNull("Regex must match URI attribute in #EXT-X-KEY", match)
        val extractedUri = match?.groupValues?.get(1)
        assertEquals("Extracted key URI must match playlist", "https://example.com/hls/key.php?id=9988", extractedUri)
    }
}
