package com.example.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.cast.CastHelper
import com.example.data.NetworkModule
import com.example.data.SettingsManager
import com.example.data.VideoCache
import com.example.data.downloads.ExoDownloadManager
import com.example.data.sources.Subtitle
import com.example.data.sources.VideoQuality
import com.example.data.sources.VideoServer
import com.example.data.torrent.TorrentDataSource
import com.example.data.torrent.TorrentStreamManager
import com.example.data.trackers.AniSkipResult
import com.example.media.AspectRatioMode
import com.example.media.ExoPlayerManager
import com.example.media.MediaSessionService
import com.example.media.PersistentHttpHeaderResolver
import com.example.media.PersistentResolvingDataSourceFactory
import com.example.media.withAdditionalHeaders
import com.example.ui.player.SubtitleCue
import com.example.ui.player.SubtitleParser
import com.example.ui.player.SubtitleSyncController
import com.example.ui.player.TrackSelectorHelper
import com.example.ui.player.components.AudioOnlyOverlay
import com.example.ui.player.components.AutoNextEpisodeOverlay
import com.example.ui.player.components.BrightnessDialog
import com.example.ui.player.components.GestureOverlay
import com.example.ui.player.components.PlayerControlsOverlay
import com.example.ui.player.components.SubtitleCustomizationDialog
import com.example.ui.player.components.ShaderCustomizationDialog
import com.example.media.shader.AnimeShaderManager
import com.example.media.shader.AnimeShaderMode
import com.example.network.watchparty.ExoPlayerSyncController
import com.example.network.watchparty.WatchPartyRole
import com.example.ui.player.components.WatchPartyHostDialog
import com.example.ui.player.components.WatchPartyJoinDialog
import com.example.ui.player.components.WatchPartyStatusChip
import com.example.ui.player.components.WatchPartyChatOverlay
import com.example.ui.player.components.VideoSurface
import com.example.ui.player.components.WebViewPlayer
import com.example.ui.tv.TvPlayerControlsOverlay
import com.example.ui.viewmodels.PlayerState
import com.example.ui.viewmodels.PlayerViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberIsInPipMode(): Boolean {
    val context = LocalContext.current
    var isInPip by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            isInPip = activity?.isInPictureInPictureMode == true
        }
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
    return isInPip
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    isTvMode: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
    val isTv = isTvMode || (uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        if (!isTv) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (!isTv) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            }
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val streamInfo by viewModel.streamInfo.collectAsStateWithLifecycle()
    val isFailoverResolving by viewModel.isFailoverResolving.collectAsStateWithLifecycle()
    val failoverMessage by viewModel.failoverMessage.collectAsStateWithLifecycle()
    val isSearchingMigration by viewModel.isSearchingMigration.collectAsStateWithLifecycle()
    val migrationCandidates by viewModel.migrationCandidates.collectAsStateWithLifecycle()
    val showMigrationDialog by viewModel.showMigrationDialog.collectAsStateWithLifecycle()
    val isPerformingMigration by viewModel.isPerformingMigration.collectAsStateWithLifecycle()
    val startPosition by viewModel.startPosition.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()
    val coverImageUrl by viewModel.coverImageUrl.collectAsStateWithLifecycle()
    val seriesTitle by viewModel.seriesTitle.collectAsStateWithLifecycle()
    val skipTimes by viewModel.skipTimes.collectAsStateWithLifecycle()
    val autoSkipOpEd by viewModel.autoSkipOpEd.collectAsStateWithLifecycle()
    val nextEpisode by viewModel.nextEpisode.collectAsStateWithLifecycle()
    val previousEpisode by viewModel.previousEpisode.collectAsStateWithLifecycle()
    val isInPip = rememberIsInPipMode()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val info = streamInfo) {
            is PlayerState.Loading -> {
                if (viewModel.activeSourceId == "torrent_source") {
                    TorrentLoadingScreen()
                } else {
                    LoadingScreen()
                }
            }
            is PlayerState.Error -> ErrorScreen(
                message = info.message, 
                onRetry = viewModel::retry,
                onMigrate = viewModel::openMigrationDialog,
                onBack = onBack
            )
            is PlayerState.Success, is PlayerState.SeamlessBuffering -> {
                val data = if (info is PlayerState.Success) info.data else (info as PlayerState.SeamlessBuffering).previousData
                val hasDirectQualities = data.qualities.isNotEmpty() || data.servers.isNotEmpty()
                val isDirectMp4OrM3u8 = data.embedUrl.lowercase().let {
                    it.endsWith(".mp4") || it.endsWith(".m3u8") || it.contains(".mp4?") || it.contains(".m3u8?")
                }

                if (hasDirectQualities) {
                    val initialQualities = if (data.qualities.isNotEmpty()) data.qualities else data.servers.firstOrNull()?.qualities ?: emptyList()
                    val initialSubtitles = if (data.subtitles.isNotEmpty()) data.subtitles else data.servers.firstOrNull()?.subtitles ?: emptyList()

                    val initialUrl = data.defaultQuality?.url
                        ?: data.bestQuality?.url
                        ?: data.servers.firstOrNull()?.let { s ->
                            s.qualities.firstOrNull { it.qualityLabel == "720p" }?.url
                                ?: s.qualities.maxByOrNull { it.qualityLabel.filter(Char::isDigit).toIntOrNull() ?: 0 }?.url
                                ?: s.embedUrl
                        }
                        ?: ""

                    val initialDrmLicenseUrl = data.drmLicenseUrl
                        ?: data.qualities.find { it.url == initialUrl }?.drmLicenseUrl
                        ?: data.servers.firstOrNull()?.drmLicenseUrl

                    val initialDrmHeaders = data.drmHeaders.ifEmpty {
                        data.qualities.find { it.url == initialUrl }?.drmHeaders
                            ?: data.servers.firstOrNull()?.drmHeaders
                            ?: emptyMap()
                    }

                    ExoPlayerView(
                        initialUrl = initialUrl,
                        qualities = initialQualities,
                        subtitles = initialSubtitles,
                        servers = data.servers,
                        initialHeaders = data.headers,
                        initialDrmLicenseUrl = initialDrmLicenseUrl,
                        initialDrmHeaders = initialDrmHeaders,
                        posterUrl = coverImageUrl,
                        seriesTitle = seriesTitle,
                        isInPip = isInPip,
                        startPosition = startPosition,
                        onSaveProgress = viewModel::saveProgress,
                        onNextEpisode = viewModel::playNextEpisode,
                        hasNextEpisode = nextEpisode != null,
                        nextEpisodeTitle = nextEpisode?.title ?: "",
                        onPreviousEpisode = viewModel::playPreviousEpisode,
                        hasPreviousEpisode = previousEpisode != null,
                        previousEpisodeTitle = previousEpisode?.title ?: "",
                        onPlaybackError = { failedUrl, error, pos ->
                            viewModel.handlePlaybackError(failedUrl, pos, error)
                        },
                        skipTimes = skipTimes,
                        autoSkipOpEd = autoSkipOpEd,
                        onToggleAutoSkip = viewModel::setAutoSkipOpEd,
                        on80PercentReached = viewModel::markAsFinishedAndProcessAutomations,
                        onBack = onBack,
                        isTvMode = isTv,
                        title = currentTitle,
                        isFailoverResolving = isFailoverResolving,
                        failoverMessage = failoverMessage
                    )
                } else if (isDirectMp4OrM3u8) {
                    ExoPlayerView(
                        initialUrl = data.embedUrl,
                        qualities = emptyList(),
                        subtitles = data.subtitles,
                        servers = data.servers,
                        initialHeaders = data.headers,
                        initialDrmLicenseUrl = data.drmLicenseUrl,
                        initialDrmHeaders = data.drmHeaders,
                        posterUrl = coverImageUrl,
                        seriesTitle = seriesTitle,
                        isInPip = isInPip,
                        startPosition = startPosition,
                        onSaveProgress = viewModel::saveProgress,
                        onNextEpisode = viewModel::playNextEpisode,
                        hasNextEpisode = nextEpisode != null,
                        nextEpisodeTitle = nextEpisode?.title ?: "",
                        onPreviousEpisode = viewModel::playPreviousEpisode,
                        hasPreviousEpisode = previousEpisode != null,
                        previousEpisodeTitle = previousEpisode?.title ?: "",
                        onPlaybackError = { failedUrl, error, pos ->
                            viewModel.handlePlaybackError(failedUrl, pos, error)
                        },
                        skipTimes = skipTimes,
                        autoSkipOpEd = autoSkipOpEd,
                        onToggleAutoSkip = viewModel::setAutoSkipOpEd,
                        on80PercentReached = viewModel::markAsFinishedAndProcessAutomations,
                        onBack = onBack,
                        isTvMode = isTv,
                        title = currentTitle,
                        isFailoverResolving = isFailoverResolving,
                        failoverMessage = failoverMessage
                    )
                } else if (data.embedUrl.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WebViewPlayer(url = data.embedUrl)
                        if (!isInPip) {
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
                        }
                    }
                }
            }
        }

        if (showMigrationDialog) {
            com.example.ui.components.OneTapMigrationDialog(
                currentTitle = currentTitle,
                currentSourceId = viewModel.activeSourceId,
                candidates = migrationCandidates,
                isLoading = isSearchingMigration,
                isMigrating = isPerformingMigration,
                onDismissRequest = viewModel::dismissMigrationDialog,
                onConfirmMigration = viewModel::executeOneTapMigration,
                onRetrySearch = viewModel::triggerAutoMigrationSearch
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
fun TorrentLoadingScreen() {
    val torrentProgress by TorrentStreamManager.progress.collectAsState()
    val torrentSpeed by TorrentStreamManager.downloadSpeed.collectAsState()
    val torrentSeeds by TorrentStreamManager.seeds.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "P2P Stream Engine Aktif",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Menghubungkan ke Peer & Mengunduh Sekuensial...",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Speed and Seeds Row
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Progress Buffer",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = String.format("%.1f%%", torrentProgress),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Kecepatan Unduh",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatTorrentSpeed(torrentSpeed),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Green,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Peers / Seeds",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "$torrentSeeds S",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Yellow,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatTorrentSpeed(bytesPerSecond: Float): String {
    val kb = bytesPerSecond / 1024f
    return if (kb > 1024) {
        String.format("%.2f MB/s", kb / 1024f)
    } else {
        String.format("%.1f KB/s", kb)
    }
}

@Composable
fun ErrorScreen(
    message: String, 
    onRetry: () -> Unit,
    onMigrate: () -> Unit,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Gagal Memutar Video",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text("Kembali", color = Color.White)
                }
                Button(onClick = onRetry) {
                    Text("Coba Lagi")
                }
                FilledTonalButton(onClick = onMigrate) {
                    Text("Pindah Sumber")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    initialUrl: String,
    qualities: List<VideoQuality>,
    subtitles: List<Subtitle>,
    servers: List<VideoServer> = emptyList(),
    initialHeaders: Map<String, String> = emptyMap(),
    initialDrmLicenseUrl: String? = null,
    initialDrmHeaders: Map<String, String> = emptyMap(),
    posterUrl: String? = null,
    seriesTitle: String = "",
    isInPip: Boolean = false,
    startPosition: Long = 0L,
    onSaveProgress: (Long, Long) -> Unit = { _, _ -> },
    onNextEpisode: () -> Unit,
    hasNextEpisode: Boolean = false,
    nextEpisodeTitle: String = "",
    onPreviousEpisode: () -> Unit = {},
    hasPreviousEpisode: Boolean = false,
    previousEpisodeTitle: String = "",
    onPlaybackError: (String, androidx.media3.common.PlaybackException?, Long) -> Unit = { _, _, _ -> },
    on80PercentReached: () -> Unit,
    onBack: () -> Unit,
    isTvMode: Boolean = false,
    skipTimes: List<AniSkipResult> = emptyList(),
    autoSkipOpEd: Boolean = false,
    onToggleAutoSkip: (Boolean) -> Unit = {},
    title: String = "",
    episodeTitle: String = "",
    isFailoverResolving: Boolean = false,
    failoverMessage: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsManager = remember { SettingsManager(context) }

    val currentOnNextEpisode by rememberUpdatedState(onNextEpisode)
    val currentOnPreviousEpisode by rememberUpdatedState(onPreviousEpisode)
    val currentOn80Percent by rememberUpdatedState(on80PercentReached)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnSaveProgress by rememberUpdatedState(onSaveProgress)

    var showAutoNextCountdown by remember { mutableStateOf(false) }
    var autoNextCountdownSeconds by remember { mutableIntStateOf(5) }
    var isAutoNextCancelled by remember { mutableStateOf(false) }
    var currentAspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }
    var isAudioOnlyMode by rememberSaveable { mutableStateOf(false) }

    var isPlayingOrBuffered by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pendingSeekPosition by remember { mutableLongStateOf(startPosition) }
    var currentQualities by remember { mutableStateOf(qualities) }
    var currentSubtitles by remember { mutableStateOf(subtitles) }
    var currentQualityLabel by remember {
        mutableStateOf(qualities.find { it.url == initialUrl }?.qualityLabel ?: "Auto")
    }

    LaunchedEffect(currentUrl) {
        isAutoNextCancelled = false
        showAutoNextCountdown = false
    }

    LaunchedEffect(showAutoNextCountdown) {
        if (showAutoNextCountdown) {
            while (autoNextCountdownSeconds > 0) {
                delay(1000)
                if (!showAutoNextCountdown || isAutoNextCancelled) break
                autoNextCountdownSeconds--
            }
            if (showAutoNextCountdown && !isAutoNextCancelled) {
                showAutoNextCountdown = false
                currentOnNextEpisode()
            }
        }
    }

    var currentHeaders by remember {
        mutableStateOf(
            qualities.find { it.url == initialUrl }?.headers?.ifEmpty { null }
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.headers?.ifEmpty { null }
                ?: initialHeaders
        )
    }

    var currentDrmLicenseUrl by remember {
        mutableStateOf(
            qualities.find { it.url == initialUrl }?.drmLicenseUrl
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.drmLicenseUrl
                ?: initialDrmLicenseUrl
        )
    }

    var currentDrmHeaders by remember {
        mutableStateOf(
            qualities.find { it.url == initialUrl }?.drmHeaders?.ifEmpty { null }
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.drmHeaders?.ifEmpty { null }
                ?: initialDrmHeaders
        )
    }

    val currentUrlRef = rememberUpdatedState(currentUrl)
    val currentHeadersRef = rememberUpdatedState(currentHeaders)

    var currentServerName by remember {
        mutableStateOf(
            servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.name
                ?: (if (servers.isNotEmpty()) servers.first().name else "Server")
        )
    }

    var isLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(settingsManager.playerSpeed) }
    var exoTracks by remember { mutableStateOf(Tracks.EMPTY) }
    val hlsQualityOptions = remember(exoTracks) {
        TrackSelectorHelper.getVideoQualities(exoTracks)
    }

    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showBrightnessDialog by remember { mutableStateOf(false) }
    var showShaderDialog by remember { mutableStateOf(false) }
    var showWatchPartyHostDialog by remember { mutableStateOf(false) }
    var showWatchPartyJoinDialog by remember { mutableStateOf(false) }
    var showWatchPartyChoiceDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val watchPartyController = remember { ExoPlayerSyncController(context, coroutineScope) }
    val watchPartyActiveRooms by watchPartyController.activeRooms.collectAsState()
    val watchPartyChatMessages by watchPartyController.chatMessages.collectAsState()
    val watchPartyIsConnected by watchPartyController.isConnected.collectAsState()
    val watchPartyPingMs by watchPartyController.pingMs.collectAsState()
    val watchPartyPeersCount by watchPartyController.connectedPeersCount.collectAsState()
    var currentShaderMode by remember {
        mutableStateOf(
            try { AnimeShaderMode.valueOf(settingsManager.animeShaderMode) }
            catch (e: Exception) { AnimeShaderMode.ANIME_HDR }
        )
    }
    var currentShaderIntensity by remember { mutableFloatStateOf(settingsManager.animeShaderIntensity) }
    var customBrightness by remember { mutableFloatStateOf(settingsManager.customBrightness) }
    var backgroundAudioEnabled by remember { mutableStateOf(settingsManager.backgroundAudio) }

    var subtitleFontSize by remember { mutableFloatStateOf(settingsManager.subtitleFontSize) }
    var subtitleTextColor by remember { mutableIntStateOf(settingsManager.subtitleTextColor) }
    var subtitleBgColor by remember { mutableIntStateOf(settingsManager.subtitleBgColor) }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var activeExternalCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var parsedExternalSubtitles by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var activeSubtitleText by remember { mutableStateOf("") }

    val bandwidthMeter = remember {
        DefaultBandwidthMeter.Builder(context).build()
    }

    val trackSelector = remember(bandwidthMeter) {
        DefaultTrackSelector(
            context,
            AdaptiveTrackSelection.Factory()
        ).apply {
            setParameters(buildUponParameters().setPreferredTextLanguage("id"))
        }
    }

    val dynamicHeaderResolver = remember {
        PersistentHttpHeaderResolver(
            headersProvider = { currentHeadersRef.value },
            streamUrlProvider = { currentUrlRef.value }
        )
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
            .setUserAgent(NetworkModule.userAgent)

        val resolvingDataSourceFactory = PersistentResolvingDataSourceFactory.create(
            upstreamFactory = httpDataSourceFactory,
            headersProvider = { currentHeadersRef.value },
            streamUrlProvider = { currentUrlRef.value }
        )

        val segmentCacheFactory = com.example.media.CacheDataSourceFactory(context)
        val hlsProxy = com.example.media.HlsSegmentCacheProxy(context, segmentCacheFactory)
        val streamDataSourceFactory = hlsProxy.createHlsCachingDataSourceFactory(resolvingDataSourceFactory)

        val downloadCache = ExoDownloadManager.getCache(context)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(streamDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val delegatingDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            if (currentUrlRef.value == "torrent://stream") {
                TorrentDataSource(TorrentStreamManager)
            } else {
                cacheDataSourceFactory.createDataSource()
            }
        }

        val loadControl = com.example.media.AdaptiveLoadControlBuilder().buildDefaultLoadControl()

        ExoPlayer.Builder(context)
            .setRenderersFactory(com.example.ui.player.CustomRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(delegatingDataSourceFactory))
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                setPlaybackSpeed(currentSpeed)
                AnimeShaderManager.applySavedShaderEffect(context, this)
                watchPartyController.attachPlayer(this)
                addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        exoTracks = tracks
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isPlayingOrBuffered = true
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            if (hasNextEpisode && !isAutoNextCancelled) {
                                showAutoNextCountdown = false
                                currentOnNextEpisode()
                            }
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val currentPos = this@apply.currentPosition.coerceAtLeast(0L)
                        currentOnPlaybackError(currentUrlRef.value, error, currentPos)
                    }
                })
            }
    }

    val castHelper = remember(exoPlayer) {
        CastHelper(
            context = context,
            localPlayer = exoPlayer,
            headers = currentHeaders,
            dynamicHeadersProvider = {
                val dummySpec = androidx.media3.datasource.DataSpec(Uri.parse(currentUrlRef.value.ifEmpty { "https://localhost" }))
                dynamicHeaderResolver.resolveDataSpec(dummySpec).httpRequestHeaders
            }
        )
    }
    val currentPlayer by castHelper.currentPlayer.collectAsStateWithLifecycle()
    val isCasting by castHelper.isCastAvailable.collectAsStateWithLifecycle()

    val toggleAudioOnlyMode = {
        val next = !isAudioOnlyMode
        isAudioOnlyMode = next
        ExoPlayerManager.setAudioOnlyMode(exoPlayer, trackSelector, next)
    }

    val mediaSession = remember(exoPlayer) {
        MediaSessionService.createMediaSession(
            context = context,
            player = exoPlayer,
            onNext = if (hasNextEpisode) { { currentOnNextEpisode() } } else null,
            onPrevious = if (hasPreviousEpisode) { { currentOnPreviousEpisode() } } else null,
            onToggleAudioOnly = { toggleAudioOnlyMode() }
        )
    }

    DisposableEffect(mediaSession, hasNextEpisode, hasPreviousEpisode, isAudioOnlyMode) {
        MediaSessionService.setActiveSession(
            session = mediaSession,
            onNext = if (hasNextEpisode) { { currentOnNextEpisode() } } else null,
            onPrevious = if (hasPreviousEpisode) { { currentOnPreviousEpisode() } } else null,
            onToggleAudioOnly = { toggleAudioOnlyMode() }
        )
        MediaSessionService.startService(context)

        onDispose {
            MediaSessionService.setActiveSession(null)
            MediaSessionService.stopService(context)
            mediaSession.release()
        }
    }

    DisposableEffect(castHelper) {
        onDispose { castHelper.release() }
    }

    LaunchedEffect(initialUrl) {
        if (currentUrl != initialUrl) {
            val currentPos = currentPlayer.currentPosition.coerceAtLeast(0L)
            if (currentPos > 0L && pendingSeekPosition == 0L) {
                pendingSeekPosition = currentPos
            }
            currentUrl = initialUrl
            currentQualities = qualities
            currentSubtitles = subtitles
            currentQualityLabel = qualities.find { it.url == initialUrl }?.qualityLabel ?: "Auto"
            currentHeaders = qualities.find { it.url == initialUrl }?.headers?.ifEmpty { null }
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.headers?.ifEmpty { null }
                ?: initialHeaders
            currentDrmLicenseUrl = qualities.find { it.url == initialUrl }?.drmLicenseUrl
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.drmLicenseUrl
                ?: initialDrmLicenseUrl
            currentDrmHeaders = qualities.find { it.url == initialUrl }?.drmHeaders?.ifEmpty { null }
                ?: servers.find { it.embedUrl == initialUrl || it.qualities.any { q -> q.url == initialUrl } }?.drmHeaders?.ifEmpty { null }
                ?: initialDrmHeaders
        }
    }

    DisposableEffect(customBrightness) {
        val act = context as? Activity
        if (act != null) {
            val window = act.window
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (customBrightness >= 0f) customBrightness else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = layoutParams
        }
        onDispose {
            val act = context as? Activity
            if (act != null) {
                val window = act.window
                val layoutParams = window.attributes
                layoutParams.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams
            }
        }
    }

    LaunchedEffect(currentUrl, currentSubtitles, currentDrmLicenseUrl, currentDrmHeaders) {
        if (currentUrl.isNotEmpty()) {
            isPlayingOrBuffered = false
            val currentPos = if (pendingSeekPosition > 0L) {
                val p = pendingSeekPosition
                pendingSeekPosition = 0L
                p
            } else {
                currentPlayer.currentPosition.coerceAtLeast(0L)
            }

            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(if (seriesTitle.isNotBlank()) seriesTitle else title.ifEmpty { "Streaming Video" })
                .setSubtitle(if (episodeTitle.isNotBlank()) episodeTitle else currentQualityLabel)
                .setDisplayTitle(if (seriesTitle.isNotBlank()) "$seriesTitle - $title" else title)
                .setArtworkUri(if (!posterUrl.isNullOrEmpty()) Uri.parse(posterUrl) else null)
                .build()

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(currentUrl))
                .setMediaMetadata(mediaMetadata)

            // External Subtitle Integration via MediaItem.SubtitleConfiguration
            if (currentSubtitles.isNotEmpty()) {
                val subtitleConfigs = currentSubtitles.mapIndexed { index, sub ->
                    val mimeType = ExoPlayerManager.inferSubtitleMimeType(sub.format.ifEmpty { sub.url })
                    val isDefault = index == 0 || sub.language.contains("id", ignoreCase = true) || sub.language.contains("indonesia", ignoreCase = true)
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(mimeType)
                        .setLanguage(sub.language.ifEmpty { "id" })
                        .setLabel(sub.language.ifEmpty { "Subtitle ${index + 1}" })
                        .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }

            if (!currentDrmLicenseUrl.isNullOrEmpty()) {
                val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(currentDrmLicenseUrl)
                if (currentDrmHeaders.isNotEmpty()) {
                    drmConfigBuilder.setLicenseRequestHeaders(currentDrmHeaders)
                }
                mediaItemBuilder.setDrmConfiguration(drmConfigBuilder.build())
            }

            currentPlayer.setMediaItem(mediaItemBuilder.build())
            currentPlayer.prepare()
            if (currentPos > 0L) {
                currentPlayer.seekTo(currentPos)
            }
            currentPlayer.play()

            delay(5000)
            if (!isPlayingOrBuffered && !currentUrl.startsWith("file://")) {
                val pos = currentPlayer.currentPosition.coerceAtLeast(0L)
                currentOnPlaybackError(currentUrl, null, pos)
            }
        }
    }

    LaunchedEffect(currentSubtitles) {
        val firstSub = currentSubtitles.firstOrNull()?.url
        parsedExternalSubtitles = if (!firstSub.isNullOrEmpty()) {
            SubtitleParser.fetchAndParse(firstSub)
        } else {
            emptyList()
        }
    }

    val subtitleSyncController = remember { SubtitleSyncController(subtitleOffsetMs) }
    LaunchedEffect(subtitleOffsetMs) {
        subtitleSyncController.applyOffsetMs(subtitleOffsetMs)
    }

    LaunchedEffect(currentPlayer, subtitleOffsetMs, parsedExternalSubtitles) {
        if (parsedExternalSubtitles.isEmpty()) return@LaunchedEffect
        while (true) {
            val displayTime = subtitleSyncController.calculateDisplayTime(currentPlayer.currentPosition)
            val active = parsedExternalSubtitles.filter { displayTime in it.startMs..it.endMs }
            if (active != activeExternalCues) {
                activeExternalCues = active
                activeSubtitleText = active.joinToString("\n") { it.text }
            }
            delay(100)
        }
    }

    LaunchedEffect(currentPlayer, currentUrl) {
        var hasTriggered80Percent = false
        while (true) {
            delay(5000)
            if (currentPlayer.isPlaying) {
                val duration = currentPlayer.duration
                val currentPosition = currentPlayer.currentPosition
                if (duration > 0) {
                    currentOnSaveProgress(currentPosition, duration)
                    if (!hasTriggered80Percent && currentPosition >= duration * 0.8) {
                        hasTriggered80Percent = true
                        currentOn80Percent()
                    }
                }
            }
        }
    }

    // Continuous Playback Check Loop: t_sisa = T_total - T_current <= 10s
    LaunchedEffect(currentPlayer, currentUrl, hasNextEpisode) {
        while (true) {
            delay(1000)
            if (currentPlayer.isPlaying && hasNextEpisode && !isAutoNextCancelled) {
                val duration = currentPlayer.duration
                val currentPosition = currentPlayer.currentPosition
                if (duration > 0L) {
                    val remainingTime = duration - currentPosition
                    if (remainingTime in 1..10_000L) {
                        if (!showAutoNextCountdown) {
                            showAutoNextCountdown = true
                            val secs = (remainingTime / 1000L).toInt().coerceIn(1, 10).coerceAtMost(5)
                            autoNextCountdownSeconds = secs
                        }
                    } else if (remainingTime > 10_000L && showAutoNextCountdown) {
                        showAutoNextCountdown = false
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, isAudioOnlyMode, backgroundAudioEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    currentOnSaveProgress(currentPlayer.currentPosition, currentPlayer.duration)
                    val act = context as? Activity
                    val inPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        act?.isInPictureInPictureMode == true
                    } else false

                    if (!inPip && !backgroundAudioEnabled && !isAudioOnlyMode) {
                        currentPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    val act = context as? Activity
                    val inPip = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        act?.isInPictureInPictureMode == true
                    } else false
                    if (!inPip && !backgroundAudioEnabled && !isAudioOnlyMode) {
                        currentPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlayingOrBuffered) {
                        currentPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            currentOnSaveProgress(currentPlayer.currentPosition, currentPlayer.duration)
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerViewRef?.player = null
            exoPlayer.clearVideoSurface()
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    val currentAspectRatio = remember { mutableStateOf(android.util.Rational(16, 9)) }

    val triggerPip: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val act = context as? Activity
            if (act != null) {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(currentAspectRatio.value)
                    .build()
                act.enterPictureInPictureMode(params)
            }
        }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val activity = context as? androidx.activity.ComponentActivity
        DisposableEffect(activity, exoPlayer, isAudioOnlyMode) {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        // Android PiP supports aspect ratios between 1:2.39 (0.418) and 2.39:1 (2.39)
                        if (ratio in 0.42f..2.38f) {
                            currentAspectRatio.value = android.util.Rational(videoSize.width, videoSize.height)
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(currentAspectRatio.value)
                                .setAutoEnterEnabled(exoPlayer.isPlaying && !isAudioOnlyMode)
                                .build()
                            activity?.setPictureInPictureParams(params)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(currentAspectRatio.value)
                        .setAutoEnterEnabled(isPlaying && !isAudioOnlyMode)
                        .build()
                    activity?.setPictureInPictureParams(params)
                }
            }
            exoPlayer.addListener(listener)

            onDispose {
                exoPlayer.removeListener(listener)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isAudioOnlyMode && !isInPip && !isTvMode) {
            // Audio-Only Mode Overlay
            AudioOnlyOverlay(
                player = currentPlayer,
                title = title,
                episodeTitle = episodeTitle,
                posterUrl = posterUrl,
                serverName = currentServerName,
                currentSpeed = currentSpeed,
                onSpeedChange = { sp ->
                    currentSpeed = sp
                    settingsManager.playerSpeed = sp
                    exoPlayer.setPlaybackSpeed(sp)
                },
                onSwitchToVideoMode = {
                    toggleAudioOnlyMode()
                },
                onPreviousEpisode = if (hasPreviousEpisode) { { currentOnPreviousEpisode() } } else null,
                onNextEpisode = if (hasNextEpisode) { { currentOnNextEpisode() } } else null,
                onBack = onBack
            )
        } else {
            // 1. Modular Video Surface
            VideoSurface(
                player = currentPlayer,
                useController = !isTvMode && !isInPip && !isLocked,
                subtitleTextColor = subtitleTextColor,
                subtitleBgColor = subtitleBgColor,
                subtitleFontSizeSp = subtitleFontSize,
                resizeMode = currentAspectRatioMode.resizeMode,
                onPlayerViewCreated = { pv ->
                    playerViewRef = pv
                    pv.resizeMode = currentAspectRatioMode.resizeMode
                }
            )

            // 2. Platform Overlays
            if (isTvMode) {
                val effectiveTvQualities = remember(currentQualities, hlsQualityOptions, currentUrl) {
                    if (currentQualities.size > 1) {
                        currentQualities
                    } else if (hlsQualityOptions.isNotEmpty()) {
                        hlsQualityOptions.map { opt ->
                            VideoQuality(qualityLabel = opt.label, url = currentUrl)
                        }
                    } else {
                        emptyList()
                    }
                }

                TvPlayerControlsOverlay(
                    player = currentPlayer,
                    trackSelector = trackSelector,
                    exoTracks = exoTracks,
                    title = title,
                    episodeTitle = episodeTitle,
                    serverName = currentServerName,
                    qualityLabel = currentQualityLabel,
                    qualities = effectiveTvQualities,
                    subtitles = currentSubtitles,
                    servers = servers,
                    currentSpeed = currentSpeed,
                    onQualitySelected = { q ->
                        if (currentQualities.size > 1) {
                            val currentPos = currentPlayer.currentPosition.coerceAtLeast(0L)
                            pendingSeekPosition = currentPos
                            currentUrl = q.url
                            currentQualityLabel = q.qualityLabel
                            if (q.headers.isNotEmpty()) {
                                val newHeaders = currentHeaders.toMutableMap()
                                newHeaders.putAll(q.headers)
                                currentHeaders = newHeaders
                            }
                            currentDrmLicenseUrl = q.drmLicenseUrl ?: currentDrmLicenseUrl
                            if (q.drmHeaders.isNotEmpty()) {
                                currentDrmHeaders = q.drmHeaders
                            }
                        } else {
                            val matchedOpt = hlsQualityOptions.find { it.label == q.qualityLabel }
                            if (matchedOpt != null) {
                                TrackSelectorHelper.applyVideoQuality(trackSelector, matchedOpt)
                                currentQualityLabel = if (matchedOpt.isAuto) "Auto" else matchedOpt.label.split(" ").first()
                            }
                        }
                    },
                    onServerSelected = { server ->
                        val currentPos = currentPlayer.currentPosition.coerceAtLeast(0L)
                        pendingSeekPosition = currentPos
                        currentQualities = server.qualities
                        currentSubtitles = server.subtitles
                        currentServerName = server.name
                        val best = server.qualities.firstOrNull { it.qualityLabel == "720p" }
                            ?: server.qualities.maxByOrNull { it.qualityLabel.filter(Char::isDigit).toIntOrNull() ?: 0 }
                        currentUrl = best?.url ?: server.embedUrl
                        currentQualityLabel = best?.qualityLabel ?: "Auto"

                        val newHeaders = mutableMapOf<String, String>()
                        newHeaders.putAll(initialHeaders)
                        newHeaders.putAll(server.headers)
                        if (best != null && best.headers.isNotEmpty()) {
                            newHeaders.putAll(best.headers)
                        }
                        currentHeaders = newHeaders
                        currentDrmLicenseUrl = best?.drmLicenseUrl ?: server.drmLicenseUrl ?: initialDrmLicenseUrl
                        currentDrmHeaders = best?.drmHeaders?.ifEmpty { null } ?: server.drmHeaders.ifEmpty { null } ?: initialDrmHeaders
                    },
                    onSubtitleSelected = { sub ->
                        if (sub == null) {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            )
                        } else {
                            val textGroup = exoTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
                            if (textGroup != null) {
                                val override = androidx.media3.common.TrackSelectionOverride(textGroup.mediaTrackGroup, 0)
                                trackSelector.setParameters(
                                    trackSelector.buildUponParameters()
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .addOverride(override)
                                )
                            }
                        }
                    },
                    onSpeedSelected = { sp ->
                        currentSpeed = sp
                        settingsManager.playerSpeed = sp
                        exoPlayer.setPlaybackSpeed(sp)
                    },
                    onSubtitleSettingsClick = { showSubtitleDialog = true },
                    onShaderSettingsClick = { showShaderDialog = true },
                    onNextEpisode = currentOnNextEpisode,
                    onBack = onBack
                )
            } else {
                // Mobile Gestures & Touch Overlay
                GestureOverlay(
                    player = currentPlayer,
                    playerView = playerViewRef,
                    isLocked = isLocked,
                    isInPip = isInPip
                )

                // Mobile Controls & Top/Bottom Bar Overlays
                PlayerControlsOverlay(
                    player = currentPlayer,
                    title = title,
                    episodeTitle = episodeTitle,
                    isLocked = isLocked,
                    onToggleLock = { isLocked = !isLocked },
                    isInPip = isInPip,
                    isCasting = isCasting,
                    currentSpeed = currentSpeed,
                    onSpeedChange = { sp ->
                        currentSpeed = sp
                        settingsManager.playerSpeed = sp
                        exoPlayer.setPlaybackSpeed(sp)
                    },
                    currentServerName = currentServerName,
                    servers = servers,
                    onServerSelected = { server ->
                        val currentPos = currentPlayer.currentPosition.coerceAtLeast(0L)
                        pendingSeekPosition = currentPos
                        currentQualities = server.qualities
                        currentSubtitles = server.subtitles
                        currentServerName = server.name
                        val best = server.qualities.firstOrNull { it.qualityLabel == "720p" }
                            ?: server.qualities.maxByOrNull { it.qualityLabel.filter(Char::isDigit).toIntOrNull() ?: 0 }
                        currentUrl = best?.url ?: server.embedUrl
                        currentQualityLabel = best?.qualityLabel ?: "Auto"

                        val newHeaders = mutableMapOf<String, String>()
                        newHeaders.putAll(initialHeaders)
                        newHeaders.putAll(server.headers)
                        if (best != null && best.headers.isNotEmpty()) {
                            newHeaders.putAll(best.headers)
                        }
                        currentHeaders = newHeaders
                        currentDrmLicenseUrl = best?.drmLicenseUrl ?: server.drmLicenseUrl ?: initialDrmLicenseUrl
                        currentDrmHeaders = best?.drmHeaders?.ifEmpty { null } ?: server.drmHeaders.ifEmpty { null } ?: initialDrmHeaders
                    },
                    currentQualityLabel = currentQualityLabel,
                    qualities = currentQualities,
                    hlsQualityOptions = hlsQualityOptions,
                    onQualitySelected = { q ->
                        val currentPos = currentPlayer.currentPosition.coerceAtLeast(0L)
                        pendingSeekPosition = currentPos
                        currentUrl = q.url
                        currentQualityLabel = q.qualityLabel
                        if (q.headers.isNotEmpty()) {
                            val newHeaders = currentHeaders.toMutableMap()
                            newHeaders.putAll(q.headers)
                            currentHeaders = newHeaders
                        }
                        currentDrmLicenseUrl = q.drmLicenseUrl ?: currentDrmLicenseUrl
                        if (q.drmHeaders.isNotEmpty()) {
                            currentDrmHeaders = q.drmHeaders
                        }
                    },
                    onHlsQualitySelected = { opt ->
                        TrackSelectorHelper.applyVideoQuality(trackSelector, opt)
                        currentQualityLabel = if (opt.isAuto) "Auto" else opt.label.split(" ").first()
                    },
                    exoTracks = exoTracks,
                    trackSelector = trackSelector,
                    currentSubtitles = currentSubtitles,
                    activeSubtitleText = activeSubtitleText,
                    subtitleFontSize = subtitleFontSize,
                    subtitleTextColor = subtitleTextColor,
                    subtitleBgColor = subtitleBgColor,
                    skipTimes = skipTimes,
                    autoSkipOpEd = autoSkipOpEd,
                    onToggleAutoSkip = onToggleAutoSkip,
                    onBack = onBack,
                    onShowSubtitleDialog = { showSubtitleDialog = true },
                    onShowBrightnessDialog = { showBrightnessDialog = true },
                    onShowShaderDialog = { showShaderDialog = true },
                    onShowWatchPartyMenu = { showWatchPartyChoiceDialog = true },
                    currentAspectRatioMode = currentAspectRatioMode,
                    onAspectRatioChange = { mode ->
                        currentAspectRatioMode = mode
                        playerViewRef?.resizeMode = mode.resizeMode
                    },
                    isAudioOnly = isAudioOnlyMode,
                    onToggleAudioOnly = { toggleAudioOnlyMode() },
                    onEnterPip = triggerPip
                )
            }
        }

        // 3. Auto-Next Episode Countdown Overlay
        AutoNextEpisodeOverlay(
            visible = showAutoNextCountdown,
            nextEpisodeTitle = nextEpisodeTitle,
            countdownSeconds = autoNextCountdownSeconds,
            totalSeconds = 5,
            isCornerOverlay = true,
            onPlayNow = {
                showAutoNextCountdown = false
                currentOnNextEpisode()
            },
            onCancel = {
                isAutoNextCancelled = true
                showAutoNextCountdown = false
            }
        )

        if (isFailoverResolving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1E1E1E).copy(alpha = 0.95f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color(0xFFE50914),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = failoverMessage ?: "Menyambungkan kembali streaming...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Watch Party Status Chip (Top-Left)
        if (watchPartyController.role != WatchPartyRole.NONE) {
            WatchPartyStatusChip(
                role = watchPartyController.role,
                roomName = watchPartyController.currentRoom?.roomName ?: "Watch Party",
                peersCount = watchPartyPeersCount,
                pingMs = watchPartyPingMs,
                onLeaveClick = { watchPartyController.leaveRoom() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 60.dp)
            )

            // Watch Party Chat & Emoji Reactions Overlay (Bottom-Left)
            WatchPartyChatOverlay(
                visible = true,
                chatMessages = watchPartyChatMessages,
                onSendEmoji = { emoji ->
                    watchPartyController.sendEmojiReaction(emoji, if (watchPartyController.role == WatchPartyRole.HOST) "Host" else "Peer")
                },
                onSendMessage = { msg ->
                    watchPartyController.sendChatMessage(msg, if (watchPartyController.role == WatchPartyRole.HOST) "Host" else "Peer")
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 80.dp)
            )
        }
    }

    // Dialogs
    SubtitleCustomizationDialog(
        showDialog = showSubtitleDialog,
        onDismissRequest = { showSubtitleDialog = false },
        subtitleFontSize = subtitleFontSize,
        onFontSizeChange = { size ->
            subtitleFontSize = size
            settingsManager.subtitleFontSize = size
        },
        subtitleTextColor = subtitleTextColor,
        onTextColorChange = { color ->
            subtitleTextColor = color
            settingsManager.subtitleTextColor = color
        },
        subtitleBgColor = subtitleBgColor,
        onBgColorChange = { bgColor ->
            subtitleBgColor = bgColor
            settingsManager.subtitleBgColor = bgColor
        },
        subtitleOffsetMs = subtitleOffsetMs,
        onOffsetChange = { offset -> subtitleOffsetMs = offset },
        backgroundAudioEnabled = backgroundAudioEnabled,
        onBackgroundAudioChange = { enabled ->
            backgroundAudioEnabled = enabled
            settingsManager.backgroundAudio = enabled
        }
    )

    BrightnessDialog(
        showDialog = showBrightnessDialog,
        onDismissRequest = { showBrightnessDialog = false },
        currentBrightness = customBrightness,
        onBrightnessChange = { newBright ->
            customBrightness = newBright
            settingsManager.customBrightness = newBright
        }
    )

    ShaderCustomizationDialog(
        showDialog = showShaderDialog,
        onDismissRequest = { showShaderDialog = false },
        currentMode = currentShaderMode,
        currentIntensity = currentShaderIntensity,
        onShaderModeSelected = { mode, intensity ->
            currentShaderMode = mode
            currentShaderIntensity = intensity
            AnimeShaderManager.saveAndApplyShaderEffect(context, exoPlayer, mode, intensity)
        }
    )

    // Watch Party Choice Selector (Host vs Join)
    if (showWatchPartyChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showWatchPartyChoiceDialog = false },
            icon = { Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("P2P LAN Watch Party (Co-Watching)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Tonton anime/drama bersama secara offline di jaringan Wi-Fi lokal tanpa jeda latensi (<50ms).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showWatchPartyChoiceDialog = false
                            showWatchPartyHostDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mulai Ruang Baru (Host)")
                    }
                    OutlinedButton(
                        onClick = {
                            showWatchPartyChoiceDialog = false
                            watchPartyController.startDiscovering()
                            showWatchPartyJoinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Gabung Ruang Aktif (Join)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWatchPartyChoiceDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    WatchPartyHostDialog(
        showDialog = showWatchPartyHostDialog,
        onDismissRequest = { showWatchPartyHostDialog = false },
        mediaTitle = title,
        onConfirmHost = { roomName, hostName ->
            watchPartyController.startHosting(
                roomName = roomName,
                hostName = hostName,
                mediaTitle = title,
                episodeTitle = episodeTitle,
                mediaUrl = currentUrl
            )
        }
    )

    WatchPartyJoinDialog(
        showDialog = showWatchPartyJoinDialog,
        onDismissRequest = {
            showWatchPartyJoinDialog = false
            watchPartyController.stopDiscovering()
        },
        activeRooms = watchPartyActiveRooms,
        onRefreshRooms = { watchPartyController.startDiscovering() },
        onJoinRoom = { room, peerName ->
            watchPartyController.joinRoom(room, peerName)
        }
    )
}
