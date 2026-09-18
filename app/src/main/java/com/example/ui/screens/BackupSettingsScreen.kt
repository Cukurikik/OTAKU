package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.BackupManager
import com.example.data.SettingsManager
import com.example.data.backup.BackupEncryptionMode
import com.example.data.backup.BackupOptions
import com.example.workers.AutoBackupWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    
    var backupInterval by remember { mutableIntStateOf(settingsManager.autoBackupInterval) }
    var showBackupIntervalDialog by remember { mutableStateOf(false) }
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // State untuk pembuatan backup
    var selectedEncryptionMode by remember { mutableStateOf(BackupEncryptionMode.KEYSTORE) }
    var userPassphrase by remember { mutableStateOf("") }
    var userPassphraseConfirm by remember { mutableStateOf("") }
    var includeVaultSecrets by remember { mutableStateOf(false) }
    var pendingBackupOptions by remember { mutableStateOf(BackupOptions()) }

    // State untuk pemulihan backup ber-passphrase
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var restorePendingUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassphraseInput by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            isProcessing = true
            coroutineScope.launch {
                val result = backupManager.createBackup(targetUri, pendingBackupOptions)
                isProcessing = false
                if (result.isSuccess) {
                    Toast.makeText(context, "Cadangan terenkripsi (.otakubackup) berhasil disimpan!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal membuat cadangan: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            isProcessing = true
            coroutineScope.launch {
                val inspection = backupManager.inspectBackup(targetUri)
                isProcessing = false
                if (inspection.isSuccess) {
                    val info = inspection.getOrNull()!!
                    if (info.requiresPassphrase) {
                        restorePendingUri = targetUri
                        restorePassphraseInput = ""
                        showRestorePassphraseDialog = true
                    } else {
                        // Tidak membutuhkan passphrase (KeyStore hardware / Legacy format)
                        isProcessing = true
                        val restoreResult = backupManager.restoreBackup(targetUri, passphrase = null)
                        isProcessing = false
                        if (restoreResult.isSuccess) {
                            Toast.makeText(context, "Pemulihan berhasil! Silakan restart aplikasi.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Gagal pemulihan: ${restoreResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Berkas cadangan tidak dapat dibuka: ${inspection.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pencadangan & Pemulihan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    SettingsCategory("Pencadangan Terenkripsi (.otakubackup)")
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Buat Cadangan Terenkripsi",
                        subtitle = "Simpan riwayat, library, dan konfigurasi dengan enkripsi AES-256-GCM",
                        onClick = {
                            userPassphrase = ""
                            userPassphraseConfirm = ""
                            showCreateBackupDialog = true
                        }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Restore,
                        title = "Pulihkan Cadangan",
                        subtitle = "Buka berkas .otakubackup, kunci hardware, atau cadangan lama",
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                        }
                    )

                    SettingsCategory("Keamanan & Isolasi Vault")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Standar Enkripsi AES-256-GCM",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Semua file ekspor dan cadangan otomatis dilindungi enkripsi Galois/Counter Mode dengan perlindungan integritas anti-tampering dan isolasi token sensitif.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsCategory("Sinkronisasi Awan")
                    SettingsItem(
                        icon = Icons.Default.CloudSync,
                        title = "Konfigurasi Firebase Sync",
                        subtitle = "Masuk dan aktifkan sinkronisasi ke Firebase",
                        onClick = { onNavigateToCloudSync() }
                    )

                    SettingsCategory("Pencadangan Otomatis")
                    SettingsItem(
                        icon = Icons.Default.Schedule,
                        title = "Jadwal Pencadangan Otomatis",
                        subtitle = if (backupInterval > 0) "Setiap $backupInterval hari (Enkripsi KeyStore Hardware)" else "Mati",
                        onClick = { showBackupIntervalDialog = true }
                    )
                }
            }

            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    // Dialog Pembuatan Cadangan Terenkripsi
    if (showCreateBackupDialog) {
        var passwordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateBackupDialog = false },
            title = { Text("Buat Cadangan Terenkripsi") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Pilih metode keamanan untuk mengenkripsi file cadangan:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pilihan KeyStore Hardware
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEncryptionMode = BackupEncryptionMode.KEYSTORE }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEncryptionMode == BackupEncryptionMode.KEYSTORE,
                            onClick = { selectedEncryptionMode = BackupEncryptionMode.KEYSTORE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Android KeyStore Hardware", fontWeight = FontWeight.SemiBold)
                            Text("Enkripsi otomatis tanpa perlu kata sandi (perangkat ini)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Pilihan Passphrase Pengguna
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEncryptionMode = BackupEncryptionMode.PASSPHRASE }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedEncryptionMode == BackupEncryptionMode.PASSPHRASE,
                            onClick = { selectedEncryptionMode = BackupEncryptionMode.PASSPHRASE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Kata Sandi Khusus (PBKDF2)", fontWeight = FontWeight.SemiBold)
                            Text("Dapat dipulihkan di perangkat mana pun dengan kata sandi", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Input Passphrase jika dipilih
                    if (selectedEncryptionMode == BackupEncryptionMode.PASSPHRASE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = userPassphrase,
                            onValueChange = { userPassphrase = it },
                            label = { Text("Kata Sandi") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = userPassphraseConfirm,
                            onValueChange = { userPassphraseConfirm = it },
                            label = { Text("Konfirmasi Kata Sandi") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Opsi Isolasi Vault Secrets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeVaultSecrets = !includeVaultSecrets }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeVaultSecrets,
                            onCheckedChange = { includeVaultSecrets = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Sertakan Token OAuth & Vault", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Token AniList/MAL/Kitsu aman terenkripsi di dalam berkas", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedEncryptionMode == BackupEncryptionMode.PASSPHRASE) {
                            if (userPassphrase.length < 6) {
                                Toast.makeText(context, "Kata sandi minimal 6 karakter.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (userPassphrase != userPassphraseConfirm) {
                                Toast.makeText(context, "Konfirmasi kata sandi tidak cocok.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        pendingBackupOptions = BackupOptions(
                            encryptionMode = selectedEncryptionMode,
                            passphrase = if (selectedEncryptionMode == BackupEncryptionMode.PASSPHRASE) userPassphrase else null,
                            includeVaultSecrets = includeVaultSecrets
                        )
                        showCreateBackupDialog = false

                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val fileName = "OtakuHub_Backup_${dateFormat.format(Date())}.otakubackup"
                        createDocumentLauncher.launch(fileName)
                    }
                ) {
                    Text("Lanjutkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBackupDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Input Passphrase saat Restore
    if (showRestorePassphraseDialog && restorePendingUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestorePassphraseDialog = false
                restorePendingUri = null
            },
            title = { Text("Masukkan Kata Sandi Cadangan") },
            text = {
                Column {
                    Text("Berkas .otakubackup ini dilindungi enkripsi AES-256-GCM. Masukkan kata sandi yang digunakan saat pencadangan:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = restorePassphraseInput,
                        onValueChange = { restorePassphraseInput = it },
                        label = { Text("Kata Sandi") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = restorePendingUri ?: return@Button
                        val pass = restorePassphraseInput
                        showRestorePassphraseDialog = false
                        restorePendingUri = null

                        isProcessing = true
                        coroutineScope.launch {
                            val result = backupManager.restoreBackup(uri, passphrase = pass)
                            isProcessing = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Pemulihan berhasil! Silakan restart aplikasi.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal pemulihan: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestorePassphraseDialog = false
                    restorePendingUri = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Interval Auto-Backup
    if (showBackupIntervalDialog) {
        val options = listOf(0 to "Mati", 1 to "Setiap Hari", 3 to "Setiap 3 Hari", 7 to "Setiap Minggu")
        AlertDialog(
            onDismissRequest = { showBackupIntervalDialog = false },
            title = { Text("Jadwal Pencadangan Otomatis") },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    backupInterval = value
                                    settingsManager.autoBackupInterval = value
                                    AutoBackupWorker.schedule(context)
                                    showBackupIntervalDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, modifier = Modifier.weight(1f))
                            if (backupInterval == value) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupIntervalDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
