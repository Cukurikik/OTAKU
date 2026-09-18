package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.data.SettingsManager
import com.example.storage.StoragePathManager
import com.example.workers.LibraryUpdateWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToBackup: () -> Unit,
    onNavigateToStatistics: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // States
    var updateInterval by remember { mutableIntStateOf(settingsManager.libraryUpdateInterval) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }

    var autoMigrationEnabled by remember { mutableStateOf(settingsManager.autoMigrationEnabled) }
    var autoMigrationInterval by remember { mutableIntStateOf(settingsManager.autoMigrationIntervalHours) }
    var showAutoMigrationIntervalDialog by remember { mutableStateOf(false) }

    var autoExtensionUpdateEnabled by remember { mutableStateOf(settingsManager.autoUpdateExtensionsEnabled) }
    var autoExtensionUpdateInterval by remember { mutableIntStateOf(settingsManager.autoUpdateExtensionsIntervalHours) }
    var autoExtensionDownload by remember { mutableStateOf(settingsManager.autoDownloadExtensionUpdates) }
    var showAutoExtensionIntervalDialog by remember { mutableStateOf(false) }

    var storageLocation by remember { mutableStateOf(StoragePathManager.getStorageLocationDisplay(context)) }
    var isCustomSaf by remember { mutableStateOf(settingsManager.downloadStorageUri.isNotBlank()) }
    var galleryIsolation by remember { mutableStateOf(settingsManager.galleryIsolationEnabled) }
    var fileObfuscation by remember { mutableStateOf(settingsManager.fileObfuscationEnabled) }

    var webCompanionEnabled by remember { mutableStateOf(settingsManager.isWebCompanionEnabled) }
    val server = remember { com.example.network.server.LocalMediaServer.getInstance(context) }
    val localIp = remember(webCompanionEnabled) { com.example.network.server.LocalMediaServer.getLocalIpAddress(context) }

    // SAF Document Tree Launcher
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            StoragePathManager.setSafDirectory(context, uri)
            storageLocation = StoragePathManager.getStorageLocationDisplay(context)
            isCustomSaf = true
            scope.launch {
                snackbarHostState.showSnackbar("Direktori unduhan SAF berhasil dikonfigurasi & diproteksi (.nomedia).")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsCategory("Analitik & Statistik")
                SettingsItem(
                    icon = Icons.Default.BarChart,
                    title = "Statistik Pengguna",
                    subtitle = "Lihat grafik waktu tonton, bab dibaca, tren harian & distribusi",
                    onClick = onNavigateToStatistics
                )

                SettingsCategory("Penyimpanan & Proteksi Media (SAF)")
                SettingsItem(
                    icon = Icons.Default.FolderSpecial,
                    title = "Lokasi Direktori Unduhan",
                    subtitle = storageLocation,
                    onClick = {
                        safLauncher.launch(null)
                    }
                )

                if (isCustomSaf) {
                    SettingsItem(
                        icon = Icons.Default.RestartAlt,
                        title = "Reset ke Penyimpanan Standar",
                        subtitle = "Kembalikan lokasi penyimpanan ke folder internal terisolasi",
                        onClick = {
                            StoragePathManager.resetToDefaultStorage(context)
                            storageLocation = StoragePathManager.getStorageLocationDisplay(context)
                            isCustomSaf = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Lokasi penyimpanan dikembalikan ke default.")
                            }
                        }
                    )
                }

                SettingsSwitchItem(
                    icon = Icons.Default.VisibilityOff,
                    title = "Isolasi Galeri (.nomedia)",
                    subtitle = "Cegah pemindai media sistem memunculkan gambar komik & video di Galeri HP",
                    checked = galleryIsolation,
                    onCheckedChange = { isChecked ->
                        galleryIsolation = isChecked
                        settingsManager.galleryIsolationEnabled = isChecked
                        if (isChecked) {
                            StoragePathManager.applyNoMediaToAll(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Proteksi .nomedia diterapkan ke seluruh folder unduhan.")
                            }
                        }
                    }
                )

                SettingsSwitchItem(
                    icon = Icons.Default.Security,
                    title = "Penyamaran Ekstensi Berkas (.dat)",
                    subtitle = "Menyamarkan format file unduhan (.jpg/.mp4 menjadi .dat) di penyimpanan",
                    checked = fileObfuscation,
                    onCheckedChange = { isChecked ->
                        fileObfuscation = isChecked
                        settingsManager.fileObfuscationEnabled = isChecked
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Kunci & Amankan Seluruh Folder Unduhan",
                    subtitle = "Periksa dan buat ulang berkas .nomedia di semua subdirektori sekarang",
                    onClick = {
                        StoragePathManager.applyNoMediaToAll(context)
                        scope.launch {
                            snackbarHostState.showSnackbar("Semua folder unduhan berhasil dikunci dengan .nomedia.")
                        }
                    }
                )

                SettingsCategory("Pembaruan Latar Belakang")
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = "Interval Pembaruan Library",
                    subtitle = if (updateInterval > 0) "Setiap $updateInterval jam" else "Mati",
                    onClick = { showUpdateIntervalDialog = true }
                )

                SettingsCategory("Pembaruan Ekstensi (Auto-Updater Engine)")
                SettingsSwitchItem(
                    icon = Icons.Default.CloudSync,
                    title = "Auto-Update Ekstensi Sumber",
                    subtitle = "Periksa versi baru ekstensi secara berkala di latar belakang untuk mencegah error / source decay",
                    checked = autoExtensionUpdateEnabled,
                    onCheckedChange = { isEnabled ->
                        autoExtensionUpdateEnabled = isEnabled
                        settingsManager.autoUpdateExtensionsEnabled = isEnabled
                        com.example.workers.ExtensionUpdateWorker.schedule(context)
                    }
                )
                if (autoExtensionUpdateEnabled) {
                    SettingsItem(
                        icon = Icons.Default.Schedule,
                        title = "Interval Pemeriksaan Ekstensi",
                        subtitle = if (autoExtensionUpdateInterval > 0) "Setiap $autoExtensionUpdateInterval jam" else "Mati",
                        onClick = { showAutoExtensionIntervalDialog = true }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Download,
                        title = "Unduh Otomatis ke Cache",
                        subtitle = "Unduh paket APK baru secara otomatis agar proses update instan",
                        checked = autoExtensionDownload,
                        onCheckedChange = { isChecked ->
                            autoExtensionDownload = isChecked
                            settingsManager.autoDownloadExtensionUpdates = isChecked
                        }
                    )
                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Periksa Pembaruan Ekstensi Sekarang",
                        subtitle = if (settingsManager.lastExtensionUpdateCheckResult.isNotBlank())
                            "Status terakhir: ${settingsManager.lastExtensionUpdateCheckResult}"
                        else
                            "Pindai repositori terdaftar untuk versi APK terbaru",
                        onClick = {
                            com.example.workers.ExtensionUpdateWorker.runOnce(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Pemeriksaan pembaruan ekstensi dimulai di latar belakang.")
                            }
                        }
                    )
                }

                SettingsCategory("Pemulihan Link & Migrasi Otomatis")
                SettingsSwitchItem(
                    icon = Icons.Default.Transform,
                    title = "Smart Link Rot Resolver",
                    subtitle = "Deteksi otomatis link rusak / domain mati (3x gagal) dan migrasi ke sumber cadangan",
                    checked = autoMigrationEnabled,
                    onCheckedChange = { isEnabled ->
                        autoMigrationEnabled = isEnabled
                        settingsManager.autoMigrationEnabled = isEnabled
                        com.example.workers.AutoMigrationWorker.schedule(context)
                    }
                )
                if (autoMigrationEnabled) {
                    SettingsItem(
                        icon = Icons.Default.Schedule,
                        title = "Interval Pemeriksaan Link Rot",
                        subtitle = if (autoMigrationInterval > 0) "Setiap $autoMigrationInterval jam" else "Mati",
                        onClick = { showAutoMigrationIntervalDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.PlayArrow,
                        title = "Pindai & Pulihkan Link Sekarang",
                        subtitle = "Mulai pencarian sumber cadangan untuk entri yang mengalami link rot",
                        onClick = {
                            com.example.workers.AutoMigrationWorker.runOnce(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Pemindaian link rot & auto-migration telah dijalankan di latar belakang.")
                            }
                        }
                    )
                }
                
                SettingsCategory("Web Companion Dashboard (Remote PC Streaming)")
                SettingsSwitchItem(
                    icon = Icons.Default.LaptopMac,
                    title = "Web Companion Server",
                    subtitle = "Akses seluruh perpustakaan, riwayat, anime & komik langsung dari browser PC di jaringan Wi-Fi lokal",
                    checked = webCompanionEnabled,
                    onCheckedChange = { isEnabled ->
                        webCompanionEnabled = isEnabled
                        settingsManager.isWebCompanionEnabled = isEnabled
                        server.setContext(context)
                        if (isEnabled) {
                            try {
                                if (!server.isAlive) server.start()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Web Companion aktif: http://$localIp:8080")
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Gagal memulai server: ${e.message}")
                                }
                            }
                        } else {
                            try {
                                server.stop()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Web Companion server dimatikan.")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )

                if (webCompanionEnabled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Buka di Browser PC / Laptop:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            val dashboardUrl = "http://$localIp:8080"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("OtakuHub URL", dashboardUrl)
                                        clipboard?.setPrimaryClip(clip)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("URL $dashboardUrl berhasil disalin!")
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dashboardUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin URL",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Pastikan PC terhubung ke Wi-Fi yang sama dengan ponsel. Tidak memakan daya mirror layar & stream HLS / manga berjalan cepat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                SettingsCategory("Pencadangan Data")
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "Pencadangan & Pemulihan",
                    subtitle = "Ekspor data ke penyimpanan lokal atau pulihkan dari cadangan",
                    onClick = onNavigateToBackup
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showUpdateIntervalDialog) {
        val options = listOf(0 to "Mati", 1 to "Setiap 1 Jam", 6 to "Setiap 6 Jam", 12 to "Setiap 12 Jam", 24 to "Setiap 24 Jam")
        AlertDialog(
            onDismissRequest = { showUpdateIntervalDialog = false },
            title = { Text("Pembaruan Library Otomatis") },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    updateInterval = value
                                    settingsManager.libraryUpdateInterval = value
                                    LibraryUpdateWorker.schedule(context)
                                    showUpdateIntervalDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, modifier = Modifier.weight(1f))
                            if (updateInterval == value) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdateIntervalDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAutoExtensionIntervalDialog) {
        val extensionIntervalOptions = listOf(0 to "Mati", 12 to "Setiap 12 Jam (Rekomendasi)", 24 to "Setiap 24 Jam", 48 to "Setiap 48 Jam")
        AlertDialog(
            onDismissRequest = { showAutoExtensionIntervalDialog = false },
            title = { Text("Jadwal Pemeriksaan Ekstensi") },
            text = {
                Column {
                    extensionIntervalOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoExtensionUpdateInterval = value
                                    settingsManager.autoUpdateExtensionsIntervalHours = value
                                    com.example.workers.ExtensionUpdateWorker.schedule(context)
                                    showAutoExtensionIntervalDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, modifier = Modifier.weight(1f))
                            if (autoExtensionUpdateInterval == value) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoExtensionIntervalDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAutoMigrationIntervalDialog) {
        val migrationOptions = listOf(0 to "Mati", 12 to "Setiap 12 Jam", 24 to "Setiap 24 Jam (Rekomendasi)", 48 to "Setiap 48 Jam")
        AlertDialog(
            onDismissRequest = { showAutoMigrationIntervalDialog = false },
            title = { Text("Jadwal Pemeriksaan Link Rot") },
            text = {
                Column {
                    migrationOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autoMigrationInterval = value
                                    settingsManager.autoMigrationIntervalHours = value
                                    com.example.workers.AutoMigrationWorker.schedule(context)
                                    showAutoMigrationIntervalDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, modifier = Modifier.weight(1f))
                            if (autoMigrationInterval == value) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoMigrationIntervalDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}


@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
