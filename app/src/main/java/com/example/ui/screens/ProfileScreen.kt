package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BackupManager
import com.example.data.SettingsManager
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.SourceStatusBus
import com.example.ui.viewmodels.ProfileViewModel
import com.example.ui.viewmodels.ProfileStats
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onManageExtensions: () -> Unit,
    onDownloadQueue: () -> Unit,
    onMigrateSource: () -> Unit,
    onLanShare: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit = {}
) {
    val context = LocalContext.current
    val backupManager = remember { BackupManager(context) }
    val scope = rememberCoroutineScope()
    
    val settingsManager = remember { SettingsManager(context) }
    var isIncognito by remember { mutableStateOf(settingsManager.isIncognito) }
    var isDynamicColor by remember { mutableStateOf(settingsManager.isDynamicColor) }
    var autoTrackerSync by remember { mutableStateOf(settingsManager.autoTrackerSync) }
    var autoSkipOpEd by remember { mutableStateOf(settingsManager.autoSkipOpEd) }
    val sourceErrors by SourceStatusBus.errors.collectAsState()
    var editingSourceId by remember { mutableStateOf<String?>(null) }
    
    val kitsuToken by viewModel.kitsuToken.collectAsState()
    val malToken by viewModel.malToken.collectAsState()
    val anilistToken by viewModel.anilistToken.collectAsState()

    var showKitsuLogin by remember { mutableStateOf(false) }
    var showAnilistLogin by remember { mutableStateOf(false) }
    var showMalLogin by remember { mutableStateOf(false) }
    var showLocalMediaDialog by remember { mutableStateOf(false) }
    var showAutoScrobblerDashboard by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupManager.createBackup(it)
                if (result.isSuccess) {
                    Toast.makeText(context, "Cadangan terenkripsi (.otakubackup) berhasil", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Cadangan gagal: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = backupManager.restoreBackup(it)
                if (result.isSuccess) {
                    Toast.makeText(context, "Restore berhasil. Mulai ulang aplikasi.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Restore gagal", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showKitsuLogin) {
        KitsuLoginDialog(
            onDismiss = { showKitsuLogin = false },
            onSave = { token, userId ->
                viewModel.saveKitsuToken(token, userId)
                showKitsuLogin = false
                Toast.makeText(context, "Kitsu berhasil terhubung!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAnilistLogin) {
        TrackerOAuthDialog(
            authUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=3596&response_type=token",
            redirectUri = "tachiyomi://anilist-auth",
            onTokenExtracted = { token ->
                viewModel.saveAnilistToken(token)
                showAnilistLogin = false
                Toast.makeText(context, "AniList berhasil terhubung!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAnilistLogin = false }
        )
    }

    if (showMalLogin) {
        MalOAuthDialog(
            onTokenExtracted = { accessToken, refreshToken, expiresIn ->
                viewModel.saveMalToken(accessToken, refreshToken, expiresIn)
                showMalLogin = false
                Toast.makeText(context, "MyAnimeList berhasil terhubung!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showMalLogin = false }
        )
    }

    if (showLocalMediaDialog) {
        LocalMediaManagerDialog(
            onDismissRequest = { showLocalMediaDialog = false },
            onRescanRequested = {
                Toast.makeText(context, "Memindai direktori media lokal...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAutoScrobblerDashboard) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAutoScrobblerDashboard = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AutoScrobblerSyncDashboard(
                onNavigateBack = { showAutoScrobblerDashboard = false },
                onConnectPlatform = { platform ->
                    when (platform) {
                        com.example.data.scrobbler.ScrobblePlatform.MY_ANIME_LIST -> showMalLogin = true
                        com.example.data.scrobbler.ScrobblePlatform.ANI_LIST -> showAnilistLogin = true
                        com.example.data.scrobbler.ScrobblePlatform.KITSU -> showKitsuLogin = true
                    }
                }
            )
        }
    }

    if (editingSourceId != null) {
        var newUrl by remember { mutableStateOf(SourceBaseUrlStore.get(context, editingSourceId!!, "")) }
        AlertDialog(
            onDismissRequest = { editingSourceId = null },
            title = { Text("Ubah Domain Sumber") },
            text = {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("URL Baru") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SourceBaseUrlStore.set(context, editingSourceId!!, newUrl)
                    SourceStatusBus.clear(editingSourceId!!) // Clear error
                    editingSourceId = null
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSourceId = null }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil & Pengaturan", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val stats by viewModel.stats.collectAsState()
            
            // Analytics Dashboard
            ProfileDashboard(stats = stats, onOpenFullStatistics = onOpenStatistics)

            // Source status warnings
            if (sourceErrors.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Status Sumber Bermasalah",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        sourceErrors.forEach { (sourceId, message) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { editingSourceId = sourceId }) {
                                    Text("Ganti URL")
                                }
                            }
                        }
                    }
                }
            }

            // Tracker Settings Section (New!)
            SectionTitle(title = "Auto-Scrobbler & Sinkronisasi Tracker")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showAutoScrobblerDashboard = true }),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Auto-Scrobbler Engine",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Multi-Service Auto-Scrobbler & Sync Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Threshold 85% completion & Two-Way Sync Watchlist/Reading List (MAL, AniList, Kitsu).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Kitsu
                    ListItem(
                        headlineContent = { Text("Kitsu", fontWeight = FontWeight.Medium) },
                        supportingContent = { 
                            Text(if (kitsuToken.isNotEmpty()) "Terhubung" else "Belum Terhubung") 
                        },
                        leadingContent = {
                            Icon(Icons.Default.Sync, contentDescription = "Kitsu", tint = if (kitsuToken.isNotEmpty()) Color(0xFFE25A3D) else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            if (kitsuToken.isNotEmpty()) {
                                TextButton(onClick = { viewModel.logoutKitsu() }) {
                                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Button(onClick = { showKitsuLogin = true }) {
                                    Text("Hubungkan")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // AniList
                    ListItem(
                        headlineContent = { Text("AniList", fontWeight = FontWeight.Medium) },
                        supportingContent = { 
                            Text(if (anilistToken.isNotEmpty()) "Terhubung" else "Belum Terhubung") 
                        },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "AniList", tint = if (anilistToken.isNotEmpty()) Color(0xFF02A9FF) else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            if (anilistToken.isNotEmpty()) {
                                TextButton(onClick = { viewModel.logoutAnilist() }) {
                                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Button(onClick = { showAnilistLogin = true }) {
                                    Text("Hubungkan")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // MyAnimeList
                    ListItem(
                        headlineContent = { Text("MyAnimeList", fontWeight = FontWeight.Medium) },
                        supportingContent = { 
                            Text(if (malToken.isNotEmpty()) "Terhubung" else "Belum Terhubung") 
                        },
                        leadingContent = {
                            Icon(Icons.Default.Person, contentDescription = "MAL", tint = if (malToken.isNotEmpty()) Color(0xFF2E51A2) else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = {
                            if (malToken.isNotEmpty()) {
                                TextButton(onClick = { viewModel.logoutMal() }) {
                                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Button(onClick = { showMalLogin = true }) {
                                    Text("Hubungkan")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    // Auto-Sync Switch
                    ListItem(
                        headlineContent = { Text("Auto-Sync Pelacak", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Sinkronisasi otomatis progres ke AniList/MAL/Kitsu saat episode/bab selesai (85%)") },
                        trailingContent = {
                            Switch(
                                checked = autoTrackerSync,
                                onCheckedChange = {
                                    autoTrackerSync = it
                                    settingsManager.autoTrackerSync = it
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Auto-Skip Switch
                    ListItem(
                        headlineContent = { Text("Auto-Skip OP/ED (AniSkip)", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Lewati Opening & Ending secara otomatis saat menonton anime") },
                        trailingContent = {
                            Switch(
                                checked = autoSkipOpEd,
                                onCheckedChange = {
                                    autoSkipOpEd = it
                                    settingsManager.autoSkipOpEd = it
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            SectionTitle(title = "Fitur & Pengelolaan")
            
            // Extension Manager Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageExtensions),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Ekstensi",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Kelola Ekstensi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Tambah, hapus, atau update sumber konten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // Download Queue Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLanShare),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "P2P LAN Share",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("P2P Local LAN Share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Bagikan/Unduh file via Wi-Fi lokal (Bebas Kuota).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Download Queue Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDownloadQueue),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Antrean Unduhan",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Antrean Unduhan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Lihat status unduhan bab atau video.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // Migration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMigrateSource),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Migrasi",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Migrasi Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("Pindahkan komik/anime ke sumber baru jika mati.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // Local Media SAF Manager Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showLocalMediaDialog = true }),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Media Lokal SAF",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Media Lokal (SAF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pasang folder Manga (CBZ/ZIP) & Anime (MP4/MKV) dari penyimpanan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionTitle(title = "Manajemen Data")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createDocumentLauncher.launch("OtakuHub_Backup_${System.currentTimeMillis()}.otakubackup") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = "Export Backup", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Data")
                }
                FilledTonalButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = "Import Backup", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import Data")
                }
            }

            SectionTitle(title = "Preferensi UI")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Mode Penyamaran (Incognito)") },
                        supportingContent = { Text("Jeda penyimpanan riwayat baca/tonton") },
                        trailingContent = {
                            Switch(
                                checked = isIncognito,
                                onCheckedChange = {
                                    isIncognito = it
                                    settingsManager.isIncognito = it
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Warna Dinamis (Material You)") },
                        supportingContent = { Text("Ikuti tema warna wallpaper sistem (Android 12+)") },
                        trailingContent = {
                            Switch(
                                checked = isDynamicColor,
                                onCheckedChange = {
                                    isDynamicColor = it
                                    settingsManager.isDynamicColor = it
                                    Toast.makeText(context, "Mulai ulang aplikasi untuk terapkan warna", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun KitsuLoginDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var token by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hubungkan Kitsu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Dapatkan Access Token dan User ID Anda dari dokumentasi API Kitsu atau ekstrak dari Web Inspector.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Access Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(token, userId) },
                enabled = token.isNotBlank() && userId.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun ProfileDashboard(stats: ProfileStats, onOpenFullStatistics: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Statistik Pengguna",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onOpenFullStatistics) {
                Text("Lihat Detail")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Waktu Tonton",
                value = formatWatchTime(stats.totalWatchTimeMinutes),
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Bab Dibaca",
                value = stats.totalChaptersRead.toString(),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Aktivitas",
                value = "${stats.streakDays} Hari",
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Favorit",
                value = (stats.favoriteComicsCount + stats.favoriteAnimesCount + stats.favoriteDramasCount).toString(),
                icon = Icons.Default.Favorite,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Aktivitas 7 Hari Terakhir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                WeeklyActivityChart(data = stats.weeklyActivity)
            }
        }
        
        if (stats.mediaDistribution.sum() > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Distribusi Media",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MediaDistributionChart(distribution = stats.mediaDistribution)
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun WeeklyActivityChart(data: List<Float>) {
    val maxVal = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val barColor = MaterialTheme.colorScheme.primary
    val labels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, value ->
            val heightPercent = value / maxVal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                if (value > 0) {
                    Text(
                        text = value.toInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Canvas(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight(heightPercent.coerceAtLeast(0.05f))
                ) {
                    drawRoundRect(
                        color = barColor,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MediaDistributionChart(distribution: List<Float>) {
    val comicColor = Color(0xFF4CAF50)
    val animeColor = Color(0xFF2196F3)
    val dramaColor = Color(0xFFFF9800)
    
    val comicPct = (distribution[0] * 100).toInt()
    val animePct = (distribution[1] * 100).toInt()
    val dramaPct = (distribution[2] * 100).toInt()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pie Chart
        Canvas(
            modifier = Modifier.size(100.dp).weight(1f)
        ) {
            var startAngle = 0f
            if (distribution[0] > 0) {
                val sweep = distribution[0] * 360f
                drawArc(
                    color = comicColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true
                )
                startAngle += sweep
            }
            if (distribution[1] > 0) {
                val sweep = distribution[1] * 360f
                drawArc(
                    color = animeColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true
                )
                startAngle += sweep
            }
            if (distribution[2] > 0) {
                val sweep = distribution[2] * 360f
                drawArc(
                    color = dramaColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Legends
        Column(modifier = Modifier.weight(1.5f)) {
            if (comicPct > 0) LegendItem(color = comicColor, label = "Komik ($comicPct%)")
            if (animePct > 0) LegendItem(color = animeColor, label = "Anime ($animePct%)")
            if (dramaPct > 0) LegendItem(color = dramaColor, label = "Drama ($dramaPct%)")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatWatchTime(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remMins = minutes % 60
    if (hours < 24) return "${hours}j ${remMins}m"
    val days = hours / 24
    val remHours = hours % 24
    return "${days}h ${remHours}j"
}
