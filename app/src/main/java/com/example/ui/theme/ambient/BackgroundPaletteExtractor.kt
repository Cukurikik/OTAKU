package com.example.ui.theme.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class penampung palet warna ambient sinematik dan hasil kalkulasi luminance.
 */
data class AmbientPalette(
    val primaryDominant: Color = Color(0xFF1E1E2E),
    val vibrant: Color = Color(0xFF89B4FA),
    val darkVibrant: Color = Color(0xFF11111B),
    val lightVibrant: Color = Color(0xFFB4BEFE),
    val muted: Color = Color(0xFF6C7086),
    val darkMuted: Color = Color(0xFF181825),
    val adaptedLuminance: Float = 0.15f,
    val isDark: Boolean = true,
    val edgeBiasColors: List<Color> = listOf(Color(0xFF89B4FA), Color(0xFF11111B), Color(0xFFB4BEFE), Color(0xFF181825))
)

/**
 * BackgroundPaletteExtractor:
 * Modul pemroses bitmap menggunakan AndroidX Palette pada thread asynchronous (Dispatchers.Default)
 * agar ekstraksi warna tidak memblokir UI thread (menjaga frame budget <= 8.33ms untuk 120 FPS).
 */
object BackgroundPaletteExtractor {

    private val paletteCache = ConcurrentHashMap<String, AmbientPalette>()

    /**
     * Mengekstrak AmbientPalette dari Bitmap pada background dispatcher dengan downsampling.
     */
    suspend fun extractPaletteFromBitmap(
        bitmap: Bitmap,
        cacheKey: String? = null,
        maxDimension: Int = 112
    ): AmbientPalette = withContext(Dispatchers.Default) {
        if (cacheKey != null && paletteCache.containsKey(cacheKey)) {
            return@withContext paletteCache[cacheKey]!!
        }

        try {
            // Downsample bitmap untuk ekstraksi instan (< 4ms)
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val targetW = if (ratio >= 1f) maxDimension else (maxDimension * ratio).toInt().coerceAtLeast(1)
                val targetH = if (ratio < 1f) maxDimension else (maxDimension / ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val palette = Palette.from(scaledBitmap)
                .maximumColorCount(16)
                .generate()

            val dominantInt = palette.getDominantColor(0xFF1E1E2E.toInt())
            val vibrantInt = palette.getVibrantColor(dominantInt)
            val darkVibrantInt = palette.getDarkVibrantColor(0xFF11111B.toInt())
            val lightVibrantInt = palette.getLightVibrantColor(0xFFB4BEFE.toInt())
            val mutedInt = palette.getMutedColor(0xFF6C7086.toInt())
            val darkMutedInt = palette.getDarkMutedColor(0xFF181825.toInt())

            val dominantColor = Color(dominantInt)
            val vibrantColor = Color(vibrantInt)
            val darkVibrantColor = Color(darkVibrantInt)
            val lightVibrantColor = Color(lightVibrantInt)
            val mutedColor = Color(mutedInt)
            val darkMutedColor = Color(darkMutedInt)

            // Ekstrak warna tepi untuk Video Edge Luminance Bias
            val edgeColors = extractEdgeColors(scaledBitmap)

            // Kalkulasi Luminance: Luminance = 0.2126 R + 0.7152 G + 0.0722 B
            val luminance = AdaptiveLuminanceCompositor.calculateAdaptedLuminance(dominantColor)

            val result = AmbientPalette(
                primaryDominant = dominantColor,
                vibrant = vibrantColor,
                darkVibrant = darkVibrantColor,
                lightVibrant = lightVibrantColor,
                muted = mutedColor,
                darkMuted = darkMutedColor,
                adaptedLuminance = luminance,
                isDark = luminance < 0.5f,
                edgeBiasColors = edgeColors.ifEmpty {
                    listOf(vibrantColor, dominantColor, darkVibrantColor, lightVibrantColor)
                }
            )

            if (cacheKey != null) {
                paletteCache[cacheKey] = result
            }

            result
        } catch (_: Exception) {
            AmbientPalette()
        }
    }

    /**
     * Mengekstrak AmbientPalette langsung dari URL gambar cover/poster via Coil loader.
     */
    suspend fun extractPaletteFromUrl(
        context: Context,
        imageUrl: String
    ): AmbientPalette = withContext(Dispatchers.IO) {
        if (paletteCache.containsKey(imageUrl)) {
            return@withContext paletteCache[imageUrl]!!
        }

        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Software bitmap untuk Palette extractor
                .size(128, 128)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = drawableToBitmap(result.drawable)
                if (bitmap != null) {
                    return@withContext extractPaletteFromBitmap(bitmap, cacheKey = imageUrl)
                }
            }
        } catch (_: Exception) {}

        AmbientPalette()
    }

    private fun extractEdgeColors(bitmap: Bitmap): List<Color> {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return emptyList()

            val top = bitmap.getPixel(w / 2, 0)
            val bottom = bitmap.getPixel(w / 2, h - 1)
            val left = bitmap.getPixel(0, h / 2)
            val right = bitmap.getPixel(w - 1, h / 2)

            listOf(Color(top), Color(bottom), Color(left), Color(right))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun clearCache() {
        paletteCache.clear()
    }
}
