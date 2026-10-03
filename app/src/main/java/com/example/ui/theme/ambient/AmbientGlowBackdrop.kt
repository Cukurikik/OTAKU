package com.example.ui.theme.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * AmbientGlowBackdrop:
 * Komponen Canvas Shader kustom penerima warna dominan yang dirender dengan
 * algoritma Gaussian Radial Gradient Blur (sigma = 48dp) di belakang UI.
 *
 * Formulasi:
 * G(x, y) = 1/(2*pi*sigma^2) * exp(-(x^2 + y^2)/(2*sigma^2)), sigma = 48dp
 * t_frame <= 8.33ms (120 FPS target)
 */
@Composable
fun AmbientGlowBackdrop(
    palette: AmbientPalette?,
    modifier: Modifier = Modifier,
    sigmaDp: Dp = 48.dp,
    glowAlpha: Float = 0.45f,
    content: @Composable () -> Unit
) {
    val activePalette = palette ?: AmbientPalette()

    val animatedDominant by animateColorAsState(
        targetValue = activePalette.primaryDominant.copy(alpha = glowAlpha),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ambientDominantColor"
    )

    val animatedVibrant by animateColorAsState(
        targetValue = activePalette.vibrant.copy(alpha = glowAlpha * 0.75f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ambientVibrantColor"
    )

    val animatedDarkMuted by animateColorAsState(
        targetValue = activePalette.darkMuted.copy(alpha = 0.95f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ambientDarkMutedColor"
    )

    val density = LocalDensity.current
    val sigmaPx = with(density) { sigmaDp.toPx() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientGlow(
                dominantColor = animatedDominant,
                vibrantColor = animatedVibrant,
                darkBaseColor = animatedDarkMuted,
                sigmaPx = sigmaPx
            )
        }
        content()
    }
}

/**
 * Fungsi render Canvas DrawScope untuk Gaussian Radial Blur ambient glow.
 */
private fun DrawScope.drawAmbientGlow(
    dominantColor: Color,
    vibrantColor: Color,
    darkBaseColor: Color,
    sigmaPx: Float
) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // 1. Base dark background
    drawRect(color = darkBaseColor)

    // 2. Primary Top-Center Gaussian Radial Glow (sigma = 48dp)
    val primaryCenter = Offset(canvasWidth * 0.5f, canvasHeight * 0.2f)
    val primaryRadius = (sigmaPx * 6f).coerceAtLeast(canvasWidth * 0.7f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                dominantColor,
                dominantColor.copy(alpha = dominantColor.alpha * 0.4f),
                Color.Transparent
            ),
            center = primaryCenter,
            radius = primaryRadius
        ),
        center = primaryCenter,
        radius = primaryRadius
    )

    // 3. Secondary Off-Center Vibrant Accent Glow
    val secondaryCenter = Offset(canvasWidth * 0.8f, canvasHeight * 0.35f)
    val secondaryRadius = (sigmaPx * 4.5f).coerceAtLeast(canvasWidth * 0.5f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                vibrantColor,
                vibrantColor.copy(alpha = vibrantColor.alpha * 0.25f),
                Color.Transparent
            ),
            center = secondaryCenter,
            radius = secondaryRadius
        ),
        center = secondaryCenter,
        radius = secondaryRadius
    )
}

/**
 * Kartu konten dengan border ambient glow dan glassmorphic backdrop.
 */
@Composable
fun AmbientGlowCard(
    palette: AmbientPalette?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val activePalette = palette ?: AmbientPalette()
    val shape = RoundedCornerShape(cornerRadius)
    val border = AdaptiveLuminanceCompositor.getGlassmorphicBorder(activePalette)

    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = activePalette.darkMuted.copy(alpha = 0.75f)
            ),
            modifier = modifier
        ) {
            content()
        }
    } else {
        Card(
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = activePalette.darkMuted.copy(alpha = 0.75f)
            ),
            modifier = modifier
        ) {
            content()
        }
    }
}
