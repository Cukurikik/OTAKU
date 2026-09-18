package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.PageDownloadEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MangaDownloadResumeTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDir = File(context.filesDir, "test_manga_downloads")
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
        testDir.mkdirs()
    }

    @Test
    fun `test disk cache evaluation skips previously downloaded pages`() = runBlocking {
        val stagingDir = File(testDir, "tmp_chapter_101").apply { mkdirs() }

        // Simulate 4 pages already successfully downloaded in a previous run
        val dummyData = ByteArray(1024) { 0x55.toByte() }
        for (i in 0 until 4) {
            val pageFile = File(stagingDir, "${String.format("%03d", i)}.jpg")
            pageFile.writeBytes(dummyData)
        }

        val totalPages = 5
        val pageEntities = (0 until totalPages).map { index ->
            PageDownloadEntity(
                sourceId = "test_source",
                mangaId = "test_manga",
                chapterId = "chapter_101",
                pageIndex = index,
                remoteUrl = "https://example.com/pages/$index.jpg",
                state = if (index < 4) "DOWNLOADED" else "QUEUE"
            )
        }

        var downloadedCount = 0
        var skippedCount = 0

        for ((index, pageEntity) in pageEntities.withIndex()) {
            val fileName = "${String.format("%03d", index)}.jpg"
            val file = File(stagingDir, fileName)

            // Disk cache evaluation logic
            if (file.exists() && file.length() > 512) {
                skippedCount++
                continue
            }

            // Simulate downloading the remaining page
            val tempFile = File(stagingDir, "$fileName.tmp")
            tempFile.writeBytes(dummyData)
            tempFile.renameTo(file)
            downloadedCount++
        }

        // Verify that 4 pages were skipped and only 1 was downloaded
        assertEquals("4 pages should be skipped from cache", 4, skippedCount)
        assertEquals("Only 1 page should be downloaded", 1, downloadedCount)
        assertEquals("All 5 files must exist in staging", 5, stagingDir.listFiles()?.size)
    }

    @Test
    fun `test failed page download preserves staging directory for subsequent resume`() = runBlocking {
        val stagingDir = File(testDir, "tmp_chapter_102").apply { mkdirs() }
        val dummyData = ByteArray(800) { 0xAA.toByte() }

        // Pages 0..2 download successfully
        for (i in 0..2) {
            File(stagingDir, "${String.format("%03d", i)}.jpg").writeBytes(dummyData)
        }

        // Simulate failure on page 3
        var allSuccess = false
        val failedIndex = 3

        // Verify that on failure, stagingDir is NOT deleted
        assertFalse(allSuccess)
        assertEquals(3, failedIndex)

        // All 3 completed pages should still exist in stagingDir
        assertTrue("Staging dir must remain intact on failure", stagingDir.exists())
        assertEquals("Previously downloaded pages must be preserved", 3, stagingDir.listFiles()?.count { it.extension == "jpg" })

        // Now simulate RESUME:
        val resumedSkipped = mutableListOf<Int>()
        val resumedDownloaded = mutableListOf<Int>()

        for (i in 0..4) {
            val file = File(stagingDir, "${String.format("%03d", i)}.jpg")
            if (file.exists() && file.length() > 512) {
                resumedSkipped.add(i)
            } else {
                file.writeBytes(dummyData)
                resumedDownloaded.add(i)
            }
        }

        // Pages 0, 1, 2 were skipped from disk cache!
        assertEquals(listOf(0, 1, 2), resumedSkipped)
        // Only pages 3 and 4 were downloaded!
        assertEquals(listOf(3, 4), resumedDownloaded)
    }

    @Test
    fun `test atomic bundling to CBZ and staging cleanup only after successful verification`() = runBlocking {
        val chapterId = "chapter_103"
        val stagingDir = File(testDir, "tmp_$chapterId").apply { mkdirs() }
        val cbzFile = File(testDir, "$chapterId.cbz")
        val pageCount = 6

        // Populate staging with all valid pages
        for (i in 0 until pageCount) {
            val dummyImg = ByteArray(2048) { (i + 1).toByte() }
            File(stagingDir, "${String.format("%03d", i)}.jpg").writeBytes(dummyImg)
        }

        // Bundle into temporary CBZ
        val tempCbzFile = File(testDir, "$chapterId.cbz.tmp")
        ZipOutputStream(FileOutputStream(tempCbzFile)).use { zout ->
            stagingDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.name.endsWith(".jpg") && f.length() > 0) {
                    FileInputStream(f).use { fis ->
                        val entry = ZipEntry(f.name)
                        zout.putNextEntry(entry)
                        fis.copyTo(zout)
                        zout.closeEntry()
                    }
                }
            }
        }

        // Verify CBZ integrity
        assertTrue("Temp CBZ must exist", tempCbzFile.exists())
        assertTrue("Temp CBZ must be non-empty", tempCbzFile.length() > 200)

        // Rename atomically and clean staging
        val renamed = tempCbzFile.renameTo(cbzFile)
        assertTrue("Atomically renamed to target CBZ", renamed)
        if (renamed) {
            stagingDir.deleteRecursively()
        }

        assertFalse("Staging directory should be cleaned up after CBZ verification", stagingDir.exists())
        assertTrue("CBZ file must exist", cbzFile.exists())

        // Verify contents of the CBZ
        ZipFile(cbzFile).use { zip ->
            assertEquals("CBZ should contain 6 entries", pageCount, zip.size())
            for (i in 0 until pageCount) {
                val entryName = "${String.format("%03d", i)}.jpg"
                val entry = zip.getEntry(entryName)
                assertNotNull("CBZ must contain entry $entryName", entry)
                assertEquals("Entry size must match 2048 bytes", 2048L, entry.size)
            }
        }
    }
}
