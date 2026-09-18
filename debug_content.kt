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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.models.ContentItem
import com.example.data.catalog.HubMode
import com.example.data.sources.SourceStatusBus
import com.example.ui.viewmodels.HomeViewModel

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import com.example.ui.viewmodels.SourceSearchState
import com.example.ui.viewmodels.SourceResult


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (String, String) -> Unit
) {
    val items by viewModel.items.collectAsState()
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
    val groupedSearchState by viewModel.groupedSearchState.collectAsState()

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
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search"
                            )
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

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null && items.isEmpty()) {
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (sourceStatusErrors.isNotEmpty()) {
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
fun ContentCard(item: ContentItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
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
