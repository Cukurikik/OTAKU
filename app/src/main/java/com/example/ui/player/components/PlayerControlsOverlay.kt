package com.example.ui.player.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.trackers.AniSkipResult
import com.example.media.AspectRatioMode
import com.example.ui.components.CastButton
import com.example.ui.player.TrackQualityOption
import com.example.ui.player.TrackSelectorHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modular Controls Overlay for ExoPlayer playback controls, top bars,
 * track selections, ani-skip actions, and subtitle styling overlays.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerControlsOverlay(
    player: Player,
    title: String,
    episodeTitle: String,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    isInPip: Boolean,
    isCasting: Boolean,
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    currentServerName: String,
    servers: List<VideoServer>,
    onServerSelected: (VideoServer) -> Unit,
    currentQualityLabel: String,
    qualities: List<VideoQuality>,
    hlsQualityOptions: List<TrackQualityOption>,
    onQualitySelected: (VideoQuality) -> Unit,
    onHlsQualitySelected: (TrackQualityOption) -> Unit,
    exoTracks: Tracks,
    trackSelector: DefaultTrackSelector,
    currentSubtitles: List<Subtitle>,
    activeSubtitleText: String,
    subtitleFontSize: Float,
    subtitleTextColor: Int,
    subtitleBgColor: Int,
    skipTimes: List<AniSkipResult>,
    autoSkipOpEd: Boolean = false,
    onToggleAutoSkip: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onShowSubtitleDialog: () -> Unit,
    onShowBrightnessDialog: () -> Unit,
    onShowShaderDialog: () -> Unit = {},
    onShowWatchPartyMenu: () -> Unit = {},
    currentAspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    onAspectRatioChange: (AspectRatioMode) -> Unit = {},
    isAudioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSubtitleTrackMenu by remember { mutableStateOf(false) }
    var showServerMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    var feedbackText by remember { mutableStateOf("") }
    var showFeedback by remember { mutableStateOf(false) }

    fun triggerFeedback(msg: String) {
        feedbackText = msg
        showFeedback = true
        coroutineScope.launch {
            delay(1200)
            showFeedback = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Casting to TV Indicator Overlay
        if (isCasting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = "Casting to Remote Display",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Memutar di Perangkat Google Cast (TV)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title.ifEmpty { "Streaming Video" },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Custom External Subtitle Overlay
        if (activeSubtitleText.isNotEmpty()) {
            Text(
                text = activeSubtitleText,
                color = Color(subtitleTextColor),
                fontSize = subtitleFontSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp, start = 32.dp, end = 32.dp)
                    .background(Color(subtitleBgColor), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Lock button (accessible when not in PiP)
        if (!isInPip) {
            IconButton(
                onClick = {
                    onToggleLock()
                    triggerFeedback(if (!isLocked) "Layar Terkunci" else "Layar Terbuka")
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .background(
                        if (isLocked) Color.Red.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Screen Lock",
                    tint = Color.White
                )
            }
        }

        // Top & Bottom Controls (Only visible when NOT locked and NOT in PiP)
        if (!isInPip && !isLocked) {
            // Top Left: Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            // Top Right: Controls Action Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Universal Cast Button (DLNA/UPnP Smart TV & Chromecast)
                val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                CastButton(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                    mediaTitle = if (episodeTitle.isNotBlank()) "$title - $episodeTitle" else title,
                    mediaUrl = currentUri
                )

                // Brightness Settings Button
                IconButton(
                    onClick = onShowBrightnessDialog,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.BrightnessMedium,
                        contentDescription = "Pengaturan Kecerahan",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Subtitle Settings Button
                IconButton(
                    onClick = onShowSubtitleDialog,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Pengaturan Subtitle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // GPU Shader Video Post-Processing Button
                IconButton(
                    onClick = onShowShaderDialog,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "GLSL GPU Shader Enhancement",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // P2P LAN Watch Party Co-Watching Button
                IconButton(
                    onClick = onShowWatchPartyMenu,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "P2P LAN Watch Party",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Auto-Skip OP/ED Toggle Button
                IconButton(
                    onClick = {
                        val newMode = !autoSkipOpEd
                        onToggleAutoSkip(newMode)
                        triggerFeedback(if (newMode) "Auto-Skip OP/ED: Aktif" else "Auto-Skip OP/ED: Nonaktif")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (autoSkipOpEd) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            else Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Auto-Skip OP/ED",
                        tint = if (autoSkipOpEd) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Audio-Only Mode Toggle (Saves >=80% bandwidth and battery)
                IconButton(
                    onClick = {
                        onToggleAudioOnly()
                        triggerFeedback(if (!isAudioOnly) "Mode Audio-Only: Aktif (Hemat Bandwidth 80%)" else "Mode Video Penuh")
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isAudioOnly) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            else Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Mode Audio-Only (Hemat Data)",
                        tint = if (isAudioOnly) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Picture-in-Picture Button
                IconButton(
                    onClick = onEnterPip,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Picture-in-Picture",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Playback Speed Selector
                Box {
                    Button(
                        onClick = { showSpeedMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${currentSpeed}x", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${speed}x",
                                        fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                    triggerFeedback("Kecepatan: ${speed}x")
                                }
                            )
                        }
                    }
                }

                // Aspect Ratio Mode Selector
                Box {
                    IconButton(
                        onClick = { showAspectRatioMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Pilih Rasio Layar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showAspectRatioMenu,
                        onDismissRequest = { showAspectRatioMenu = false }
                    ) {
                        AspectRatioMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.label,
                                        fontWeight = if (currentAspectRatioMode == mode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onAspectRatioChange(mode)
                                    showAspectRatioMenu = false
                                    triggerFeedback("Rasio Layar: ${mode.label}")
                                }
                            )
                        }
                    }
                }

                // Native Subtitle Track Selector
                val textGroups = exoTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                if (textGroups.isNotEmpty() || currentSubtitles.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showSubtitleTrackMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Pilih Subtitle",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSubtitleTrackMenu,
                            onDismissRequest = { showSubtitleTrackMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mati (Off)") },
                                onClick = {
                                    trackSelector.setParameters(
                                        trackSelector.buildUponParameters()
                                            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                                    )
                                    showSubtitleTrackMenu = false
                                }
                            )
                            textGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val label = format.language ?: format.label ?: "Subtitle ${i + 1}"
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                            trackSelector.setParameters(
                                                trackSelector.buildUponParameters()
                                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                                    .addOverride(override)
                                            )
                                            showSubtitleTrackMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Server Selector Dropdown
                if (servers.size > 1) {
                    Box {
                        Button(
                            onClick = { showServerMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(currentServerName, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(
                            expanded = showServerMenu,
                            onDismissRequest = { showServerMenu = false }
                        ) {
                            servers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name) },
                                    onClick = {
                                        onServerSelected(server)
                                        showServerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Quality Selector Dropdown
                if (qualities.size > 1 || hlsQualityOptions.isNotEmpty()) {
                    Box {
                        Button(
                            onClick = { showQualityMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(currentQualityLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false }
                        ) {
                            if (qualities.size > 1) {
                                qualities.forEach { q ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                q.qualityLabel,
                                                fontWeight = if (q.qualityLabel == currentQualityLabel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onQualitySelected(q)
                                            showQualityMenu = false
                                        }
                                    )
                                }
                            } else {
                                hlsQualityOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                opt.label,
                                                fontWeight = if (opt.isSelected || (opt.isAuto && currentQualityLabel == "Auto")) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onHlsQualitySelected(opt)
                                            showQualityMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Right: AniSkip Auto-Engine & Skip Button
            var lastSkippedIntervalEndTime by remember { mutableFloatStateOf(-1f) }
            var currentPositionMs by remember { mutableLongStateOf(0L) }

            LaunchedEffect(player, skipTimes, autoSkipOpEd) {
                while (true) {
                    currentPositionMs = player.currentPosition
                    val posSeconds = currentPositionMs / 1000f
                    val activeSkip = skipTimes.firstOrNull { skip ->
                        posSeconds >= skip.interval.startTime && posSeconds < skip.interval.endTime
                    }

                    if (activeSkip != null && autoSkipOpEd && player.isPlaying) {
                        if (lastSkippedIntervalEndTime != activeSkip.interval.endTime) {
                            lastSkippedIntervalEndTime = activeSkip.interval.endTime
                            val targetMs = (activeSkip.interval.endTime * 1000).toLong() + 200L
                            player.seekTo(targetMs)
                            val label = when (activeSkip.skipType) {
                                "op" -> "Intro (Opening)"
                                "ed" -> "Outro (Ending)"
                                "recap" -> "Recap"
                                else -> "Bagian Intro/Outro"
                            }
                            triggerFeedback("Auto-Skip $label Selesai")
                        }
                    }
                    delay(300)
                }
            }

            val currentSkip = skipTimes.firstOrNull { skip ->
                val posSeconds = currentPositionMs / 1000f
                posSeconds >= skip.interval.startTime && posSeconds <= skip.interval.endTime
            }

            if (currentSkip != null && !autoSkipOpEd) {
                val remainingSeconds = ((currentSkip.interval.endTime - (currentPositionMs / 1000f)).coerceAtLeast(0f)).toInt()
                val label = when (currentSkip.skipType) {
                    "op" -> "Skip Intro (OP)"
                    "ed" -> "Skip Outro (ED)"
                    "recap" -> "Skip Recap"
                    else -> "Skip"
                }

                Button(
                    onClick = {
                        player.seekTo((currentSkip.interval.endTime * 1000).toLong() + 200L)
                        triggerFeedback("$label Selesai")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 70.dp, end = 24.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (remainingSeconds > 0) "$label (${remainingSeconds}s)" else label,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Bottom Right: Quick Skip Intro / Outro Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val newPos = (player.currentPosition + 85000).coerceAtMost(player.duration.coerceAtLeast(0L))
                        player.seekTo(newPos)
                        triggerFeedback("Skip Intro (+85s)")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+85s Intro", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        val newPos = (player.currentPosition + 90000).coerceAtMost(player.duration.coerceAtLeast(0L))
                        player.seekTo(newPos)
                        triggerFeedback("Skip Outro (+90s)")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+90s Outro", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Feedback Text Overlay
        AnimatedVisibility(
            visible = showFeedback && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = feedbackText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
