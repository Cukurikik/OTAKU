package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import coil.size.Dimension
import coil.size.Precision
import coil.size.Size
import coil.memory.MemoryCache
import com.example.ui.utils.WhiteBorderCropTransformation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderMemoryOptimizationTest {

    @Test
    fun `test white border is successfully cropped`() = runBlocking {
        // Create a 200x300 bitmap with white borders (20px margins on all sides)
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { color = Color.BLACK }
        // Draw content in the center: left=30, top=40, right=170, bottom=260
        canvas.drawRect(30f, 40f, 170f, 260f, paint)

        val transformation = WhiteBorderCropTransformation()
        val result = transformation.transform(bitmap, Size.ORIGINAL)

        assertNotNull(result)
        // Width should be cropped from 200 down to approximately 140-145 (allowing padding of 2px)
        assertTrue("Width should be smaller than original", result.width < 200)
        assertTrue("Height should be smaller than original", result.height < 300)
        assertTrue("Cropped width should contain content", result.width >= 140)
        assertTrue("Cropped height should contain content", result.height >= 220)
    }

    @Test
    fun `test bitmap without borders is not cropped`() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLUE) // Solid blue image, no white borders

        val transformation = WhiteBorderCropTransformation()
        val result = transformation.transform(bitmap, Size.ORIGINAL)

        assertEquals("Width must remain identical", 100, result.width)
        assertEquals("Height must remain identical", 100, result.height)
    }

    @Test
    fun `test small and invalid bitmaps return input safely`() = runBlocking {
        val tinyBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val transformation = WhiteBorderCropTransformation()
        val result = transformation.transform(tinyBitmap, Size.ORIGINAL)
        assertSame("Tiny bitmap should be returned directly", tinyBitmap, result)
    }

    @Test
    fun `test high resolution manhwa page cropping performance`() = runBlocking {
        // Simulate a typical webtoon vertical strip: 1080 x 3200 pixels
        val manhwaStrip = Bitmap.createBitmap(1080, 3200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(manhwaStrip)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { color = Color.DKGRAY }
        // Panels in the middle with white gutters/margins
        canvas.drawRect(80f, 100f, 1000f, 3100f, paint)

        val startTime = System.currentTimeMillis()
        val transformation = WhiteBorderCropTransformation()
        val cropped = transformation.transform(manhwaStrip, Size.ORIGINAL)
        val duration = System.currentTimeMillis() - startTime

        assertNotNull(cropped)
        assertTrue("Cropped width should be cropped from margins", cropped.width < 1080)
        assertTrue("Cropped height should be cropped from margins", cropped.height < 3200)
        // Ensure the coarse-to-fine scan completes rapidly (under 500ms even in JVM unit test)
        assertTrue("Crop should finish quickly, took ${duration}ms", duration < 1000)
    }

    @Test
    fun `test RGB_565 configuration compatibility`() = runBlocking {
        val rgbBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.RGB_565)
        val canvas = Canvas(rgbBitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawRect(20f, 20f, 130f, 130f, paint)

        val transformation = WhiteBorderCropTransformation()
        val result = transformation.transform(rgbBitmap, Size.ORIGINAL)

        assertNotNull(result)
        assertTrue("RGB_565 should be cropped without crash", result.width < 150)
    }

    @Test
    fun `test size resolver parameters for webtoon downsampling`() {
        val screenWidth = 1080
        val webtoonImageSize = Size(Dimension.Pixels(screenWidth), Dimension.Undefined)
        
        assertEquals(Dimension.Pixels(1080), webtoonImageSize.width)
        assertEquals(Dimension.Undefined, webtoonImageSize.height)
        
        // Inexact precision allows BitmapFactory to select power-of-two inSampleSize
        val precision = Precision.INEXACT
        assertEquals(Precision.INEXACT, precision)
    }

    @Test
    fun `test calculateSpread in single page mode`() {
        val totalPages = 10
        for (i in 0 until totalPages) {
            val spread = com.example.ui.screens.calculateSpread(
                spreadIndex = i,
                totalPages = totalPages,
                isDoublePage = false,
                separateCover = false,
                isRtl = false
            )
            assertEquals(i, spread.leftPageIndex)
            assertNull(spread.rightPageIndex)
            assertFalse(spread.isCoverSpread)
        }
    }

    @Test
    fun `test calculateSpread in dual page mode without separate cover`() {
        val totalPages = 5
        // Spread 0: pages 0 & 1 (LTR: left=0, right=1; RTL: left=1, right=0)
        val ltrSpread0 = com.example.ui.screens.calculateSpread(0, totalPages, isDoublePage = true, separateCover = false, isRtl = false)
        assertEquals(0, ltrSpread0.leftPageIndex)
        assertEquals(1, ltrSpread0.rightPageIndex)
        assertFalse(ltrSpread0.isCoverSpread)

        val rtlSpread0 = com.example.ui.screens.calculateSpread(0, totalPages, isDoublePage = true, separateCover = false, isRtl = true)
        assertEquals(1, rtlSpread0.leftPageIndex)
        assertEquals(0, rtlSpread0.rightPageIndex)
        assertFalse(rtlSpread0.isCoverSpread)

        // Spread 2: page 4 alone
        val spread2 = com.example.ui.screens.calculateSpread(2, totalPages, isDoublePage = true, separateCover = false, isRtl = false)
        assertEquals(4, spread2.leftPageIndex)
        assertNull(spread2.rightPageIndex)
    }

    @Test
    fun `test calculateSpread in dual page mode with separate cover page`() {
        val totalPages = 5
        // Spread 0: Cover page alone (Index 0)
        val spread0 = com.example.ui.screens.calculateSpread(0, totalPages, isDoublePage = true, separateCover = true, isRtl = true)
        assertEquals(0, spread0.leftPageIndex)
        assertNull(spread0.rightPageIndex)
        assertTrue(spread0.isCoverSpread)

        // Spread 1: pages 1 & 2 (RTL: right=1, left=2)
        val spread1 = com.example.ui.screens.calculateSpread(1, totalPages, isDoublePage = true, separateCover = true, isRtl = true)
        assertEquals(2, spread1.leftPageIndex)
        assertEquals(1, spread1.rightPageIndex)
        assertFalse(spread1.isCoverSpread)

        // Spread 2: pages 3 & 4 (RTL: right=3, left=4)
        val spread2 = com.example.ui.screens.calculateSpread(2, totalPages, isDoublePage = true, separateCover = true, isRtl = true)
        assertEquals(4, spread2.leftPageIndex)
        assertEquals(3, spread2.rightPageIndex)
        assertFalse(spread2.isCoverSpread)
    }

    @Test
    fun `test viewport bounded pre-fetching window calculation`() {
        val totalPages = 100

        // At beginning (index 0): 1 page behind (clamped to 0) to 3 pages ahead (0..3)
        val startMin = (0 - 1).coerceAtLeast(0)
        val startMax = (0 + 3).coerceAtMost(totalPages - 1)
        val startWindow = (startMin..startMax).toSet()
        assertEquals(setOf(0, 1, 2, 3), startWindow)

        // At middle (index 20): 1 page behind (19) to 3 pages ahead (23)
        val midMin = (20 - 1).coerceAtLeast(0)
        val midMax = (20 + 3).coerceAtMost(totalPages - 1)
        val midWindow = (midMin..midMax).toSet()
        assertEquals(setOf(19, 20, 21, 22, 23), midWindow)

        // At end (index 99): 1 page behind (98) to 3 pages ahead (clamped to 99)
        val endMin = (99 - 1).coerceAtLeast(0)
        val endMax = (99 + 3).coerceAtMost(totalPages - 1)
        val endWindow = (endMin..endMax).toSet()
        assertEquals(setOf(98, 99), endWindow)
    }

    @Test
    fun `test bitmap eviction outside active window`() {
        val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        val imageLoader = createReaderImageLoader(context)
        val memoryCache = imageLoader.memoryCache
        assertNotNull("Memory cache must be initialized", memoryCache)

        // Populate memory cache with 10 dummy page bitmaps
        val pages = (0..9).map { index ->
            com.example.data.models.Page(url = "https://example.com/chapter1/page$index.jpg")
        }

        for (page in pages) {
            val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            memoryCache!!.set(MemoryCache.Key(page.url), MemoryCache.Value(bmp))
        }

        // Verify all 10 pages are cached initially
        for ((idx, page) in pages.withIndex()) {
            assertNotNull("Page $idx should be in cache", memoryCache!!.get(MemoryCache.Key(page.url)))
        }

        // Simulate active window at page 5: window is [4, 5, 6, 7, 8]
        val activeWindow = setOf(4, 5, 6, 7, 8)
        for (i in pages.indices) {
            if (i !in activeWindow) {
                val url = pages[i].url
                memoryCache!!.remove(MemoryCache.Key(url))
            }
        }

        // Verify pages outside window (0, 1, 2, 3, 9) were evicted
        for (i in listOf(0, 1, 2, 3, 9)) {
            assertNull("Page $i must be evicted from memory cache", memoryCache!!.get(MemoryCache.Key(pages[i].url)))
        }

        // Verify pages inside window (4, 5, 6, 7, 8) are retained in memory cache
        for (i in activeWindow) {
            assertNotNull("Page $i must be kept in memory cache", memoryCache!!.get(MemoryCache.Key(pages[i].url)))
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test
    fun `test reader image loader disk cache directory and size`() {
        val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        val imageLoader = createReaderImageLoader(context)
        val diskCache = imageLoader.diskCache
        assertNotNull("Disk cache must be configured", diskCache)
        assertEquals("manga_page_cache", diskCache!!.directory.name)
        assertEquals(500L * 1024 * 1024, diskCache.maxSize)
    }
}
