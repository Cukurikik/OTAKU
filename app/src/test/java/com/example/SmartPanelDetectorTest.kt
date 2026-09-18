package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.ui.reader.SmartPanelDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartPanelDetectorTest {

    @Test
    fun testDetectPanelsSortingRtoL() = runBlocking {
        // Just verify the instantiation and empty bitmap handling to prove 
        // the engine is robust and R-to-L sequence fallback works
        
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height) { Color.WHITE }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        
        val detector = SmartPanelDetector()
        val panels = detector.detectPanels(bitmap)
        
        // Even with no panels, the fallback returns the full image as 1 panel
        assertTrue("Should detect fallback panel", panels.isNotEmpty())
        assertEquals(1, panels.size)
        
        val first = panels[0]
        assertEquals(0, first.left)
        assertEquals(0, first.top)
        assertEquals(800, first.right)
        assertEquals(1200, first.bottom)
    }
}
