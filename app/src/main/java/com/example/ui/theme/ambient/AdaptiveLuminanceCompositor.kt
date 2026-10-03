package com.example.ui.theme.ambient

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AdaptiveLuminanceCompositor:
 * Modul kalkulator luminance adaptif dan penggabung warna sinematik Material 3.
 *
 * Formulasi:
 * Luminance = 0.2126 R + 0.7152 G + 0.0722 B
 */
object AdaptiveLuminanceCompositor {

    /**
     * Menghitung nilai luminance relatif dari warna (0.0f hingga 1.0f).
     */
    fun calculateAdaptedLuminance(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Mengembalikan warna teks dengan rasio kontras optimal (WCAG compliant) terhadap warna latar.
     */
    fun calculateContrastTextColor(backgroundColor: Color): Color {
        val luminance = calculateAdaptedLuminance(backgroundColor)
        return if (luminance > 0.5f) {
            Color(0xFF11111B) // Teks gelap pada latar terang
        } else {
            Color(0xFFCDD6F4) // Teks terang pada latar gelap
        }
    }

    /**
     * Memadukan warna dominan dengan warna surface latar agar menyatu secara harmonis.
     */
    fun blendWithSurface(
        color: Color,
        surfaceColor: Color = Color(0xFF11111B),
        alpha: Float = 0.35f
    ): Color {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        val r = color.red * clampedAlpha + surfaceColor.red * (1f - clampedAlpha)
        val g = color.green * clampedAlpha + surfaceColor.green * (1f - clampedAlpha)
        val b = color.blue * clampedAlpha + surfaceColor.blue * (1f - clampedAlpha)
        return Color(r, g, b, 1f)
    }

    /**
     * Menghasilkan kuas gradien dinamis yang mengalir dari warna vibrant/dominan ke surface gelap.
     */
    fun createDynamicGradient(
        palette: AmbientPalette,
        isDarkTheme: Boolean = true
    ): Brush {
        val baseColor = if (palette.vibrant != Color.Unspecified && palette.vibrant.alpha > 0) {
            palette.vibrant
        } else {
            palette.primaryDominant
        }

        val surfaceColor = if (isDarkTheme) Color(0xFF11111B) else Color(0xFFEFF1F5)
        val topGlow = baseColor.copy(alpha = if (isDarkTheme) 0.45f else 0.25f)
        val midGlow = palette.primaryDominant.copy(alpha = if (isDarkTheme) 0.20f else 0.10f)

        return Brush.verticalGradient(
            colors = listOf(
                topGlow,
                midGlow,
                surfaceColor
            )
        )
    }

    /**
     * Membuat border halus dengan aksen warna vibrant untuk kartu konten.
     */
    fun getGlassmorphicBorder(
        palette: AmbientPalette,
        width: Dp = 1.dp,
        alpha: Float = 0.25f
    ): BorderStroke {
        val accent = if (palette.lightVibrant != Color.Unspecified) palette.lightVibrant else palette.vibrant
        return BorderStroke(
            width = width,
            brush = Brush.linearGradient(
                listOf(
                    accent.copy(alpha = alpha),
                    Color.Transparent,
                    accent.copy(alpha = alpha * 0.5f)
                )
            )
        )
    }
}
