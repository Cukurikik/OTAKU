package com.example.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.server.LanShareManager
import com.example.network.server.LocalMediaServer
import com.example.network.server.PeerDevice
import com.example.network.server.SharedLanFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward

class LanShareViewModel(private val context: Context) : ViewModel() {
    private val lanShareManager = LanShareManager(context)

    val peers = lanShareManager.localPeerDiscovery.peers

    private val _selectedPeer = MutableStateFlow<PeerDevice?>(null)
    val selectedPeer = _selectedPeer.asStateFlow()

    private val _sharedFiles = MutableStateFlow<List<SharedLanFile>>(emptyList())
    val sharedFiles = _sharedFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    init {
        // Automatically start the server if not running, to allow reciprocal sharing
        val server = LocalMediaServer.getInstance(context)
        if (!server.isAlive) {
            try {
                server.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Start discovering peers
        lanShareManager.localPeerDiscovery.startDiscovery()
        
        // Register ourselves so others can find us
        lanShareManager.localPeerDiscovery.startRegistration(
            if (server.listeningPort > 0) server.listeningPort else LocalMediaServer.DEFAULT_PORT
        )
    }

    fun selectPeer(peer: PeerDevice) {
        _selectedPeer.value = peer
        loadFilesForPeer(peer)
    }

    fun clearSelectedPeer() {
        _selectedPeer.value = null
        _sharedFiles.value = emptyList()
    }

    fun refreshPeers() {
        lanShareManager.localPeerDiscovery.startDiscovery()
    }

    fun refreshFiles() {
        _selectedPeer.value?.let { loadFilesForPeer(it) }
    }

    private fun loadFilesForPeer(peer: PeerDevice) {
        viewModelScope.launch {
            _isLoading.value = true
            _sharedFiles.value = lanShareManager.getSharedFiles(peer.baseUrl)
            _isLoading.value = false
        }
    }

    fun downloadFile(peer: PeerDevice, file: SharedLanFile, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(file.name, 0) }
            
            val destDir = if (file.type == "application/zip") {
                File(context.getExternalFilesDir(null), "comics/LAN_Downloads")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "OtakuHub")
            }
            if (!destDir.exists()) destDir.mkdirs()
            
            val destFile = File(destDir, file.name)
            
            val success = lanShareManager.downloadFile(peer.baseUrl, file.path, destFile) { progress ->
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(file.name, progress) }
            }
            
            if (success && destFile.name.endsWith(".mp4")) {
                // Register in MediaStore
                com.example.download.FFmpegExporterEngine.registerWithMediaStore(context, destFile, destFile.name)
            }
            
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(file.name) }
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun getStreamUrl(peer: PeerDevice, file: SharedLanFile): String {
        return lanShareManager.getDownloadUrl(peer.baseUrl, file.path)
    }

    override fun onCleared() {
        super.onCleared()
        lanShareManager.localPeerDiscovery.stopAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanShareScreen(
    viewModel: LanShareViewModel,
    onBack: () -> Unit,
    onStreamVideo: (url: String, title: String) -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val selectedPeer by viewModel.selectedPeer.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedPeer == null) "P2P LAN Share" else selectedPeer!!.serviceName) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPeer != null) {
                            viewModel.clearSelectedPeer()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        if (selectedPeer == null) viewModel.refreshPeers() else viewModel.refreshFiles() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedPeer == null) {
            // Peer List
            if (peers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mencari perangkat di jaringan Wi-Fi...")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pastikan perangkat lain sedang membuka aplikasi Otaku Hub di jaringan yang sama.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(peers) { peer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { viewModel.selectPeer(peer) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer.serviceName, style = MaterialTheme.typography.titleMedium)
                                    Text(peer.ipAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        } else {
            // File List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sharedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Tidak ada file yang dibagikan oleh perangkat ini.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(sharedFiles) { file ->
                        val progress = downloadProgress[file.name]
                        val isDownloading = progress != null
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (file.type == "application/zip") Icons.Default.MenuBook else Icons.Default.Movie,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${file.size / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall)
                                    }
                                    
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            progress = { progress!! / 100f },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        IconButton(onClick = {
                                            viewModel.downloadFile(selectedPeer!!, file) { success ->
                                                Toast.makeText(context, if (success) "Download selesai!" else "Download gagal", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Download, contentDescription = "Download")
                                        }
                                        
                                        if (file.type != "application/zip") {
                                            IconButton(onClick = {
                                                val streamUrl = viewModel.getStreamUrl(selectedPeer!!, file)
                                                onStreamVideo(streamUrl, file.name)
                                            }) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Stream")
                                            }
                                        }
                                    }
                                }
                                
                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress!! / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Mengunduh: $progress%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
