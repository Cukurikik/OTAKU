package com.example.ui.download

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.download.compressor.CompressionProfile
import com.example.download.compressor.StorageSavedStats
import com.example.download.compressor.StorageSpaceSaverManager
import com.example.download.compressor.VideoStorageItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSpaceSaverDashboard(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val videoItems by StorageSpaceSaverManager.videoItems.collectAsState()
    val stats by StorageSpaceSaverManager.stats.collectAsState()
    val selectedProfile by StorageSpaceSaverManager.selectedProfile.collectAsState()

    var showConfirmBatchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        StorageSpaceSaverManager.refreshStorageData(context)
    }

    Scaffold(
        modifier = Modifier.testTag("storage_space_saver_dashboard"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Storage Space Saver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "FFmpeg HEVC/H.265 Hardware Transcoder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("space_saver_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                StorageSpaceSaverManager.refreshStorageData(context)
                                snackbarHostState.showSnackbar("Data penyimpanan diperbarui")
                            }
                        },
                        modifier = Modifier.testTag("space_saver_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Card: Storage Saved Overview
            item {
                StorageOverviewCard(
                    stats = stats,
                    onBatchCompress = { showConfirmBatchDialog = true }
                )
            }

            // Compression Profile Selector
            item {
                CompressionProfileSelectorCard(
                    selectedProfile = selectedProfile,
                    onSelectProfile = { StorageSpaceSaverManager.setSelectedProfile(it) }
                )
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Berkas Video Offline (${videoItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stats.compressedVideosCount} Terkompresi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Video Items List
            if (videoItems.isEmpty()) {
                item {
                    EmptyVideoStorageCard()
                }
            } else {
                items(videoItems, key = { it.id }) { item ->
                    VideoStorageItemCard(
                        item = item,
                        currentProfile = selectedProfile,
                        onCompress = {
                            StorageSpaceSaverManager.enqueueCompression(
                                context = context,
                                filePath = item.filePath,
                                title = item.title,
                                profile = selectedProfile
                            )
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Kompresi '${item.title}' dimasukkan ke antrean latar belakang")
                                StorageSpaceSaverManager.refreshStorageData(context)
                            }
                        }
                    )
                }
            }

            // Technical details footer
            item {
                TechnicalEngineCard()
            }
        }
    }

    if (showConfirmBatchDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBatchDialog = false },
            icon = { Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Kompres Semua Video?") },
            text = {
                Text(
                    "Aplikasi akan mengonversi ${stats.uncompressedVideosCount} video mentah ke format ${selectedProfile.codecBadge}.\n\n" +
                            "Proses berjalan di latar belakang (WorkManager) dan menghemat estimasi hingga ${formatBytes(stats.potentialSavingsBytes)} kapasitas penyimpanan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmBatchDialog = false
                        StorageSpaceSaverManager.enqueueBatchCompression(context, selectedProfile)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Memulai kompresi batch untuk ${stats.uncompressedVideosCount} video")
                            StorageSpaceSaverManager.refreshStorageData(context)
                        }
                    },
                    modifier = Modifier.testTag("confirm_batch_compression_button")
                ) {
                    Text("Mulai Kompresi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBatchDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun StorageOverviewCard(
    stats: StorageSavedStats,
    onBatchCompress: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Kapasitas Penyimpanan Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${stats.totalVideosCount} video offline terdeteksi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (stats.totalSavedBytes > 0L) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Hemat %.1f%%".format(stats.overallSavingsPercentage),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(
                    title = "Total Video",
                    value = formatBytes(stats.totalVideoBytes),
                    icon = Icons.Outlined.VideoLibrary
                )
                MetricColumn(
                    title = "Ruang Dihemat",
                    value = formatBytes(stats.totalSavedBytes),
                    icon = Icons.Outlined.Savings,
                    highlight = true
                )
                MetricColumn(
                    title = "Potensi Tambahan",
                    value = formatBytes(stats.potentialSavingsBytes),
                    icon = Icons.Outlined.Compress
                )
            }

            // Quick Batch Action Button
            if (stats.uncompressedVideosCount > 0) {
                Button(
                    onClick = onBatchCompress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_compress_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Kompres Semua (${stats.uncompressedVideosCount} Video)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MetricColumn(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CompressionProfileSelectorCard(
    selectedProfile: CompressionProfile,
    onSelectProfile: (CompressionProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Profil Kompresi FFmpeg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            CompressionProfile.values().forEach { profile ->
                val isSelected = profile == selectedProfile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectProfile(profile) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("profile_${profile.id}"),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (profile == CompressionProfile.SPACE_SAVER) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Disarankan",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${profile.codecBadge} • ${profile.qualityLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectProfile(profile) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoStorageItemCard(
    item: VideoStorageItem,
    currentProfile: CompressionProfile,
    onCompress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ukuran saat ini: ${formatBytes(item.currentBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.isCompressed) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "HEVC (-${formatBytes(item.savedBytes)})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onCompress,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("compress_btn_${item.id}")
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Kompres",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Estimation subtitle if not compressed
            if (!item.isCompressed) {
                val potentialSaved = currentProfile.calculateEstimatedSavedBytes(item.currentBytes)
                Text(
                    text = "Estimasi penghematan: ~${formatBytes(potentialSaved)} (${currentProfile.savingsPercentageText}) dengan ${currentProfile.codecBadge}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TechnicalEngineCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Informasi Mesin Transcoder",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Menggunakan dev.ffmpegkit-maintained:ffmpeg-kit-full dengan encoder x265 (CRF 28) dan Opus Audio (96kbps). Seluruh kompresi diproses secara 100% lokal pada perangkat tanpa membutuhkan server luar atau koneksi internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun EmptyVideoStorageCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.MovieFilter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "Tidak ada video offline yang ditemukan di direktori OtakuHub",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
