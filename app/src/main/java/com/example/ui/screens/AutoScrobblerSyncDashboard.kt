package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.OtakuHubApp
import com.example.data.ProfileManager
import com.example.data.scrobbler.ScrobblePlatform
import com.example.data.scrobbler.ScrobbleSyncWorker
import com.example.data.scrobbler.TwoWaySyncSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScrobblerSyncDashboard(
    onNavigateBack: () -> Unit,
    onConnectPlatform: (ScrobblePlatform) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as? OtakuHubApp ?: OtakuHubApp.instance
    val secureStorage = app.secureCredentialStore
    val scrobblerEngine = app.scrobblerEngine
    val syncEngine = app.twoWaySyncEngine
    val settingsManager = app.settingsManager

    var autoSyncEnabled by remember { mutableStateOf(settingsManager.autoTrackerSync) }
    var thresholdPercent by remember { mutableStateOf((scrobblerEngine.config.completionThreshold * 100).toInt()) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncSummary by remember { mutableStateOf<TwoWaySyncSummary?>(null) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }

    // Platform login states
    var isMalLogged by remember { mutableStateOf(secureStorage.isLogged(ScrobblePlatform.MY_ANIME_LIST)) }
    var isAniListLogged by remember { mutableStateOf(secureStorage.isLogged(ScrobblePlatform.ANI_LIST)) }
    var isKitsuLogged by remember { mutableStateOf(secureStorage.isLogged(ScrobblePlatform.KITSU)) }

    fun refreshAuthStates() {
        isMalLogged = secureStorage.isLogged(ScrobblePlatform.MY_ANIME_LIST)
        isAniListLogged = secureStorage.isLogged(ScrobblePlatform.ANI_LIST)
        isKitsuLogged = secureStorage.isLogged(ScrobblePlatform.KITSU)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Auto-Scrobbler & Sync Engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Multi-Service Tracker Synchronization",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            refreshAuthStates()
                            Toast.makeText(context, "Status akun diperbarui", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (autoSyncEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = if (autoSyncEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (autoSyncEnabled) "Auto-Scrobbler Aktif" else "Auto-Scrobbler Dinonaktifkan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (autoSyncEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Deteksi batas ketuntasan pemutaran & sinkronisasi dua arah otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoSyncEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = {
                            autoSyncEnabled = it
                            settingsManager.autoTrackerSync = it
                            if (it) {
                                ScrobbleSyncWorker.schedulePeriodic(context, 15L)
                            }
                        }
                    )
                }
            }

            // Completion Threshold Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Batas Ketuntasan (Completion Threshold)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Standar Formula: ScrobbleTrigger = TRUE jika Progress >= $thresholdPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$thresholdPercent%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Slider(
                        value = thresholdPercent.toFloat(),
                        onValueChange = {
                            thresholdPercent = it.toInt()
                            scrobblerEngine.config = scrobblerEngine.config.copy(
                                completionThreshold = it.toDouble() / 100.0
                            )
                        },
                        valueRange = 50f..95f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50% (Awal)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("85% (Rekomendasi Resmi)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("95% (Hampir Selesai)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Connected Tracking Platforms List
            Text(
                text = "Platform Sinkronisasi Terintegrasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // MAL Card
            TrackerPlatformCard(
                platform = ScrobblePlatform.MY_ANIME_LIST,
                isConnected = isMalLogged,
                badgeColor = Color(0xFF2E51A2),
                icon = Icons.Default.Person,
                onConnect = { onConnectPlatform(ScrobblePlatform.MY_ANIME_LIST) },
                onDisconnect = {
                    secureStorage.logout(ScrobblePlatform.MY_ANIME_LIST)
                    refreshAuthStates()
                    Toast.makeText(context, "MyAnimeList diputuskan", Toast.LENGTH_SHORT).show()
                }
            )

            // AniList Card
            TrackerPlatformCard(
                platform = ScrobblePlatform.ANI_LIST,
                isConnected = isAniListLogged,
                badgeColor = Color(0xFF02A9FF),
                icon = Icons.AutoMirrored.Filled.List,
                onConnect = { onConnectPlatform(ScrobblePlatform.ANI_LIST) },
                onDisconnect = {
                    secureStorage.logout(ScrobblePlatform.ANI_LIST)
                    refreshAuthStates()
                    Toast.makeText(context, "AniList diputuskan", Toast.LENGTH_SHORT).show()
                }
            )

            // Kitsu Card
            TrackerPlatformCard(
                platform = ScrobblePlatform.KITSU,
                isConnected = isKitsuLogged,
                badgeColor = Color(0xFFE25A3D),
                icon = Icons.Default.Sync,
                onConnect = { onConnectPlatform(ScrobblePlatform.KITSU) },
                onDisconnect = {
                    secureStorage.logout(ScrobblePlatform.KITSU)
                    refreshAuthStates()
                    Toast.makeText(context, "Kitsu diputuskan", Toast.LENGTH_SHORT).show()
                }
            )

            // Two-Way Sync Actions
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Operasi Two-Way Status Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            syncErrorMessage = null
                            try {
                                val profileId = ProfileManager.currentProfileIdValue
                                val summary = syncEngine.syncAll(profileId)
                                lastSyncSummary = summary
                                Toast.makeText(
                                    context,
                                    "Sinkronisasi selesai: ${summary.totalImported} impor, ${summary.totalUpdated} update",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                syncErrorMessage = e.message ?: "Gagal sinkronisasi"
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSyncing && (isMalLogged || isAniListLogged || isKitsuLogged)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menyinkronkan...")
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Dua Arah Sekarang")
                    }
                }
            }

            // Sync Results Summary Card
            lastSyncSummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Hasil Sinkronisasi Terakhir",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Item Diimpor:", style = MaterialTheme.typography.bodyMedium)
                            Text("${summary.totalImported}", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Status Diperbarui:", style = MaterialTheme.typography.bodyMedium)
                            Text("${summary.totalUpdated}", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Progres Didorong:", style = MaterialTheme.typography.bodyMedium)
                            Text("${summary.totalPushed}", fontWeight = FontWeight.Bold)
                        }
                        for (res in summary.results) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(res.platform.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    if (res.success) "Berhasil" else "Gagal",
                                    color = if (res.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            syncErrorMessage?.let { err ->
                Text(
                    text = "Kesalahan: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun TrackerPlatformCard(
    platform: ScrobblePlatform,
    isConnected: Boolean,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = platform.displayName, tint = badgeColor)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${platform.protocol} • ${platform.apiType}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isConnected) "Status: Terhubung & Aktif" else "Status: Belum Terhubung",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (isConnected) {
                FilledTonalButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Keluar")
                }
            } else {
                Button(onClick = onConnect) {
                    Text("Hubungkan")
                }
            }
        }
    }
}
