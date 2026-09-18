package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ContentType
import com.example.data.sources.ContentSource
import com.example.data.sources.SourceBaseUrlStore
import com.example.data.sources.SourceHeaderStore
import com.example.data.sources.SourceStatusBus

/**
 * Screen dedicated to Source & Extension Network Management.
 * Allows users to inspect and override User-Agent, Cookie, Referer, and Base URLs
 * on a per-source basis to bypass anti-bot and Cloudflare protections seamlessly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagementScreen(
    sources: List<ContentSource>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<ContentType?>(null) }
    var selectedSourceForConfig by remember { mutableStateOf<ContentSource?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val filteredSources = remember(sources, searchQuery, selectedTypeFilter, refreshTrigger) {
        sources.filter { source ->
            val matchesQuery = searchQuery.isBlank() ||
                    source.sourceName.contains(searchQuery, ignoreCase = true) ||
                    source.sourceId.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == null || source.type == selectedTypeFilter
            matchesQuery && matchesType
        }
    }

    if (selectedSourceForConfig != null) {
        val src = selectedSourceForConfig!!
        SourceConfigDialog(
            sourceId = src.sourceId,
            sourceName = src.sourceName,
            onDismissRequest = {
                selectedSourceForConfig = null
                refreshTrigger++
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Manajemen Sumber",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Header & Proteksi L7 (${sources.size} sumber)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Reset all custom headers
                            sources.forEach { s ->
                                SourceHeaderStore.clear(context, s.sourceId)
                                SourceBaseUrlStore.set(context, s.sourceId, "")
                                SourceStatusBus.clear(s.sourceId)
                            }
                            refreshTrigger++
                            Toast.makeText(context, "Semua konfigurasi sumber di-reset ke default", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Semua", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari sumber / domain...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content Type Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("Semua (${sources.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == ContentType.COMIC,
                        onClick = { selectedTypeFilter = ContentType.COMIC },
                        label = { Text("Komik (${sources.count { it.type == ContentType.COMIC }})") },
                        leadingIcon = { Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == ContentType.ANIME,
                        onClick = { selectedTypeFilter = ContentType.ANIME },
                        label = { Text("Anime (${sources.count { it.type == ContentType.ANIME }})") },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == ContentType.DRAMA,
                        onClick = { selectedTypeFilter = ContentType.DRAMA },
                        label = { Text("Drama (${sources.count { it.type == ContentType.DRAMA }})") },
                        leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Source List
            if (filteredSources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LayersClear,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tidak ada sumber yang cocok",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSources, key = { it.sourceId }) { source ->
                        val hasCustomUa = !SourceHeaderStore.getUserAgent(context, source.sourceId).isNullOrBlank()
                        val hasCustomCookie = !SourceHeaderStore.getCookie(context, source.sourceId).isNullOrBlank()
                        val hasCustomUrl = !SourceBaseUrlStore.get(context, source.sourceId, "").isNullOrBlank()
                        val isOverridden = hasCustomUa || hasCustomCookie || hasCustomUrl

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSourceForConfig = source },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOverridden)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (source.type) {
                                                    ContentType.ANIME -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                    ContentType.DRAMA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                                    ContentType.COMIC -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (source.type) {
                                                ContentType.ANIME -> Icons.Default.PlayCircle
                                                ContentType.DRAMA -> Icons.Default.Movie
                                                ContentType.COMIC -> Icons.Default.Book
                                                else -> Icons.Default.Movie
                                            },
                                            contentDescription = null,
                                            tint = when (source.type) {
                                                ContentType.ANIME -> MaterialTheme.colorScheme.primary
                                                ContentType.DRAMA -> MaterialTheme.colorScheme.tertiary
                                                ContentType.COMIC -> MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = source.sourceName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isOverridden) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Custom",
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "ID: ${source.sourceId} • ${source.type.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (isOverridden) {
                                            val activeTags = mutableListOf<String>()
                                            if (hasCustomUrl) activeTags.add("Domain")
                                            if (hasCustomUa) activeTags.add("User-Agent")
                                            if (hasCustomCookie) activeTags.add("Cookie")
                                            Text(
                                                text = "Aktif: ${activeTags.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { selectedSourceForConfig = source }
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Atur Header",
                                        tint = if (isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
