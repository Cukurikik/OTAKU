package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ambient.AdaptiveLuminanceCompositor
import com.example.ui.theme.ambient.AmbientPalette
import com.example.ui.theme.ambient.BackgroundPaletteExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class Proposal5DynamicAmbientGlowTest {

    @Test
    fun testAdaptiveLuminanceCalculationFormulas() {
        // Red = 1.0, Green = 0.0, Blue = 0.0 -> Luminance = 0.2126
        val redLuminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(Color(1f, 0f, 0f, 1f))
        assertEquals(0.2126f, redLuminance, 0.001f)

        // Green = 1.0 -> Luminance = 0.7152
        val greenLuminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(Color(0f, 1f, 0f, 1f))
        assertEquals(0.7152f, greenLuminance, 0.001f)

        // Blue = 1.0 -> Luminance = 0.0722
        val blueLuminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(Color(0f, 0f, 1f, 1f))
        assertEquals(0.0722f, blueLuminance, 0.001f)

        // Black = 0.0 -> Luminance = 0.0
        val blackLuminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(Color.Black)
        assertEquals(0.0f, blackLuminance, 0.001f)

        // White = 1.0 -> Luminance = 1.0
        val whiteLuminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(Color.White)
        assertEquals(1.0f, whiteLuminance, 0.001f)
    }

    @Test
    fun testContrastTextColorSelection() {
        // Latar belakang sangat terang (Putih) harus memilih teks gelap (0xFF11111B)
        val darkText = AdaptiveLuminanceCompositor.calculateContrastTextColor(Color.White)
        assertEquals(Color(0xFF11111B), darkText)

        // Latar belakang sangat gelap (Hitam) harus memilih teks terang (0xFFCDD6F4)
        val lightText = AdaptiveLuminanceCompositor.calculateContrastTextColor(Color.Black)
        assertEquals(Color(0xFFCDD6F4), lightText)
    }

    @Test
    fun testBlendWithSurface() {
        val vibrantRed = Color(1f, 0f, 0f, 1f)
        val darkSurface = Color(0f, 0f, 0f, 1f)

        val blended = AdaptiveLuminanceCompositor.blendWithSurface(
            color = vibrantRed,
            surfaceColor = darkSurface,
            alpha = 0.5f
        )

        assertEquals(0.5f, blended.red, 0.01f)
        assertEquals(0.0f, blended.green, 0.01f)
        assertEquals(0.0f, blended.blue, 0.01f)
        assertEquals(1.0f, blended.alpha, 0.01f)
    }

    @Test
    fun testBackgroundPaletteExtractorFromBitmap() = runBlocking {
        BackgroundPaletteExtractor.clearCache()

        // Buat bitmap uji dengan kuadran warna khusus
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Kuadran atas biru, bawah merah
        paint.color = AndroidColor.rgb(30, 144, 255) // DodgerBlue
        canvas.drawRect(0f, 0f, 64f, 32f, paint)

        paint.color = AndroidColor.rgb(255, 69, 0) // OrangeRed
        canvas.drawRect(0f, 32f, 64f, 64f, paint)

        val palette = BackgroundPaletteExtractor.extractPaletteFromBitmap(bitmap, cacheKey = "test_bitmap_key")
        assertNotNull(palette)
        assertTrue(palette.edgeBiasColors.isNotEmpty())
        assertEquals(4, palette.edgeBiasColors.size)

        // Pastikan cache bekerja pada panggilan kedua
        val cachedPalette = BackgroundPaletteExtractor.extractPaletteFromBitmap(bitmap, cacheKey = "test_bitmap_key")
        assertSame(palette, cachedPalette)
    }

    @Test
    fun testAmbientPaletteDefaultProperties() {
        val defaultPalette = AmbientPalette()
        assertTrue(defaultPalette.isDark)
        assertTrue(defaultPalette.adaptedLuminance < 0.5f)
        assertEquals(4, defaultPalette.edgeBiasColors.size)
    }

    @Test
    fun testGlassmorphicBorderCreation() {
        val palette = AmbientPalette(vibrant = Color(0xFF89B4FA), lightVibrant = Color(0xFFB4BEFE))
        val border = AdaptiveLuminanceCompositor.getGlassmorphicBorder(palette)
        assertNotNull(border)
    }
}
