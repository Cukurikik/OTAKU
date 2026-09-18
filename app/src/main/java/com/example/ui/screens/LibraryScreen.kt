package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.CategoryEntry
import com.example.data.models.LibraryEntry
import com.example.ui.util.TvFocusAccentColor
import com.example.ui.util.tvDpadFocusable
import com.example.ui.viewmodels.LibraryViewModel
import com.example.workers.LibraryUpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stable 64-bit Long identifier generator for LibraryEntry in adaptive navigation
 */
fun LibraryEntry.toStableId(): Long {
    val h1 = sourceId.hashCode().toLong() and 0xFFFFFFFFL
    val h2 = mangaId.hashCode().toLong() and 0xFFFFFFFFL
    return (h1 shl 32) or h2
}

/**
 * Main Library Entry Point delegating to Adaptive Canonical Split-Pane Layout.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onItemClick: (String, String) -> Unit
) {
    AdaptiveLibraryScreen(
        viewModel = viewModel,
        onItemClick = onItemClick
    )
}

/**
 * Adaptasi Canonical Layout pada LibraryScreen menggunakan ListDetailPaneScaffold.
 * - Single-pane pada Compact (Phone Portrait): Menampilkan grid library penuh, klik membuka Detail.
 * - Dual-pane pada Expanded (Tablet Landscape / Foldable / TV): Menampilkan List di kiri & Detail di kanan.
 * - Remote Android TV D-Pad navigasi dengan 1.05x scale-up dan focus border tanpa jebakan fokus.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveLibraryScreen(
    viewModel: LibraryViewModel,
    onItemClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val libraryItems by viewModel.libraryItems.collectAsState(emptyList())

    // BackHandler untuk kembali dari detail pane ke list pane pada single-pane adaptif
    BackHandler(enabled = navigator.canNavigateBack()) {
        navigator.navigateBack()
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.fillMaxSize(),
        listPane = {
            AnimatedPane {
                LibraryListContent(
                    viewModel = viewModel,
                    libraryItems = libraryItems,
                    onItemClick = { entry ->
                        val isDualPane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
                        if (isDualPane) {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.toStableId())
                        } else {
                            onItemClick(entry.mangaId, entry.sourceId)
                        }
                    },
                    selectedItemId = navigator.currentDestination?.content
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val currentId = navigator.currentDestination?.content
                    ?: libraryItems.firstOrNull()?.toStableId()

                if (currentId != null) {
                    LibraryDetailContent(
                        id = currentId,
                        viewModel = viewModel,
                        onOpenFullDetail = { mangaId, sourceId ->
                            onItemClick(mangaId, sourceId)
                        }
                    )
                } else {
                    LibraryEmptyDetailPane()
                }
            }
        }
    )
}

/**
 * List Pane Content: Top bar, kategori chips, search, sort, filter, dan grid kartu dengan D-Pad focusable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListContent(
    viewModel: LibraryViewModel,
    libraryItems: List<LibraryEntry>,
    onItemClick: (LibraryEntry) -> Unit,
    selectedItemId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState(emptyList())
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val currentSortOrder by viewModel.sortOrder.collectAsState()
    val currentMediaType by viewModel.mediaTypeFilter.collectAsState()
    val currentStatus by viewModel.statusFilter.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showMoveCategoryDialog by remember { mutableStateOf(false) }

    // Multi-select state
    var selectedItems by remember { mutableStateOf(setOf<LibraryEntry>()) }
    val isSelectionMode = selectedItems.isNotEmpty()

    // Reset selection when category changes
    LaunchedEffect(selectedCategoryId) {
        selectedItems = emptySet()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedItems.size} terpilih",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedItems = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Batal Pilih")
                            }
                        },
                        actions = {
                            IconButton(onClick = { selectedItems = libraryItems.toSet() }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Pilih Semua")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchExpanded) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = { Text("Cari di Perpustakaan...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text("Perpustakaan", fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = {
                            IconButton(onClick = { LibraryUpdateWorker.runOnce(context) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Perbarui Library")
                            }

                            IconButton(onClick = { 
                                isSearchExpanded = !isSearchExpanded 
                                if (!isSearchExpanded) viewModel.updateSearchQuery("")
                            }) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search, 
                                    contentDescription = "Search"
                                )
                            }
                            
                            IconButton(onClick = { showFilterDialog = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter & Sort")
                            }
                        }
                    )
                }
                
                if (!isSelectionMode) {
                    ScrollableTabRow(
                        selectedTabIndex = when {
                            selectedCategoryId == null -> 0
                            selectedCategoryId == -1L -> 1
                            else -> {
                                val index = categories.indexOfFirst { it.id == selectedCategoryId }
                                if (index >= 0) index + 2 else 0
                            }
                        },
                        edgePadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedCategoryId == null,
                            onClick = { viewModel.selectCategory(null) },
                            text = { Text("Semua") }
                        )
                        
                        Tab(
                            selected = selectedCategoryId == -1L,
                            onClick = { viewModel.selectCategory(-1L) },
                            text = { Text("Offline") }
                        )

                        categories.forEach { category ->
                            Tab(
                                selected = selectedCategoryId == category.id,
                                onClick = { viewModel.selectCategory(category.id) },
                                text = { Text(category.name) }
                            )
                        }

                        IconButton(onClick = { showAddCategoryDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Kategori")
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    viewModel.markItemsAsRead(selectedItems.toList())
                                    selectedItems = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Tandai Dibaca", tint = MaterialTheme.colorScheme.primary)
                            Text("Dibaca", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showMoveCategoryDialog = true }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Pindah Kategori", tint = MaterialTheme.colorScheme.secondary)
                            Text("Kategori", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    viewModel.deleteLibraryItems(selectedItems.toList())
                                    selectedItems = emptySet()
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Favorit", tint = MaterialTheme.colorScheme.error)
                            Text("Hapus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (libraryItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Hasil pencarian kosong." else "Perpustakaan kosong.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(libraryItems, key = { "${it.sourceId}_${it.mangaId}" }) { item ->
                    val isSelected = selectedItems.contains(item)
                    val isPaneSelected = selectedItemId == item.toStableId()

                    LibraryGridCard(
                        entry = item,
                        isSelected = isSelected,
                        isPaneSelected = isPaneSelected,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItems = if (isSelected) {
                                    selectedItems - item
                                } else {
                                    selectedItems + item
                                }
                            } else {
                                onItemClick(item)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedItems = setOf(item)
                            }
                        }
                    )
                }
            }
        }
        
        // Add Category Dialog
        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text("Tambah Kategori") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nama Kategori") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName)
                            newCategoryName = ""
                            showAddCategoryDialog = false
                        }
                    }) {
                        Text("Tambah")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Move Category Dialog
        if (showMoveCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showMoveCategoryDialog = false },
                title = { Text("Pindahkan Kategori") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Pilih Kategori untuk item terpilih:")
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                viewModel.updateLibraryCategories(selectedItems.toList(), null)
                                selectedItems = emptySet()
                                showMoveCategoryDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bawaan / Tanpa Kategori", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                        }

                        categories.forEach { category ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    viewModel.updateLibraryCategories(selectedItems.toList(), category.id)
                                    selectedItems = emptySet()
                                    showMoveCategoryDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(category.name, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMoveCategoryDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Advanced Filter & Sort Dialog
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter & Urutkan", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Sort Order
                        Column {
                            Text("Urutkan Berdasarkan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    LibraryViewModel.SortOrder.ALPHABETICAL to "A-Z",
                                    LibraryViewModel.SortOrder.DATE_ADDED to "Baru Ditambahkan",
                                    LibraryViewModel.SortOrder.UNREAD to "Jumlah Belum Dibaca",
                                    LibraryViewModel.SortOrder.LAST_UPDATED to "Pembaruan Terakhir",
                                    LibraryViewModel.SortOrder.LAST_READ to "Terakhir Dibaca"
                                ).forEach { (order, label) ->
                                    FilterChip(
                                        selected = currentSortOrder == order,
                                        onClick = { viewModel.updateSortOrder(order) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Media Type Filter
                        Column {
                            Text("Jenis Media", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    LibraryViewModel.MediaTypeFilter.ALL to "Semua",
                                    LibraryViewModel.MediaTypeFilter.COMIC to "Komik",
                                    LibraryViewModel.MediaTypeFilter.ANIME to "Anime",
                                    LibraryViewModel.MediaTypeFilter.DRAMA to "Drama"
                                ).forEach { (media, label) ->
                                    FilterChip(
                                        selected = currentMediaType == media,
                                        onClick = { viewModel.updateMediaTypeFilter(media) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider()

                        // Status Filter
                        Column {
                            Text("Status Konten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    LibraryViewModel.StatusFilter.ALL to "Semua",
                                    LibraryViewModel.StatusFilter.ONGOING to "Ongoing",
                                    LibraryViewModel.StatusFilter.COMPLETED to "Completed"
                                ).forEach { (status, label) ->
                                    FilterChip(
                                        selected = currentStatus == status,
                                        onClick = { viewModel.updateStatusFilter(status) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showFilterDialog = false }) {
                        Text("Selesai")
                    }
                }
            )
        }
    }
}

/**
 * Kartu item Library dengan dukungan D-Pad Android TV focus ring dan 1.05x scale feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridCard(
    entry: LibraryEntry,
    isSelected: Boolean,
    isPaneSelected: Boolean = false,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .tvDpadFocusable(
                shape = RoundedCornerShape(10.dp),
                onClick = onClick
            )
            .then(
                if (isPaneSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = entry.thumbnailUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay dimming for multi-selection
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 0.35f
                    )
                )
        )

        Text(
            text = entry.title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )

        // Unread badge
        if (entry.unreadCount > 0 && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (entry.unreadCount > 99) "99+" else entry.unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Multi-selection checkmark icon
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Terpilih",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Detail Pane Content: Tampilan detail sebelah kanan pada Canonical Split-Pane Layout.
 * Menyajikan poster, judul, sumber, status, tombol aksi Buka Detail Lengkap, dan navigasi D-Pad TV.
 */
@Composable
fun LibraryDetailContent(
    id: Long,
    viewModel: LibraryViewModel,
    onOpenFullDetail: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryItems by viewModel.libraryItems.collectAsState(emptyList())
    val entry = libraryItems.find { it.toStableId() == id }

    if (entry != null) {
        LibraryDetailCard(
            entry = entry,
            onOpenFullDetail = { onOpenFullDetail(entry.mangaId, entry.sourceId) },
            onDeleteFromLibrary = { viewModel.deleteLibraryItems(listOf(entry)) },
            modifier = modifier
        )
    } else {
        LibraryEmptyDetailPane(modifier = modifier)
    }
}

/**
 * Komponen Kartu Detail pada Split-Pane dengan tombol aksi cepat dan remote TV focusability.
 */
@Composable
fun LibraryDetailCard(
    entry: LibraryEntry,
    onOpenFullDetail: () -> Unit,
    onDeleteFromLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Poster Header and Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = entry.title,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    AssistChip(
                        onClick = {},
                        label = { Text("Sumber: ${entry.sourceId}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )

                    if (entry.status.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Status: ${entry.status}") }
                        )
                    }

                    if (entry.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                "${entry.unreadCount} Belum Dibaca/Ditonton",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Action Buttons with TV D-Pad Focusability
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenFullDetail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvDpadFocusable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = onOpenFullDetail
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Halaman Lengkap & Bab")
                }

                FilledTonalButton(
                    onClick = onOpenFullDetail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvDpadFocusable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = onOpenFullDetail
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lanjutkan / Putar Sekarang")
                }

                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvDpadFocusable(
                            shape = RoundedCornerShape(12.dp),
                            onClick = { showDeleteConfirmDialog = true }
                        ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hapus dari Perpustakaan")
                }
            }

            HorizontalDivider()

            // Metadata info list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Informasi Pustaka",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (entry.addedAt > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ditambahkan:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text(dateFormat.format(Date(entry.addedAt)), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (entry.lastUpdated > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pembaruan Terakhir:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text(dateFormat.format(Date(entry.lastUpdated)), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ID Manga/Konten:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    Text(entry.mangaId, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Hapus dari Perpustakaan?") },
            text = { Text("Konten \"${entry.title}\" akan dihapus dari daftar favorit Anda.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteFromLibrary()
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Placeholder detail pane saat belum ada item yang dipilih pada mode layar lebar.
 */
@Composable
fun LibraryEmptyDetailPane(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Pilih Konten dari Pustaka",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pilih item komik, anime, atau drama di sebelah kiri untuk melihat informasi lengkap, bab, serta progres baca/tonton secara instan di sini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
