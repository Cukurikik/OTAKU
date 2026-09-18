package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.OtakuHubApp
import com.example.data.SettingsManager
import com.example.data.sync.CloudSyncManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    
    var projectId by remember { mutableStateOf(settingsManager.firebaseProjectId) }
    var appId by remember { mutableStateOf(settingsManager.firebaseAppId) }
    var apiKey by remember { mutableStateOf(settingsManager.firebaseApiKey) }
    var webClientId by remember { mutableStateOf(settingsManager.googleWebClientId) }
    var syncEnabled by remember { mutableStateOf(settingsManager.syncEnabled) }
    
    var syncStatus by remember { mutableStateOf("") }
    var isFirebaseInitialized by remember { mutableStateOf(CloudSyncManager.isInitialized) }
    
    val pendingQueue = remember { 
        try { OtakuHubApp.instance.pendingSyncQueue } catch (_: Exception) { null }
    }
    val pendingCount by (pendingQueue?.pendingCount?.collectAsState() ?: remember { mutableStateOf(0) })
    val isQueueSyncing by (pendingQueue?.isSyncing?.collectAsState() ?: remember { mutableStateOf(false) })
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync via Firebase") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            
            if (!isFirebaseInitialized) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        "Firebase belum dikonfigurasi atau inisialisasi gagal. Harap isi kredensial di bawah dan klik 'Simpan & Inisialisasi Firebase' terlebih dahulu.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
Text("Konfigurasi Firebase", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = projectId,
                onValueChange = { projectId = it },
                label = { Text("Project ID") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = appId,
                onValueChange = { appId = it },
                label = { Text("App ID") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webClientId,
                onValueChange = { webClientId = it },
                label = { Text("Google Web Client ID") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = { 
                        syncEnabled = it
                        settingsManager.syncEnabled = it
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aktifkan Sinkronisasi")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    settingsManager.firebaseProjectId = projectId
                    settingsManager.firebaseAppId = appId
                    settingsManager.firebaseApiKey = apiKey
                    settingsManager.googleWebClientId = webClientId
                    
                    syncStatus = "Menyimpan pengaturan & Menginisialisasi Firebase..."
                    coroutineScope.launch {
                        try {
                            CloudSyncManager.initialize(context, settingsManager)
                            isFirebaseInitialized = CloudSyncManager.isInitialized
                            if (isFirebaseInitialized) {
                                syncStatus = "Firebase berhasil diinisialisasi!"
                            } else {
                                syncStatus = "Gagal menginisialisasi Firebase. Periksa kredensial."
                            }
                        } catch (e: Exception) {
                            syncStatus = "Error: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Simpan & Inisialisasi Firebase")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = isFirebaseInitialized,
                onClick = {
                    coroutineScope.launch {
                        syncStatus = "Meminta login Google..."
                        try {
                            val user = CloudSyncManager.signInWithGoogle(context)
                            if (user != null) {
                                syncStatus = "Berhasil masuk sebagai ${user.email}"
                            } else {
                                syncStatus = "Login gagal / dibatalkan."
                            }
                        } catch (e: Exception) {
                            syncStatus = "Error Login: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Masuk dengan Google (Wajib untuk Cloud Sync)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                enabled = isFirebaseInitialized,
                onClick = {
                    coroutineScope.launch {
                        syncStatus = "Mulai sinkronisasi..."
                        try {
                            CloudSyncManager.syncNow(context)
                            syncStatus = "Sinkronisasi selesai!"
                        } catch (e: Exception) {
                            syncStatus = "Gagal sinkronisasi: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sinkronkan Sekarang")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Antrean Sinkronisasi Luring (Offline Queue)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (pendingCount > 0) "Terdapat $pendingCount aksi luring tertunda dalam antrean." else "Semua data tracking dan sinkronisasi up-to-date (Antrean kosong).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isQueueSyncing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang memproses antrean ke server...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                enabled = !isQueueSyncing && pendingCount > 0,
                onClick = {
                    coroutineScope.launch {
                        syncStatus = "Memproses antrean sinkronisasi offline..."
                        try {
                            pendingQueue?.processQueue()
                            syncStatus = "Antrean offline berhasil diproses!"
                        } catch (e: Exception) {
                            syncStatus = "Gagal memproses antrean: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Proses Antrean Luring Sekarang")
            }
            
            if (syncStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(syncStatus, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
