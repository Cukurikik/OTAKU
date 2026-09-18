package com.example.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    enableSmartPanel: Boolean = false
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    val scope = rememberCoroutineScope()
    val smartPanelState = remember { SmartPanelState() }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    // Smooth animation states for Smart Panel jumping
    val animScale by animateFloatAsState(targetValue = scale, animationSpec = tween(400))
    val animOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = tween(400))
    val animOffsetY by animateFloatAsState(targetValue = offsetY, animationSpec = tween(400))

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = { state ->
            if (enableSmartPanel) {
                val drawable = state.result.drawable
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    scope.launch {
                        smartPanelState.analyze(bitmap)
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer(
                scaleX = animScale,
                scaleY = animScale,
                translationX = animOffsetX,
                translationY = animOffsetY
            )
            .pointerInput(enableSmartPanel) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downTime = System.currentTimeMillis()
                    val isDoubleTap = downTime - lastTapTime < 300
                    lastTapTime = downTime
                    
                    if (isDoubleTap) {
                        down.consume()
                        if (enableSmartPanel && smartPanelState.panels.isNotEmpty()) {
                            val nextPanel = smartPanelState.nextPanel()
                            if (nextPanel != null) {
                                // Fit panel to screen
                                val panelWidth = nextPanel.width.toFloat()
                                val panelHeight = nextPanel.height.toFloat()
                                
                                // Approximate scale (based on intrinsic vs size, assuming FillWidth/Fit)
                                // Simplified approach: Zoom 2.5x and center on panel
                                scale = 2.5f
                                
                                // Calculate offset to center the panel
                                // This requires knowing the image's original dimensions vs layout dimensions
                                // As an approximation, assume the image fills the screen
                                val origWidth = size.width.toFloat()
                                val origHeight = size.height.toFloat()
                                
                                val relativeCenterX = (nextPanel.centerX / origWidth) - 0.5f
                                val relativeCenterY = (nextPanel.centerY / origHeight) - 0.5f
                                
                                val maxX = (origWidth * (scale - 1)) / 2
                                val maxY = (origHeight * (scale - 1)) / 2
                                
                                offsetX = (-relativeCenterX * origWidth * scale).coerceIn(-maxX, maxX)
                                offsetY = (-relativeCenterY * origHeight * scale).coerceIn(-maxY, maxY)
                            } else {
                                // Reset zoom
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        } else {
                            // Standard double tap zoom
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        }
                    }
                    
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        
                        if (event.changes.size > 1) {
                            // Pinch to zoom
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                val maxX = (size.width * (scale - 1)) / 2
                                val maxY = (size.height * (scale - 1)) / 2
                                offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f && !isDoubleTap) {
                            // Single finger pan when zoomed in
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            
                            val newOffsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                            val newOffsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                            
                            val hitHorizontalEdge = newOffsetX == maxX || newOffsetX == -maxX
                            
                            offsetX = newOffsetX
                            offsetY = newOffsetY
                            
                            if (!hitHorizontalEdge) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}
