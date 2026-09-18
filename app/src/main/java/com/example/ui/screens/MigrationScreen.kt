package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.sources.ContentSource
import com.example.ui.viewmodels.MigrationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen(
    viewModel: MigrationViewModel,
    onBack: () -> Unit
) {
    val sourceFromOptions by viewModel.sourceFromOptions.collectAsState()
    val sourceToOptions by viewModel.sourceToOptions.collectAsState()
    val selectedFrom by viewModel.selectedSourceFrom.collectAsState()
    val selectedTo by viewModel.selectedSourceTo.collectAsState()
    val itemsToMigrate by viewModel.itemsToMigrate.collectAsState()
    val migrationStates by viewModel.migrationStates.collectAsState()
    val isMigratingAll by viewModel.isMigratingAll.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val isAutoMigrating by com.example.workers.AutoMigrationWorker.isMigratingFlow(context).collectAsState(initial = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Migrasi Sumber", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedFrom != null && selectedTo != null && itemsToMigrate.isNotEmpty() && !isMigratingAll) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.migrateAll() },
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    text = { Text("Mulai Migrasi (${itemsToMigrate.size} Item)") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Smart Link Rot Resolver",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sistem secara otomatis memantau status link (HTTP 404/502). Jika sebuah sumber mati 3x berturut-turut, algoritma Fuzzy Matching akan memindahkan data ke sumber cadangan tanpa intervensi pengguna.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            com.example.workers.AutoMigrationWorker.runOnce(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Pemindaian link rot & migrasi otomatis dimulai di latar belakang.")
                            }
                        },
                        enabled = !isAutoMigrating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAutoMigrating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sedang Memindai & Memulihkan...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pindai & Pulihkan Link Rusak Sekarang")
                        }
                    }
                }
            }

            Text(
                text = "Atau lakukan migrasi manual dari sumber tertentu:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Dari Sumber
            ExposedDropdownMenuBox(
                expanded = showFromDropdown,
                onExpandedChange = { if (!isMigratingAll) showFromDropdown = !showFromDropdown }
            ) {
                OutlinedTextField(
                    value = selectedFrom?.sourceName ?: "Pilih sumber asal",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Dari Sumber") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFromDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = showFromDropdown,
                    onDismissRequest = { showFromDropdown = false }
                ) {
                    if (sourceFromOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Tidak ada sumber di Library") },
                            onClick = { showFromDropdown = false }
                        )
                    } else {
                        sourceFromOptions.forEach { src ->
                            DropdownMenuItem(
                                text = { Text(src.sourceName) },
                                onClick = {
                                    viewModel.selectSourceFrom(src)
                                    showFromDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Ke Sumber
            AnimatedVisibility(visible = selectedFrom != null) {
                ExposedDropdownMenuBox(
                    expanded = showToDropdown,
                    onExpandedChange = { if (!isMigratingAll) showToDropdown = !showToDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedTo?.sourceName ?: "Pilih sumber tujuan",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ke Sumber") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showToDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = showToDropdown,
                        onDismissRequest = { showToDropdown = false }
                    ) {
                        if (sourceToOptions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Tidak ada sumber tujuan yang cocok") },
                                onClick = { showToDropdown = false }
                            )
                        } else {
                            sourceToOptions.forEach { src ->
                                DropdownMenuItem(
                                    text = { Text(src.sourceName) },
                                    onClick = {
                                        viewModel.selectSourceTo(src)
                                        showToDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedFrom != null && itemsToMigrate.isNotEmpty()) {
                Text(
                    text = "Daftar Item (${itemsToMigrate.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itemsToMigrate) { entry ->
                        val state = migrationStates[entry.mangaId]
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    state?.isSuccess == true -> Color.Green.copy(alpha = 0.1f)
                                    state?.isError == true -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = entry.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(end = 8.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (state?.isMigrating == true) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                                    } else if (state?.isError == true) {
                                        Text(
                                            text = state.errorMsg ?: "Gagal migrasi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else if (state?.isSuccess == true) {
                                        Text(
                                            text = "Berhasil dipindahkan",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF388E3C)
                                        )
                                    }
                                }
                                
                                if (state?.isSuccess == true) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF388E3C))
                                } else if (state?.isError == true) {
                                    Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
