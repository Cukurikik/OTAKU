package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.models.ContentItem
import com.example.data.catalog.HubMode
import com.example.data.sources.SourceStatusBus
import com.example.ui.viewmodels.HomeViewModel
import com.example.ui.util.tvDpadFocusable

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import com.example.ui.viewmodels.MediaSearchState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (String, String) -> Unit,
    onResumeClick: ((com.example.data.models.HistoryItem) -> Unit)? = null
) {
    val items by viewModel.items.collectAsState()
    val resumeItems by viewModel.resumeItems.collectAsState()
    val recommendedItems by viewModel.recommendedItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= items.size - 5 && items.isNotEmpty()) {
                    viewModel.loadNextPage()
                }
            }
    }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSourceId by viewModel.selectedSourceId.collectAsState()
    val sources by viewModel.sourcesState.collectAsState()
    val mode by viewModel.mode.collectAsState()
val errorMessage by viewModel.errorMessage.collectAsState()
    val sourceStatusErrors by SourceStatusBus.errors.collectAsState()
    val groupedByMediaState by viewModel.groupedByMediaState.collectAsState()
    val browseFilter by viewModel.browseFilter.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showLocalMediaDialog by remember { mutableStateOf(false) }

    val selectedSource = sources.find { it.sourceId == selectedSourceId }

    if (showLocalMediaDialog) {
        LocalMediaManagerDialog(
            onDismissRequest = { showLocalMediaDialog = false },
            onRescanRequested = { viewModel.reload() }
        )
    }

    if (showFilterSheet && selectedSource != null) {
        FilterBottomSheet(
            source = selectedSource,
            currentFilter = browseFilter,
            onApplyFilter = { viewModel.updateBrowseFilter(it) },
            onDismissRequest = { showFilterSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otaku Hub", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Top-level content mode, similar to separating Browse surfaces in Mihon/Aniyomi.
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HubMode.entries) { candidate ->
                    FilterChip(
                        selected = mode == candidate,
                        onClick = { viewModel.setMode(candidate) },
                        label = { Text(candidate.label) }
                    )
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(when (mode) { HubMode.ANIME -> "Cari anime..."; HubMode.DRAMA -> "Cari drama..."; HubMode.COMIC -> "Cari manga atau komik..." }) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    Row {
                        if (selectedSource?.supportsFilter == true) {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Filter"
                                )
                            }
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search"
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Sources Row Selection Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedSourceId == null,
                        onClick = { viewModel.selectSource(null) },
                        label = { Text("Semua Sumber") }
                    )
                }
                items(sources) { source ->
                    val hasIssue = sourceStatusErrors.containsKey(source.sourceId)
                    FilterChip(
                        selected = selectedSourceId == source.sourceId,
                        onClick = { viewModel.selectSource(source.sourceId) },
                        label = { Text(source.sourceName) },
                        trailingIcon = if (hasIssue) {
                            {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Sumber ini sedang bermasalah, cek halaman Profil untuk ganti URL",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                }
            }

            var showSourceConfigModalForId by remember { mutableStateOf<String?>(null) }
            if (showSourceConfigModalForId != null) {
                val cfgSource = sources.find { it.sourceId == showSourceConfigModalForId }
                if (cfgSource != null) {
                    SourceConfigDialog(
                        sourceId = cfgSource.sourceId,
                        sourceName = cfgSource.sourceName,
                        onDismissRequest = {
                            showSourceConfigModalForId = null
                            viewModel.reload()
                        }
                    )
                }
            }

            if (selectedSource != null && selectedSourceId != "local_storage" && selectedSourceId != "torrent_source") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sumber aktif: ${selectedSource.sourceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showSourceConfigModalForId = selectedSource.sourceId },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Header & URL", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (selectedSourceId == "local_storage") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Media Penyimpanan Lokal (SAF)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { showLocalMediaDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Kelola Folder", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (selectedSourceId == "torrent_source") {
                val currentContext = androidx.compose.ui.platform.LocalContext.current
                var magnetLink by remember { mutableStateOf("") }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Stream via Magnet Link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = magnetLink,
                            onValueChange = { magnetLink = it },
                            placeholder = { Text("magnet:?xt=urn:btih:...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (magnetLink.isNotBlank()) {
                                    val intent = android.content.Intent(currentContext, com.example.MainActivity::class.java).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra("action", "play_video")
                                        putExtra("sourceId", "torrent_source")
                                        putExtra("episodeId", magnetLink)
                                    }
                                    currentContext.startActivity(intent)
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            enabled = magnetLink.isNotBlank()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hub For You: Top Carousel (One-Click Resume) & Smart Recommendations
            if (searchQuery.isBlank()) {
                if (resumeItems.isNotEmpty()) {
                    ResumeCarouselSection(
                        resumeItems = resumeItems,
                        onResumeClick = { historyItem ->
                            onResumeClick?.invoke(historyItem) ?: onItemClick(historyItem.id, historyItem.sourceId)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (recommendedItems.isNotEmpty() && selectedSourceId == null) {
                    SmartRecommendationSection(
                        recommendedItems = recommendedItems,
                        onItemClick = onItemClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (selectedSourceId == null && (resumeItems.isNotEmpty() || recommendedItems.isNotEmpty()) && items.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jelajah Katalog",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val isGlobalSearch = selectedSourceId == null && searchQuery.isNotBlank()
            
            if (isGlobalSearch) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val entries = groupedByMediaState.entries.toList()
                    items(entries, key = { it.key.name }) { (mediaType, state) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = mediaType.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (state) {
                                        is MediaSearchState.Loading -> {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        }
                                        is MediaSearchState.Success -> {
                                            if (state.items.isNotEmpty()) {
                                                Text(
                                                    text = "${state.items.size} Hasil",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Text("Kosong", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        is MediaSearchState.Error -> {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = "Error",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            when (state) {
                                is MediaSearchState.Success -> {
                                    if (state.items.isNotEmpty()) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(state.items) { item ->
                                                ContentCard(
                                                    item = item,
                                                    onClick = { onItemClick(item.id, item.sourceId) },
                                                    modifier = Modifier.width(120.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                is MediaSearchState.Error -> {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                is MediaSearchState.Loading -> {
                                    // Optionally show shimmer or nothing
                                }
                            }
                        }
                    }
                }
            } else if (errorMessage != null && items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    val errText = when (errorMessage) {
                        is com.example.data.sources.SourceError.Network -> "Network Error: ${(errorMessage as com.example.data.sources.SourceError.Network).message}"
                        is com.example.data.sources.SourceError.Parsing -> "Parsing Error: ${(errorMessage as com.example.data.sources.SourceError.Parsing).message}"
                        is com.example.data.sources.SourceError.RateLimit -> "Rate Limit Exceeded. Try again later."
                        is com.example.data.sources.SourceError.NotFound -> "Content Not Found."
                        is com.example.data.sources.SourceError.Unknown -> "Unknown Error Occurred."
                        else -> "An error occurred."
                    }
                    Text(errText, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            } else if (isLoading && items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            if (selectedSourceId == "local_storage") {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("Tidak Ada Media Lokal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Belum ada berkas .cbz, .zip, .mp4, atau .mkv yang terdeteksi di direktori Downloads. Anda dapat memasang folder kustom menggunakan Storage Access Framework (SAF).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(onClick = { showLocalMediaDialog = true }) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pasang Folder Media (SAF)")
                                }
                            } else if (sourceStatusErrors.isNotEmpty()) {
                                Text("Beberapa sumber memblokir akses (Cloudflare/Timeout)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                                Text("Emulator berjalan di Cloud IP yang sering diblokir. Unduh APK dan install di HP asli Anda untuk akses lancar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            } else {
                                Text("Tidak ada konten ditemukan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                Text("Jika Anda menggunakan Emulator AI Studio, server (Cloudflare) mungkin memblokir IP cloud.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text("Silakan unduh (Export APK) dan jalankan di HP Android asli Anda untuk akses jaringan yang normal tanpa blokir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items) { item ->
                            ContentCard(item = item, onClick = { onItemClick(item.id, item.sourceId) })
                        }
                        
                        item {
                            val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                            if (isLoadingMore) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    LaunchedEffect(gridState, items.size) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect { lastIndex ->
                            if (lastIndex != null && items.isNotEmpty() && lastIndex >= items.size - 4) {
                                viewModel.loadNextPage()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentCard(item: ContentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .tvDpadFocusable(
                shape = RoundedCornerShape(10.dp),
                onClick = onClick
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)),
                        startY = 0.35f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.type.name,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.LightGray
            )
        }
    }
}

@Composable
fun ResumeCarouselSection(
    resumeItems: List<com.example.data.models.HistoryItem>,
    onResumeClick: (com.example.data.models.HistoryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Lanjutkan Menonton & Membaca",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${resumeItems.size} Tersedia",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(resumeItems, key = { "${it.sourceId}_${it.id}" }) { historyItem ->
                ResumeCard(
                    item = historyItem,
                    onResumeClick = { onResumeClick(historyItem) }
                )
            }
        }
    }
}

@Composable
fun ResumeCard(
    item: com.example.data.models.HistoryItem,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onResumeClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .width(220.dp)
            .tvDpadFocusable(shape = RoundedCornerShape(14.dp), onClick = onResumeClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )

                val (badgeBg, badgeText) = when (item.type) {
                    com.example.data.models.ContentType.ANIME -> androidx.compose.ui.graphics.Color(0xFF00E5FF) to androidx.compose.ui.graphics.Color.Black
                    com.example.data.models.ContentType.COMIC -> androidx.compose.ui.graphics.Color(0xFF00E676) to androidx.compose.ui.graphics.Color.Black
                    com.example.data.models.ContentType.DRAMA -> androidx.compose.ui.graphics.Color(0xFFFF9100) to androidx.compose.ui.graphics.Color.Black
                    else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (item.type == com.example.data.models.ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                            contentDescription = "Lanjutkan",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.lastChapterOrEpisode.isNotBlank()) item.lastChapterOrEpisode else "Terakhir diakses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(item.progress * 100).toInt()}% selesai",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lanjutkan",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SmartRecommendationSection(
    recommendedItems: List<com.example.engine.recommendation.RecommendationResult>,
    onItemClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Direkomendasikan Untuk Anda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "AI / ML Lokal",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(recommendedItems, key = { "${it.item.sourceId}_${it.item.id}" }) { rec ->
                RecommendationCard(
                    rec = rec,
                    onClick = { onItemClick(rec.item.id, rec.item.sourceId) }
                )
            }
        }
    }
}

@Composable
fun RecommendationCard(
    rec: com.example.engine.recommendation.RecommendationResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .width(130.dp)
            .tvDpadFocusable(shape = RoundedCornerShape(12.dp), onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            ) {
                AsyncImage(
                    model = rec.item.imageUrl,
                    contentDescription = rec.item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 0.4f
                            )
                        )
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = rec.item.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = rec.item.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = rec.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
