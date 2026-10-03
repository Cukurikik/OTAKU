package com.example.ui.theme.ambient

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * VideoFrameLuminanceBias:
 * Modul pengukur tingkat kecerahan dan komponen warna tepi frame video
 * untuk efek memancar pada panel layar AMOLED/OLED tanpa membebani frame rate.
 */
@Composable
fun VideoPlayerAmbientGlow(
    palette: AmbientPalette?,
    modifier: Modifier = Modifier,
    glowIntensity: Float = 0.55f,
    edgeGlowRadiusDp: Dp = 36.dp,
    content: @Composable () -> Unit
) {
    val activePalette = palette ?: AmbientPalette()
    val edgeColors = activePalette.edgeBiasColors

    val topColor = edgeColors.getOrElse(0) { activePalette.vibrant }
    val bottomColor = edgeColors.getOrElse(1) { activePalette.primaryDominant }
    val leftColor = edgeColors.getOrElse(2) { activePalette.darkVibrant }
    val rightColor = edgeColors.getOrElse(3) { activePalette.lightVibrant }

    val animTop by animateColorAsState(
        targetValue = topColor.copy(alpha = glowIntensity),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animTopGlow"
    )
    val animBottom by animateColorAsState(
        targetValue = bottomColor.copy(alpha = glowIntensity),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animBottomGlow"
    )
    val animLeft by animateColorAsState(
        targetValue = leftColor.copy(alpha = glowIntensity * 0.7f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animLeftGlow"
    )
    val animRight by animateColorAsState(
        targetValue = rightColor.copy(alpha = glowIntensity * 0.7f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animRightGlow"
    )

    val density = LocalDensity.current
    val radiusPx = with(density) { edgeGlowRadiusDp.toPx() }

    Box(modifier = modifier) {
        // Render Edge Luminance Canvas Bias
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Top Edge Bias
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(animTop, Color.Transparent),
                    startY = 0f,
                    endY = radiusPx * 2.5f
                ),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(w, radiusPx * 2.5f)
            )

            // 2. Bottom Edge Bias
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, animBottom),
                    startY = (h - radiusPx * 2.5f).coerceAtLeast(0f),
                    endY = h
                ),
                topLeft = Offset(0f, (h - radiusPx * 2.5f).coerceAtLeast(0f)),
                size = androidx.compose.ui.geometry.Size(w, radiusPx * 2.5f)
            )

            // 3. Left Edge Bias
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(animLeft, Color.Transparent),
                    startX = 0f,
                    endX = radiusPx * 2f
                ),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(radiusPx * 2f, h)
            )

            // 4. Right Edge Bias
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, animRight),
                    startX = (w - radiusPx * 2f).coerceAtLeast(0f),
                    endX = w
                ),
                topLeft = Offset((w - radiusPx * 2f).coerceAtLeast(0f), 0f),
                size = androidx.compose.ui.geometry.Size(radiusPx * 2f, h)
            )
        }

        // Render main content (e.g. SurfaceView / Player View)
        content()
    }
}
