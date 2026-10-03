package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil.request.Options
import com.example.data.sources.local.CoilEncryptedStreamFetcher
import com.example.storage.AppStorageDirectoryConfig
import com.example.storage.secure.EncryptedBitmapPool
import com.example.storage.secure.SecureEncryptedMediaStorage
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
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal4SecureEncryptedStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var secureStorage: SecureEncryptedMediaStorage
    private lateinit var bitmapPool: EncryptedBitmapPool

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        secureStorage = SecureEncryptedMediaStorage(context)
        bitmapPool = EncryptedBitmapPool(context)
    }

    @Test
    fun testAppStorageDirectoryConfigSandboxingAndNoMedia() {
        val baseDir = AppStorageDirectoryConfig.getSandboxedBaseDir(context)
        assertTrue(baseDir.exists())
        assertTrue(File(baseDir, ".nomedia").exists())

        val vaultDir = AppStorageDirectoryConfig.getSecureVaultDir(context)
        assertTrue(vaultDir.exists())
        assertTrue(File(vaultDir, ".nomedia").exists())

        val mangaDir = AppStorageDirectoryConfig.getSecureMangaDir(context, "komikcast", "solo-leveling")
        assertTrue(mangaDir.exists())
        assertTrue(File(mangaDir, ".nomedia").exists())
    }

    @Test
    fun testSecureEncryptedMediaStorageSaveAndDecrypt() {
        val originalText = "RAW_MANGA_PANEL_PAYLOAD_BINARY_DATA_1234567890_!@#$"
        val rawBytes = originalText.toByteArray(Charsets.UTF_8)

        // 1. Simpan ke berkas terenkripsi
        val encryptedFile = secureStorage.saveEncryptedMedia("page_001", rawBytes, subDir = "vault")
        assertTrue(encryptedFile.exists())
        assertTrue(secureStorage.isEncryptedMedia(encryptedFile))

        // 2. Verifikasi Magic Header 16-byte di disk
        val fileOnDiskBytes = encryptedFile.readBytes()
        assertEquals(rawBytes.size + SecureEncryptedMediaStorage.HEADER_SIZE_BYTES, fileOnDiskBytes.size)

        val headerOnDisk = fileOnDiskBytes.copyOfRange(0, SecureEncryptedMediaStorage.HEADER_SIZE_BYTES)
        assertArrayEquals(SecureEncryptedMediaStorage.ENCRYPTED_HEADER_SIGNATURE, headerOnDisk)

        // 3. Verifikasi bahwa payload di disk ter-scramble (tidak sama dengan rawBytes polos)
        val payloadOnDisk = fileOnDiskBytes.copyOfRange(SecureEncryptedMediaStorage.HEADER_SIZE_BYTES, fileOnDiskBytes.size)
        assertFalse(payloadOnDisk.contentEquals(rawBytes))

        // 4. Dekripsi via getDecryptedBytes
        val decryptedBytes = secureStorage.getDecryptedBytes(encryptedFile)
        assertArrayEquals(rawBytes, decryptedBytes)
        assertEquals(originalText, String(decryptedBytes, Charsets.UTF_8))

        // 5. Dekripsi via getDecryptedInputStream
        val decryptedStream = secureStorage.getDecryptedInputStream(encryptedFile)
        val streamBytes = decryptedStream.readBytes()
        assertArrayEquals(rawBytes, streamBytes)
    }

    @Test
    fun testSaveEncryptedStreamStreaming() {
        val largeData = ByteArray(64 * 1024) { (it % 256).toByte() }
        val inputStream = ByteArrayInputStream(largeData)

        val encryptedFile = secureStorage.saveEncryptedStream("large_image", inputStream, subDir = "vault")
        assertTrue(encryptedFile.exists())
        assertTrue(secureStorage.isEncryptedMedia(encryptedFile))
        assertEquals((largeData.size + SecureEncryptedMediaStorage.HEADER_SIZE_BYTES).toLong(), encryptedFile.length())

        val decrypted = secureStorage.getDecryptedBytes(encryptedFile)
        assertArrayEquals(largeData, decrypted)
    }

    @Test
    fun testEncryptedArchiveCreationAndEntryExtraction() {
        val archiveFile = File(tempFolder.root, "chapter_10.encpack")
        val page1 = "PAGE_1_IMAGE_DATA".toByteArray()
        val page2 = "PAGE_2_IMAGE_DATA_MORE_PIXELS".toByteArray()
        val page3 = "PAGE_3_FINAL_PANEL".toByteArray()

        val entries = mapOf(
            "page_001.dat" to page1,
            "page_002.dat" to page2,
            "page_003.dat" to page3
        )

        secureStorage.createEncryptedArchive(archiveFile, entries)
        assertTrue(archiveFile.exists())
        assertTrue(secureStorage.isEncryptedMedia(archiveFile))

        // List entri dalam archive tanpa ekstrak ke disk
        val entryList = secureStorage.listEncryptedArchiveEntries(archiveFile)
        assertEquals(3, entryList.size)
        assertTrue(entryList.contains("page_001.dat"))
        assertTrue(entryList.contains("page_002.dat"))
        assertTrue(entryList.contains("page_003.dat"))

        // Ekstrak entri tertentu langsung ke memori
        val readPage2 = secureStorage.readEncryptedArchiveEntry(archiveFile, "page_002.dat")
        assertNotNull(readPage2)
        assertArrayEquals(page2, readPage2)

        val readPage3 = secureStorage.readEncryptedArchiveEntry(archiveFile, "page_003.dat")
        assertNotNull(readPage3)
        assertArrayEquals(page3, readPage3)
    }

    @Test
    fun testCoilEncryptedStreamFetcherWithArchiveAndSingleFile() = runBlocking {
        // 1. Uji skema enc://
        val rawImage = createSyntheticPngBytes(width = 10, height = 10)
        val encFile = secureStorage.saveEncryptedMedia("synthetic_cover", rawImage, subDir = "vault")

        val options = Options(context = context)
        val fetcherEnc = CoilEncryptedStreamFetcher("enc://${encFile.absolutePath}", options)
        val resultEnc = fetcherEnc.fetch()
        assertNotNull(resultEnc)

        // 2. Uji skema encpack://
        val archiveFile = File(tempFolder.root, "comic_ch.encpack")
        secureStorage.createEncryptedArchive(archiveFile, mapOf("page_01.png" to rawImage))

        val fetcherArchive = CoilEncryptedStreamFetcher("encpack://${archiveFile.absolutePath}::page_01.png", options)
        val resultArchive = fetcherArchive.fetch()
        assertNotNull(resultArchive)

        // 3. Uji Factory registration
        val factory = CoilEncryptedStreamFetcher.Factory()
        val createdFetcher = factory.create("encpack://${archiveFile.absolutePath}::page_01.png", options, coil.ImageLoader(context))
        assertNotNull(createdFetcher)
    }

    @Test
    fun testEncryptedBitmapPoolInSampleSizeAndCache() {
        val rawPng = createSyntheticPngBytes(width = 64, height = 64)
        val encFile = secureStorage.saveEncryptedMedia("test_bitmap", rawPng, subDir = "vault")

        val bitmap = bitmapPool.decodeDecryptedBitmap(encFile, maxWidth = 32, maxHeight = 32)
        assertNotNull(bitmap)

        // Pastikan decoding kedua diambil dari cache memori
        val cachedBitmap = bitmapPool.decodeDecryptedBitmap(encFile, maxWidth = 32, maxHeight = 32)
        assertSame(bitmap, cachedBitmap)
    }

    private fun createSyntheticPngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }
}
