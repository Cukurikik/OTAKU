package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.DownloadEntity
import com.example.ui.viewmodels.DownloadItemUiState
import com.example.ui.viewmodels.DownloadViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    onCancel: (com.example.data.models.DownloadEntity) -> Unit,
    onPause: (com.example.data.models.DownloadEntity) -> Unit,
    onResume: (com.example.data.models.DownloadEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloads by viewModel.uiDownloads.collectAsState()

    var pendingExportEntity by remember { mutableStateOf<DownloadEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val entity = pendingExportEntity
        if (uri != null && entity != null) {
            viewModel.exportMediaToSaf(context, entity, uri) { success, msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
        pendingExportEntity = null
    }

    val importComicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMediaFromSaf(context, uri, isVideo = false) { success, msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMediaFromSaf(context, uri, isVideo = true) { success, msg ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    var showImportMenu by remember { mutableStateOf(false) }

    // Default to handling our own actions but delegate if provided
    val handleCancel = { download: DownloadEntity -> viewModel.cancelDownload(download) }
    val handlePause = { download: DownloadEntity -> viewModel.pauseDownload(download) }
    val handleResume = { download: DownloadEntity -> viewModel.resumeDownload(download) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Antrean Unduhan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Impor Media")
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Impor Komik (.cbz / .zip)") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importComicLauncher.launch(arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Impor Video (.mp4 / .mkv)") },
                                leadingIcon = { Icon(Icons.Default.OndemandVideo, contentDescription = null) },
                                onClick = {
                                    showImportMenu = false
                                    importVideoLauncher.launch(arrayOf("video/*", "application/octet-stream", "*/*"))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Cancel, 
                        contentDescription = "Kosong", 
                        modifier = Modifier.size(64.dp), 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Tidak ada unduhan aktif", 
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(downloads, key = { "${it.entity.sourceId}_${it.entity.mangaId}_${it.entity.chapterId}" }) { download ->
                    DownloadItemCard(
                        uiState = download,
                        onCancel = { handleCancel(download.entity) },
                        onPause = { handlePause(download.entity) },
                        onResume = { handleResume(download.entity) },
                        onExport = {
                            pendingExportEntity = download.entity
                            val defaultName = if (download.isVideo) {
                                "${download.title}_${download.chapterTitle}.mp4"
                            } else {
                                "${download.title}_${download.chapterTitle}.cbz"
                            }.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                            exportLauncher.launch(defaultName)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    uiState: DownloadItemUiState, 
    onCancel: () -> Unit, 
    onPause: () -> Unit, 
    onResume: () -> Unit,
    onExport: () -> Unit = {}
) {
    val entity = uiState.entity
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = uiState.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp, 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Title and Media Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.isVideo) Icons.Default.OndemandVideo else Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = uiState.title, 
                        style = MaterialTheme.typography.titleSmall, 
                        fontWeight = FontWeight.Bold, 
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = uiState.chapterTitle, 
                    style = MaterialTheme.typography.bodySmall, 
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress Bar
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (entity.state == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // State Text
                val stateText = when (entity.state) {
                    "QUEUE" -> "Menunggu antrean (WorkManager Backoff)"
                    "DOWNLOADING" -> "Mengunduh... ${(uiState.progress * 100).toInt()}%"
                    "ERROR" -> "Gagal - Menunggu retry otomatis..."
                    "DOWNLOADED" -> "Selesai"
                    "PAUSED" -> "Dijeda"
                    else -> entity.state
                }
                
                val sizeText = if (uiState.totalBytes > 0) {
                    val downloadedMb = uiState.downloadedBytes / (1024 * 1024f)
                    val totalMb = uiState.totalBytes / (1024 * 1024f)
                    " - %.1f MB / %.1f MB".format(downloadedMb, totalMb)
                } else ""
                
                val speedText = if (entity.state == "DOWNLOADING" && uiState.downloadSpeedKbps > 0) {
                    " (${uiState.downloadSpeedKbps} KB/s)"
                } else ""
                
                Text(
                    text = stateText + sizeText + speedText, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = if (entity.state == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Action Buttons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (entity.state == "DOWNLOADED") {
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Ekspor SAF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (entity.state == "DOWNLOADING" || entity.state == "QUEUE") {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Jeda", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (entity.state == "PAUSED" || entity.state == "ERROR") {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                val isFinished = entity.state == "DOWNLOADED"
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = if (isFinished) Icons.Default.Delete else Icons.Default.Cancel, 
                        contentDescription = if (isFinished) "Hapus" else "Batal", 
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

