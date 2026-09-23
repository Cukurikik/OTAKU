package com.example.ui.tv

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV-Optimized Player Controls Overlay.
 * Designed for Android TV / Smart TV remotes with full D-Pad navigation,
 * clear visual focus highlighting, auto-hiding OSD, and TV-friendly dialogs.
 */
@Composable
fun TvPlayerControlsOverlay(
    player: Player,
    title: String = "",
    episodeTitle: String = "",
    serverName: String = "",
    qualityLabel: String = "",
    qualities: List<VideoQuality> = emptyList(),
    subtitles: List<Subtitle> = emptyList(),
    servers: List<VideoServer> = emptyList(),
    currentSpeed: Float = 1.0f,
    onQualitySelected: (VideoQuality) -> Unit = {},
    onServerSelected: (VideoServer) -> Unit = {},
    onSubtitleSelected: (Subtitle?) -> Unit = {},
    onSpeedSelected: (Float) -> Unit = {},
    onSubtitleSettingsClick: () -> Unit = {},
    onShaderSettingsClick: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onBack: () -> Unit,
    isAudioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null,
    exoTracks: androidx.media3.common.Tracks = player.currentTracks,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    
    // Feedback Toast (e.g. +10s, -10s, Speed change)
    var feedbackText by remember { mutableStateOf<String?>(null) }
    
    // Dialog states
    var showQualityDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    val playPauseFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible, showQualityDialog, showServerDialog, showSubtitleDialog, showSpeedDialog, showAudioDialog) {
        if (isControlsVisible && !showQualityDialog && !showServerDialog && !showSubtitleDialog && !showSpeedDialog && !showAudioDialog) {
            delay(5000)
            if (player.isPlaying) {
                isControlsVisible = false
            }
        }
    }

    // Sync player playback state & time
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0L) {
                duration = player.duration
            }
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    fun showControlsAndResetTimer() {
        isControlsVisible = true
    }

    fun triggerFeedback(text: String) {
        feedbackText = text
        coroutineScope.launch {
            delay(1500)
            if (feedbackText == text) {
                feedbackText = null
            }
        }
    }

    fun seekBy(offsetMs: Long) {
        val target = (player.currentPosition + offsetMs).coerceIn(0L, duration.coerceAtLeast(1L))
        player.seekTo(target)
        currentPosition = target
        val prefix = if (offsetMs >= 0) "+${offsetMs / 1000}s" else "${offsetMs / 1000}s"
        triggerFeedback(prefix)
    }

    // Capture TV D-Pad and Media key events at root level
    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!isControlsVisible) {
                                isControlsVisible = true
                                coroutineScope.launch {
                                    delay(100)
                                    playPauseFocusRequester.requestFocus()
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isControlsVisible) {
                                seekBy(-10000L)
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isControlsVisible) {
                                seekBy(10000L)
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isControlsVisible) {
                                isControlsVisible = true
                                coroutineScope.launch {
                                    delay(100)
                                    playPauseFocusRequester.requestFocus()
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (player.isPlaying) player.pause() else player.play()
                            isPlaying = player.isPlaying
                            showControlsAndResetTimer()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.play()
                            isPlaying = true
                            showControlsAndResetTimer()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.pause()
                            isPlaying = false
                            showControlsAndResetTimer()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(15000L)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(-15000L)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            onNextEpisode()
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (showQualityDialog || showServerDialog || showSubtitleDialog || showSpeedDialog) {
                                showQualityDialog = false
                                showServerDialog = false
                                showSubtitleDialog = false
                                showSpeedDialog = false
                                return@onPreviewKeyEvent true
                            } else if (isControlsVisible) {
                                isControlsVisible = false
                                return@onPreviewKeyEvent true
                            } else {
                                onBack()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            }
    ) {
        // Transient Seek / Status Feedback Pill (Center Screen)
        if (feedbackText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFF64B5F6), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = feedbackText ?: "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Full TV Controls Overlay with animated fade
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // 1. TOP HEADER BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        TvFocusableIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            onClick = onBack
                        )
                        Column {
                            if (title.isNotEmpty()) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (episodeTitle.isNotEmpty()) {
                                Text(
                                    text = episodeTitle,
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Top Right Badges & Subtitle Settings
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (serverName.isNotEmpty()) {
                            TvBadge(text = serverName, icon = Icons.Default.Dns)
                        }
                        if (qualityLabel.isNotEmpty()) {
                            TvBadge(text = qualityLabel, icon = Icons.Default.HighQuality)
                        }
                        TvFocusableIconButton(
                            icon = Icons.Default.Subtitles,
                            contentDescription = "Pengaturan Tampilan Subtitle",
                            onClick = {
                                onSubtitleSettingsClick()
                                showControlsAndResetTimer()
                            }
                        )
                    }
                }

                // 2. CENTER PLAYBACK CONTROLS
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Intro Button (+85s)
                    TvFocusableButton(
                        icon = Icons.Default.FastForward,
                        label = "+85s Intro",
                        onClick = {
                            seekBy(85000L)
                            showControlsAndResetTimer()
                        }
                    )

                    // Rewind 10s
                    TvFocusableIconButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "Mundur 10 Detik",
                        size = 56.dp,
                        iconSize = 32.dp,
                        onClick = {
                            seekBy(-10000L)
                            showControlsAndResetTimer()
                        }
                    )

                    // Primary Play/Pause Button (Large & Focus Target)
                    TvFocusableIconButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        size = 72.dp,
                        iconSize = 44.dp,
                        isPrimary = true,
                        focusRequester = playPauseFocusRequester,
                        onClick = {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.play()
                                isPlaying = true
                            }
                            showControlsAndResetTimer()
                        }
                    )

                    // Forward 10s
                    TvFocusableIconButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "Maju 10 Detik",
                        size = 56.dp,
                        iconSize = 32.dp,
                        onClick = {
                            seekBy(10000L)
                            showControlsAndResetTimer()
                        }
                    )

                    // Next Episode Button
                    TvFocusableButton(
                        icon = Icons.Default.SkipNext,
                        label = "Episode Berikut",
                        onClick = {
                            onNextEpisode()
                            showControlsAndResetTimer()
                        }
                    )
                }

                // 3. BOTTOM PANEL: TIME SCRUBBER & ACTION CHIPS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Time text and Focusable Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }

                    // TV Interactive Seek Scrubber
                    TvSeekScrubber(
                        currentPosition = currentPosition,
                        duration = duration,
                        onSeekTo = { newPos ->
                            player.seekTo(newPos)
                            currentPosition = newPos
                            showControlsAndResetTimer()
                        }
                    )

                    // Bottom Quick Selection Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quality Chip
                        if (qualities.isNotEmpty()) {
                            TvActionChip(
                                label = "Kualitas: $qualityLabel",
                                icon = Icons.Default.Settings,
                                onClick = {
                                    showQualityDialog = true
                                    showControlsAndResetTimer()
                                }
                            )
                        }

                        // Server Chip
                        if (servers.isNotEmpty()) {
                            TvActionChip(
                                label = "Server: $serverName",
                                icon = Icons.Default.Dns,
                                onClick = {
                                    showServerDialog = true
                                    showControlsAndResetTimer()
                                }
                            )
                        }

                        // Dual-Audio Track Chip (Proposal 2)
                        val audioTracks = remember(exoTracks) {
                            com.example.ui.player.AnimeTrackSelectionEngine.getAvailableAudioTracks(exoTracks)
                        }
                        if (audioTracks.isNotEmpty()) {
                            val activeAudio = audioTracks.firstOrNull { it.isSelected }?.label ?: "Audio"
                            TvActionChip(
                                label = "Audio: $activeAudio",
                                icon = Icons.Default.Audiotrack,
                                onClick = {
                                    showAudioDialog = true
                                    showControlsAndResetTimer()
                                }
                            )
                        }

                        // Subtitle Chip
                        val subTracks = remember(exoTracks, subtitles) {
                            com.example.ui.player.AnimeTrackSelectionEngine.getAvailableSubtitleTracks(exoTracks, subtitles)
                        }
                        TvActionChip(
                            label = if (subTracks.any { it.isSelected && !it.isOff }) "Subtitle: Aktif" else "Subtitle: Mati",
                            icon = Icons.Default.ClosedCaption,
                            onClick = {
                                showSubtitleDialog = true
                                showControlsAndResetTimer()
                            }
                        )

                        // GPU Shader Chip
                        TvActionChip(
                            label = "GPU Shader",
                            icon = Icons.Default.AutoAwesome,
                            onClick = {
                                onShaderSettingsClick()
                                showControlsAndResetTimer()
                            }
                        )

                        // Playback Speed Chip
                        TvActionChip(
                            label = "${currentSpeed}x Kecepatan",
                            icon = Icons.Default.Speed,
                            onClick = {
                                showSpeedDialog = true
                                showControlsAndResetTimer()
                            }
                        )

                        // Audio-Only Mode Chip
                        if (onToggleAudioOnly != null) {
                            TvActionChip(
                                label = if (isAudioOnly) "Mode Audio-Only: Aktif" else "Mode Audio-Only",
                                icon = Icons.Default.Headphones,
                                onClick = {
                                    onToggleAudioOnly()
                                    feedbackText = if (!isAudioOnly) "Mode Audio-Only: Aktif (Hemat Bandwidth 80%)" else "Mode Video Penuh"
                                    showControlsAndResetTimer()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 4. TV-FRIENDLY SELECTION DIALOGS
    if (showQualityDialog) {
        TvListSelectionDialog(
            title = "Pilih Resolusi / Kualitas",
            items = qualities.map { it.qualityLabel },
            selectedItem = qualityLabel,
            onItemSelected = { label ->
                qualities.find { it.qualityLabel == label }?.let { onQualitySelected(it) }
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showServerDialog) {
        TvListSelectionDialog(
            title = "Pilih Server Pemutaran",
            items = servers.map { it.name },
            selectedItem = serverName,
            onItemSelected = { name ->
                servers.find { it.name == name }?.let { onServerSelected(it) }
                showServerDialog = false
            },
            onDismiss = { showServerDialog = false }
        )
    }

    if (showSubtitleDialog) {
        val subTracks = com.example.ui.player.AnimeTrackSelectionEngine.getAvailableSubtitleTracks(exoTracks, subtitles)
        val selectedSub = subTracks.firstOrNull { it.isSelected }?.label ?: "Mati (Off)"
        TvListSelectionDialog(
            title = "Pilih Trek Subtitle (ASS/SSA & VTT)",
            items = subTracks.map { it.label },
            selectedItem = selectedSub,
            onItemSelected = { label ->
                val selectedOption = subTracks.find { it.label == label }
                if (selectedOption != null) {
                    if (trackSelector != null) {
                        com.example.ui.player.AnimeTrackSelectionEngine.selectSubtitleTrack(player, trackSelector, selectedOption)
                    } else if (selectedOption.isOff) {
                        onSubtitleSelected(null)
                    } else {
                        val matchingSub = subtitles.find { it.language.equals(selectedOption.language, ignoreCase = true) }
                        onSubtitleSelected(matchingSub)
                    }
                    feedbackText = if (selectedOption.isOff) "Subtitle Dinonaktifkan" else "Subtitle: $label"
                }
                showSubtitleDialog = false
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showAudioDialog) {
        val audioTracks = com.example.ui.player.AnimeTrackSelectionEngine.getAvailableAudioTracks(exoTracks)
        val selectedAudio = audioTracks.firstOrNull { it.isSelected }?.label ?: "Default"
        TvListSelectionDialog(
            title = "Pilih Audio Track (Dual-Audio Dub Indo / JP)",
            items = audioTracks.map { it.label },
            selectedItem = selectedAudio,
            onItemSelected = { label ->
                val selectedOption = audioTracks.find { it.label == label }
                if (selectedOption != null && trackSelector != null) {
                    com.example.ui.player.AnimeTrackSelectionEngine.selectAudioTrack(player, trackSelector, selectedOption)
                    feedbackText = "Audio: $label"
                }
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        TvListSelectionDialog(
            title = "Pilih Kecepatan Putar",
            items = speeds.map { "${it}x" },
            selectedItem = "${currentSpeed}x",
            onItemSelected = { label ->
                val speed = label.removeSuffix("x").toFloatOrNull() ?: 1.0f
                onSpeedSelected(speed)
                triggerFeedback("Kecepatan: ${speed}x")
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

// -------------------------------------------------------------
// TV UI HELPER COMPONENTS WITH FOCUS HIGHLIGHTING
// -------------------------------------------------------------

@Composable
fun TvFocusableIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, label = "scale")

    val bgModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    Box(
        modifier = bgModifier
            .size(size)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color(0xFF64B5F6)
                    isPrimary -> Color(0xFF1976D2)
                    else -> Color.Black.copy(alpha = 0.6f)
                }
            )
            .border(
                BorderStroke(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.3f)
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun TvFocusableButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.1f else 1.0f, label = "scale")

    Row(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF64B5F6) else Color.Black.copy(alpha = 0.6f))
            .border(
                BorderStroke(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TvActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    Row(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.12f))
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 0.5.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TvBadge(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Focusable Seek Scrubber for D-Pad navigation.
 * When focused, pressing D-Pad Left/Right scrubs backwards or forwards by 5 seconds.
 */
@Composable
fun TvSeekScrubber(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val newPos = (currentPosition - 5000L).coerceAtLeast(0L)
                            onSeekTo(newPos)
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val newPos = (currentPosition + 5000L).coerceAtMost(duration)
                            onSeekTo(newPos)
                            return@onKeyEvent true
                        }
                    }
                }
                false
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 8.dp else 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            // Played Track
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .fillMaxHeight()
                    .background(if (isFocused) Color(0xFF64B5F6) else Color(0xFFE53935))
            )
        }

        // Thumb indicator when focused
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .wrapContentWidth(Alignment.End)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0xFF1976D2), CircleShape)
            )
        }
    }
}

/**
 * TV Modal Selection Dialog with full D-Pad support and clear focus rings on list items.
 */
@Composable
fun TvListSelectionDialog(
    title: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            border = BorderStroke(1.5.dp, Color(0xFF64B5F6)),
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(items) { item ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        val isSelected = item == selectedItem

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isItemFocused -> Color(0xFF64B5F6)
                                        isSelected -> Color.White.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        width = if (isItemFocused) 2.dp else 0.dp,
                                        color = if (isItemFocused) Color.White else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onItemSelected(item) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                color = if (isItemFocused) Color.Black else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Terpilih",
                                    tint = if (isItemFocused) Color.Black else Color(0xFF64B5F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TvFocusableButton(
                        icon = Icons.Default.Close,
                        label = "Tutup",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
