package com.example.ui.download

import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.models.DownloadEntity
import com.example.download.compressor.CompressionProfile
import com.example.download.compressor.StorageSpaceSaverManager
import com.example.download.resiliency.HlsChunkVerifier
import com.example.ui.viewmodels.DownloadItemUiState
import com.example.ui.viewmodels.DownloadViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class DownloadTabFilter(val label: String) {
    ALL("Semua"),
    ACTIVE("Aktif & Antrean"),
    COMPLETED("Selesai"),
    FAILED("Gagal")
}

enum class MediaTypeFilter(val label: String) {
    ALL("Semua Media"),
    VIDEO("Anime & Drama"),
    COMIC("Komik & Manga")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDashboardScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    onOpenMedia: ((DownloadEntity, Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloads by viewModel.uiDownloads.collectAsState()

    var selectedTab by remember { mutableStateOf(DownloadTabFilter.ALL) }
    var selectedMediaType by remember { mutableStateOf(MediaTypeFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    var selectedItemForDetail by remember { mutableStateOf<DownloadItemUiState?>(null) }
    var pendingExportEntity by remember { mutableStateOf<DownloadEntity?>(null) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showStorageSpaceSaver by remember { mutableStateOf(false) }

    // SAF Launchers
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val entity = pendingExportEntity
        if (uri != null && entity != null) {
            viewModel.exportMediaToSaf(context, entity, uri) { _, msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
        pendingExportEntity = null
    }

    val importComicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMediaFromSaf(context, uri, isVideo = false) { _, msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMediaFromSaf(context, uri, isVideo = true) { _, msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    // Filter Logic
    val filteredDownloads = remember(downloads, selectedTab, selectedMediaType, searchQuery) {
        downloads.filter { item ->
            val matchTab = when (selectedTab) {
                DownloadTabFilter.ALL -> true
                DownloadTabFilter.ACTIVE -> item.entity.state == "DOWNLOADING" || item.entity.state == "QUEUE" || item.entity.state == "PAUSED"
                DownloadTabFilter.COMPLETED -> item.entity.state == "DOWNLOADED"
                DownloadTabFilter.FAILED -> item.entity.state == "ERROR"
            }

            val matchMedia = when (selectedMediaType) {
                MediaTypeFilter.ALL -> true
                MediaTypeFilter.VIDEO -> item.isVideo
                MediaTypeFilter.COMIC -> !item.isVideo
            }

            val matchSearch = if (searchQuery.isBlank()) true else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                        item.chapterTitle.contains(searchQuery, ignoreCase = true) ||
                        item.entity.sourceId.contains(searchQuery, ignoreCase = true)
            }

            matchTab && matchMedia && matchSearch
        }
    }

    // Aggregate statistics
    val activeCount = downloads.count { it.entity.state == "DOWNLOADING" || it.entity.state == "QUEUE" }
    val completedCount = downloads.count { it.entity.state == "DOWNLOADED" }
    val totalSpeedKbps = downloads.filter { it.entity.state == "DOWNLOADING" }.sumOf { it.downloadSpeedKbps }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (showSearchBar) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari unduhan...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text("Download Center", fontWeight = FontWeight.Bold)
                            Text(
                                "M3U8 Resiliency & Multi-Source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) searchQuery = ""
                    }) {
                        Icon(
                            if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Cari"
                        )
                    }

                    IconButton(onClick = { showStorageSpaceSaver = true }) {
                        Icon(
                            Icons.Default.SdStorage,
                            contentDescription = "Storage Space Saver (HEVC)"
                        )
                    }

                    // Import / Export SAF Menu
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opsi Lainnya")
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Storage Space Saver (HEVC)") },
                                leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    showStorageSpaceSaver = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Jeda Semua Unduhan") },
                                leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    viewModel.pauseAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lanjutkan Semua") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    viewModel.resumeAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bersihkan yang Selesai") },
                                leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    viewModel.clearCompletedDownloads()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Impor Komik (.cbz/.zip)") },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importComicLauncher.launch(arrayOf("application/zip", "application/x-cbz", "application/octet-stream"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Impor Video (.mp4/.mkv)") },
                                leadingIcon = { Icon(Icons.Default.VideoFile, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importVideoLauncher.launch(arrayOf("video/mp4", "video/x-matroska", "video/*"))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Dashboard Card
            item {
                DownloadSummaryCard(
                    activeCount = activeCount,
                    completedCount = completedCount,
                    totalSpeedKbps = totalSpeedKbps,
                    onPauseAll = { viewModel.pauseAll() },
                    onResumeAll = { viewModel.resumeAll() },
                    onOpenSpaceSaver = { showStorageSpaceSaver = true }
                )
            }

            // Tab Filters
            item {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DownloadTabFilter.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }

            // Media Type Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(MediaTypeFilter.values()) { type ->
                        FilterChip(
                            selected = selectedMediaType == type,
                            onClick = { selectedMediaType = type },
                            label = { Text(type.label) },
                            leadingIcon = if (selectedMediaType == type) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Items List
            if (filteredDownloads.isEmpty()) {
                item {
                    EmptyDownloadPlaceholder(selectedTab)
                }
            } else {
                items(
                    items = filteredDownloads,
                    key = { "${it.entity.sourceId}||${it.entity.mangaId}||${it.entity.chapterId}" }
                ) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { viewModel.pauseDownload(item.entity) },
                        onResume = { viewModel.resumeDownload(item.entity) },
                        onRetry = { viewModel.retryDownload(item.entity) },
                        onCancel = { viewModel.cancelDownload(item.entity) },
                        onExport = {
                            if (item.isVideo) {
                                viewModel.exportVideoToSystemGallery(context, item.entity) { _, msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            } else {
                                val cleanTitle = "${item.title}_${item.chapterTitle}".replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                pendingExportEntity = item.entity
                                exportLauncher.launch("$cleanTitle.cbz")
                            }
                        },
                        onDetail = { selectedItemForDetail = item },
                        onOpen = {
                            onOpenMedia?.invoke(item.entity, item.isVideo)
                        },
                        onCompress = if (item.isVideo) {
                            {
                                val movieFile = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub/${item.entity.mangaId}-${item.entity.chapterId}.mp4")
                                if (movieFile.exists()) {
                                    StorageSpaceSaverManager.enqueueCompression(
                                        context = context,
                                        filePath = movieFile.absolutePath,
                                        title = "${item.title} - ${item.chapterTitle}"
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Kompresi HEVC untuk '${item.title}' dimulai di latar belakang")
                                    }
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Berkas video belum siap dikompresi")
                                    }
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    // Detail & Chunk Resiliency Dialog
    if (selectedItemForDetail != null) {
        val item = selectedItemForDetail!!
        ChunkResiliencyDetailDialog(
            item = item,
            onDismiss = { selectedItemForDetail = null }
        )
    }

    // Storage Space Saver Dashboard Dialog
    if (showStorageSpaceSaver) {
        Dialog(
            onDismissRequest = { showStorageSpaceSaver = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StorageSpaceSaverDashboard(
                onDismiss = { showStorageSpaceSaver = false }
            )
        }
    }
}

@Composable
fun DownloadSummaryCard(
    activeCount: Int,
    completedCount: Int,
    totalSpeedKbps: Long,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onOpenSpaceSaver: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Status Pengunduhan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (activeCount > 0) "$activeCount berkas sedang diproses" else "Tidak ada unduhan aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (totalSpeedKbps > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = formatSpeed(totalSpeedKbps),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Aktif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$activeCount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Column {
                        Text("Selesai", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$completedCount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOpenSpaceSaver,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hemat Ruang", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = onPauseAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Jeda Semua", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onResumeAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lanjutkan", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadItemUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onDetail: () -> Unit,
    onOpen: () -> Unit,
    onCompress: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (item.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            if (item.isVideo) Icons.Default.Movie else Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Media Type Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(2.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = if (item.isVideo) "VIDEO" else "KOMIK",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // State Tag
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(state = item.entity.state, errorMessage = item.entity.errorMessage)
                        if (item.downloadSpeedKbps > 0 && item.entity.state == "DOWNLOADING") {
                            Text(
                                text = "• ${formatSpeed(item.downloadSpeedKbps)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Quick Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (item.entity.state) {
                        "DOWNLOADING", "QUEUE" -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause, contentDescription = "Jeda")
                            }
                        }
                        "PAUSED" -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan")
                            }
                        }
                        "ERROR" -> {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Coba Lagi")
                            }
                        }
                        "DOWNLOADED" -> {
                            IconButton(onClick = onOpen) {
                                Icon(if (item.isVideo) Icons.Default.PlayCircle else Icons.Default.AutoStories, contentDescription = "Buka")
                            }
                            if (item.isVideo && onCompress != null) {
                                IconButton(onClick = onCompress) {
                                    Icon(Icons.Default.Compress, contentDescription = "Kompres ke HEVC")
                                }
                            }
                            IconButton(onClick = onExport) {
                                Icon(if (item.isVideo) Icons.Default.IosShare else Icons.Default.FileUpload, contentDescription = "Ekspor ke Galeri (FFmpeg)")
                            }
                        }
                    }

                    IconButton(onClick = onDetail) {
                        Icon(Icons.Outlined.Info, contentDescription = "Detail & Integritas")
                    }
                }
            }

            // Progress Bar (if active or paused)
            if (item.entity.state == "DOWNLOADING" || item.entity.state == "QUEUE" || item.entity.state == "PAUSED") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.downloadedBytes > 0) {
                            Text(
                                text = "${formatBytes(item.downloadedBytes)} / ${if (item.totalBytes > 0) formatBytes(item.totalBytes) else "..."}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(state: String, errorMessage: String?) {
    val (bgColor, textColor, text) = when (state) {
        "DOWNLOADING" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Mengunduh")
        "QUEUE" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Antrean")
        "PAUSED" -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, "Dijeda")
        "DOWNLOADED" -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Selesai")
        "ERROR" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Gagal")
        else -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, state)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ChunkResiliencyDetailDialog(
    item: DownloadItemUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var checksumHash by remember { mutableStateOf<String?>("Menghitung...") }

    // Hitung checksum secara asynchronous jika file lokal tersedia
    LaunchedEffect(item) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = if (item.isVideo) {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "OtakuHub/${item.entity.mangaId}-${item.entity.chapterId}.mp4")
            } else {
                File(context.filesDir, "downloads/${item.entity.sourceId}/${item.entity.mangaId}/${item.entity.chapterId}.cbz")
            }
            if (file.exists() && file.length() > 0) {
                checksumHash = HlsChunkVerifier.calculateChecksum(file)
            } else {
                checksumHash = "File belum tersedia / Masih dalam proses"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Integritas & Metadata Chunk", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Judul", value = item.title)
                DetailRow(label = "Chapter/Episode", value = item.chapterTitle)
                DetailRow(label = "Sumber ID", value = item.entity.sourceId)
                DetailRow(label = "Status", value = item.entity.state)
                DetailRow(label = "MIME / Format", value = item.entity.mimeType ?: if (item.isVideo) "video/mp4 (HLS)" else "application/x-cbz")
                
                if (!item.entity.directUrl.isNullOrBlank()) {
                    DetailRow(label = "Stream URL", value = item.entity.directUrl!!)
                }

                if (!item.entity.errorMessage.isNullOrBlank()) {
                    DetailRow(label = "Detail Error", value = item.entity.errorMessage!!, isError = true)
                }

                HorizontalDivider()
                Text("Verifikasi Integritas Data (Resiliency)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    text = "Checksum SHA-256:\n$checksumHash",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, isError: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyDownloadPlaceholder(tab: DownloadTabFilter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.DownloadDone,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = when (tab) {
                    DownloadTabFilter.ALL -> "Belum ada unduhan di antrean"
                    DownloadTabFilter.ACTIVE -> "Tidak ada unduhan yang sedang berjalan"
                    DownloadTabFilter.COMPLETED -> "Belum ada file yang selesai diunduh"
                    DownloadTabFilter.FAILED -> "Tidak ada riwayat unduhan yang gagal"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatSpeed(speedKbps: Long): String {
    return if (speedKbps >= 1024) {
        "%.1f MB/s".format(speedKbps / 1024f)
    } else {
        "$speedKbps KB/s"
    }
}

fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1f -> "%.1f GB".format(gb)
        mb >= 1f -> "%.1f MB".format(mb)
        kb >= 1f -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}
