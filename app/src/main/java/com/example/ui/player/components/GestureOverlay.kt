package com.example.ui.player.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Handler Touch Gesture untuk Brightness dan Volume pada PlayerScreen.kt
 */
@Composable
fun PlayerGestureOverlay(
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    val isLeftScreen = change.position.x < (size.width / 2)
                    val delta = -dragAmount / size.height

                    if (isLeftScreen) {
                        onBrightnessChange(delta)
                    } else {
                        onVolumeChange(delta)
                    }
                }
            }
    )
}

/**
 * Modular Gesture Overlay that intercepts tap and drag gestures for
 * brightness adjustment, volume adjustment, and quick seeking.
 */
@Composable
fun GestureOverlay(
    player: Player,
    playerView: PlayerView?,
    isLocked: Boolean,
    isInPip: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLocked || isInPip) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var gestureFeedbackText by remember { mutableStateOf("") }
    var showGestureFeedback by remember { mutableStateOf(false) }

    fun triggerFeedback(text: String, durationMs: Long = 1000L) {
        gestureFeedbackText = text
        showGestureFeedback = true
        coroutineScope.launch {
            delay(durationMs)
            showGestureFeedback = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main Tap & Double Tap Gesture detector
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player, playerView) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width / 2) {
                                val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(newPos)
                                triggerFeedback("-10s")
                            } else {
                                val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration.coerceAtLeast(0L))
                                player.seekTo(newPos)
                                triggerFeedback("+10s")
                            }
                        },
                        onTap = {
                            playerView?.let { pv ->
                                if (pv.isControllerFullyVisible) {
                                    pv.hideController()
                                } else {
                                    pv.showController()
                                }
                            }
                        }
                    )
                }
        )

        // Center Area (Horizontal Drag for Seek - 40% width)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .pointerInput(player) {
                    var startPos = 0L
                    var accumulatedDeltaX = 0f
                    var duration = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startPos = player.currentPosition
                            duration = player.duration.coerceAtLeast(1L)
                            accumulatedDeltaX = 0f
                        },
                        onDragEnd = {
                            val deltaMs = (accumulatedDeltaX * 120).toLong()
                            val targetPos = (startPos + deltaMs).coerceIn(0L, duration)
                            player.seekTo(targetPos)
                            coroutineScope.launch {
                                delay(1000)
                                showGestureFeedback = false
                            }
                        },
                        onDragCancel = {
                            showGestureFeedback = false
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulatedDeltaX += dragAmount
                        val deltaMs = (accumulatedDeltaX * 120).toLong()
                        val targetPos = (startPos + deltaMs).coerceIn(0L, duration)
                        val sign = if (deltaMs >= 0) "+" else "-"
                        val deltaAbsSec = kotlin.math.abs(deltaMs) / 1000
                        val deltaStr = String.format("%s%02d:%02d", sign, deltaAbsSec / 60, deltaAbsSec % 60)
                        val targetStr = formatPlayerTime(targetPos)
                        val totalStr = formatPlayerTime(duration)
                        triggerFeedback("Seek $targetStr / $totalStr ($deltaStr)", durationMs = 3000L)
                    }
                }
        )

        // Left Side Drag (Brightness Control - 30% width)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .pointerInput(context) {
                    var startY = 0f
                    var startBright = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startY = offset.y
                            val act = context as? Activity
                            startBright = act?.window?.attributes?.screenBrightness ?: -1f
                            if (startBright < 0) startBright = 0.5f
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                delay(1000)
                                showGestureFeedback = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                delay(1000)
                                showGestureFeedback = false
                            }
                        }
                    ) { change, _ ->
                        change.consume()
                        val deltaY = change.position.y - startY
                        val act = context as? Activity
                        if (act != null) {
                            val window = act.window
                            val attributes = window.attributes
                            var brightness = startBright + (-deltaY / size.height)
                            brightness = brightness.coerceIn(0.01f, 1f)
                            attributes.screenBrightness = brightness
                            window.attributes = attributes
                            triggerFeedback(String.format("Brightness %.0f%%", brightness * 100))
                        }
                    }
                }
        )

        // Right Side Drag (Volume Control - 30% width)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.3f)
                .pointerInput(audioManager) {
                    var startY = 0f
                    var startVol = 0
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startY = offset.y
                            startVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                delay(1000)
                                showGestureFeedback = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                delay(1000)
                                showGestureFeedback = false
                            }
                        }
                    ) { change, _ ->
                        change.consume()
                        val deltaY = change.position.y - startY
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val volChange = (-deltaY / (size.height / maxVol)).toInt()
                        val newVol = (startVol + volChange).coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        triggerFeedback(String.format("Volume %.0f%%", (newVol.toFloat() / maxVol) * 100))
                    }
                }
        )

        // Center Gesture Feedback Overlay
        AnimatedVisibility(
            visible = showGestureFeedback,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = gestureFeedbackText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
