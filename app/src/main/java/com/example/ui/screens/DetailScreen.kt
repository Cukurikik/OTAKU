package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.Chapter
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.ui.components.DetailEpisodeListPane
import com.example.ui.components.DetailStaticHeaderPane
import com.example.ui.util.tvDpadFocusable
import com.example.ui.viewmodels.DetailViewModel
import com.example.ui.viewmodels.DetailViewModel.StreamAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onBack: () -> Unit,
    onReadChapter: (String, String) -> Unit,
    onWatchEpisode: (String, String) -> Unit,
    onAlternativeSourceClick: (String, String) -> Unit = { _, _ -> }
) {
    val item by viewModel.item.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val chapterEntries by viewModel.chapterEntries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val libraryEntry by viewModel.libraryEntry.collectAsState()
    
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTrackerDialog by remember { mutableStateOf(false) }
    var showTrackerSelection by remember { mutableStateOf(false) }
    var selectedTracker by remember { mutableStateOf<com.example.data.trackers.Tracker?>(null) }
    val resolvingStream by viewModel.resolvingStream.collectAsState()
    val streamTarget by viewModel.streamTarget.collectAsState()
    val resolvedStreamInfo by viewModel.resolvedStreamInfo.collectAsState()
    val streamAction by viewModel.streamAction.collectAsState(initial = null)

    val quickSourceSearchResults by viewModel.quickSourceSearchResults.collectAsState()
    val isSearchingOtherSources by viewModel.isSearchingOtherSources.collectAsState()
    var showQuickSourceDialog by remember { mutableStateOf(false) }

    // Quick Source Dialog
    if (showQuickSourceDialog) {
        AlertDialog(
            onDismissRequest = { showQuickSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ganti Sumber")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Cari judul \"${item?.title ?: ""}\" di sumber lain untuk mengatasi cermin/server yang lambat atau rusak:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isSearchingOtherSources && quickSourceSearchResults.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Mencari di semua sumber aktif...")
                        }
                    } else if (quickSourceSearchResults.isEmpty()) {
                        Text(
                            "Belum ada hasil dari sumber lain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickSourceSearchResults) { resItem ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showQuickSourceDialog = false
                                            onAlternativeSourceClick(resItem.id, resItem.sourceId)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                resItem.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                "Sumber: ${resItem.source}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Pilih", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickSourceDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Quality Selection Dialog
    if (resolvingStream || resolvedStreamInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissQualitySelector() },
            title = { Text(if (resolvedStreamInfo == null) "Memproses Video" else "Pilih Resolusi Video") },
            text = { 
                if (resolvedStreamInfo == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Mengambil daftar resolusi...")
                    }
                } else {
                    Column {
                        resolvedStreamInfo!!.qualities.forEach { quality ->
                            TextButton(
                                onClick = { viewModel.selectQualityAndProceed(quality.url) }, 
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(quality.qualityLabel)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (resolvedStreamInfo == null) {
                    TextButton(onClick = { viewModel.dismissQualitySelector() }) {
                        Text("Batal")
                    }
                }
            }
        )
    }

    // Stream action response trigger
    LaunchedEffect(streamAction) {
        streamAction?.let { action ->
            val target = streamTarget
            if (target != null) {
                if (target.action == StreamAction.PLAY) {
                    onWatchEpisode(target.episodeId, target.episodeTitle)
                } else {
                    viewModel.downloadChapter(target.episodeId)
                }
            }
            viewModel.clearStreamAction()
        }
    }

    // Calculate Primary Resume Action info
    val lastRead = chapterEntries.values.maxByOrNull { it.lastReadAt ?: 0L }
    val targetChapterId = lastRead?.chapterId ?: chapters.lastOrNull()?.id
    val targetTitle = chapters.find { it.id == targetChapterId }?.title ?: ""
    val isSingleVideo = chapters.size == 1 && item?.type != ContentType.COMIC
    val primaryActionLabel = if (chapters.isNotEmpty() && item != null && targetChapterId != null) {
        if (lastRead?.lastReadAt != null && lastRead.lastReadAt > 0L) "Lanjutkan" else if (item?.type == ContentType.COMIC) "Mulai Membaca" else "Mulai Menonton"
    } else null

    val onPerformPrimaryAction: () -> Unit = {
        if (targetChapterId != null) {
            if (item?.type == ContentType.COMIC) {
                onReadChapter(targetChapterId, targetTitle)
            } else {
                viewModel.resolveVideoStream(targetChapterId, targetTitle, StreamAction.PLAY)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title ?: "", maxLines = 1, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(
                            onClick = {
                                viewModel.searchAcrossOtherSources()
                                showQuickSourceDialog = true
                            }
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Ganti Sumber", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (isFavorite) {
                            IconButton(onClick = { showCategoryDialog = true }) {
                                Icon(Icons.Default.Folder, contentDescription = "Pilih Kategori", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Box {
                            IconButton(onClick = { showTrackerSelection = true }) {
                                Icon(Icons.Default.Sync, contentDescription = "Tracking")
                            }
                            DropdownMenu(
                                expanded = showTrackerSelection,
                                onDismissRequest = { showTrackerSelection = false }
                            ) {
                                com.example.OtakuHubApp.instance.trackerManager.trackers.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.name) },
                                        onClick = {
                                            selectedTracker = t
                                            showTrackerSelection = false
                                            showTrackerDialog = true
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from library" else "Add to library",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Only show FAB on Compact / Medium layout where primary button is not sticky on left pane
            if (windowWidthSizeClass != WindowWidthSizeClass.Expanded && chapters.isNotEmpty() && item != null && !isSingleVideo && targetChapterId != null) {
                ExtendedFloatingActionButton(
                    onClick = onPerformPrimaryAction,
                    icon = { Icon(if (item?.type == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow, "Resume") },
                    text = { Text(primaryActionLabel ?: "Mulai") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        val msg = errorMessage
        if (msg != null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Kembali")
                    }
                }
            }
        } else if (item == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val contentItem = item!!

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded || maxWidth >= 720.dp

                if (isExpanded) {
                    // DUAL-PANE CANONICAL LAYOUT FOR TABLET / FOLDABLE / EXPANDED SCREENS
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Sisi Kiri (Pane 1): Metadata Statis, Poster, Sinopsis, Actions
                        DetailStaticHeaderPane(
                            item = contentItem,
                            isFavorite = isFavorite,
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onSelectCategory = { showCategoryDialog = true },
                            onSelectTracker = { showTrackerSelection = true },
                            onSwapSource = {
                                viewModel.searchAcrossOtherSources()
                                showQuickSourceDialog = true
                            },
                            onAlternativeSourceClick = onAlternativeSourceClick,
                            primaryActionTitle = primaryActionLabel,
                            onPrimaryAction = onPerformPrimaryAction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        // Sisi Kanan (Pane 2): Daftar Chapter / Episode (Scrollable)
                        DetailEpisodeListPane(
                            contentType = contentItem.type,
                            chapters = chapters,
                            chapterEntries = chapterEntries,
                            onReadChapter = onReadChapter,
                            onWatchEpisode = { epId, epTitle ->
                                viewModel.resolveVideoStream(epId, epTitle, StreamAction.PLAY)
                            },
                            onDownloadChapter = { epId, epTitle ->
                                if (contentItem.type == ContentType.COMIC) viewModel.downloadChapter(epId)
                                else viewModel.resolveVideoStream(epId, epTitle, StreamAction.DOWNLOAD)
                            },
                            onDeleteDownloadedChapter = { epId ->
                                viewModel.deleteDownloadedChapter(epId)
                            },
                            modifier = Modifier
                                .weight(1.4f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    // SINGLE COLUMN LAYOUT FOR MOBILE / COMPACT SCREENS
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            when (contentItem.type) {
                                ContentType.COMIC -> ComicHeader(contentItem)
                                ContentType.ANIME -> AnimeHeader(contentItem)
                                ContentType.DRAMA -> DramaHeader(contentItem)
                                else -> ComicHeader(contentItem)
                            }

                            if (contentItem.alternativeSources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Tersedia juga di (Alternatif):",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(contentItem.alternativeSources) { alt ->
                                        AssistChip(
                                            onClick = { onAlternativeSourceClick(alt.id, alt.sourceId) },
                                            label = { Text(alt.sourceName) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (contentItem.type == ContentType.COMIC) "Chapters (${chapters.size})" else "Episodes (${chapters.size})",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (contentItem.type == ContentType.DRAMA) {
                            items(chapters) { chapter ->
                                val entry = chapterEntries[chapter.id]
                                DramaEpisodeCard(
                                    chapter = chapter,
                                    isDownloaded = entry?.downloaded == true,
                                    isRead = entry?.read == true,
                                    onDownload = { 
                                        if (entry?.downloaded == true) viewModel.deleteDownloadedChapter(chapter.id)
                                        else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.DOWNLOAD)
                                    },
                                    onWatch = { viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.PLAY) }
                                )
                            }
                        } else {
                            items(chapters) { chapter ->
                                val entry = chapterEntries[chapter.id]
                                val openAction = {
                                    if (contentItem.type == ContentType.COMIC) onReadChapter(chapter.id, chapter.title)
                                    else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.PLAY)
                                }
                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .tvDpadFocusable(
                                            shape = RoundedCornerShape(8.dp),
                                            onClick = openAction
                                        )
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = openAction),
                                    headlineContent = {
                                        Text(
                                            chapter.title,
                                            fontWeight = if (entry?.read == true) FontWeight.Normal else FontWeight.Bold,
                                            color = if (entry?.read == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        if (chapter.date.isNotBlank()) Text(chapter.date, style = MaterialTheme.typography.bodySmall)
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val isDownloaded = entry?.downloaded == true
                                            IconButton(onClick = {
                                                if (isDownloaded) viewModel.deleteDownloadedChapter(chapter.id)
                                                else if (contentItem.type == ContentType.COMIC) viewModel.downloadChapter(chapter.id)
                                                else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.DOWNLOAD)
                                            }) {
                                                Icon(
                                                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download, 
                                                    contentDescription = "Download",
                                                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { 
                                                if (contentItem.type == ContentType.COMIC) onReadChapter(chapter.id, chapter.title)
                                                else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.PLAY)
                                            }) {
                                                Icon(
                                                    imageVector = if (contentItem.type == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                                                    contentDescription = "Open",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(88.dp))
                        }
                    }
                }
            }
        }

        // Category Selection Dialog
        if (showCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showCategoryDialog = false },
                title = { Text("Pilih Kategori") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateItemCategory(null); showCategoryDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = libraryEntry?.categoryId == null, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tanpa Kategori")
                        }
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateItemCategory(category.id); showCategoryDialog = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = libraryEntry?.categoryId == category.id, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(category.name)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Batal") } }
            )
        }

        // Tracker Dialog
        if (showTrackerDialog && selectedTracker != null) {
            TrackerDialog(
                tracker = selectedTracker!!,
                onDismiss = { showTrackerDialog = false },
                onBind = { trackItem -> viewModel.bindTracker(selectedTracker!!, trackItem) }
            )
        }
    }
}

@Composable
fun ComicHeader(item: ContentItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            
            val status = item.status.takeIf { it.isNotBlank() }
            if (status != null) {
                Spacer(modifier = Modifier.height(4.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(status) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                )
            }
            
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 5, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AnimeHeader(item: ContentItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(item.source, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status.isNotBlank()) {
                    Badge { Text(item.status, modifier = Modifier.padding(4.dp)) }
                }
                if (item.year.isNotBlank()) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text(item.year, modifier = Modifier.padding(4.dp)) }
                }
            }
            
            if (item.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item.genres.take(3).forEach { genre ->
                        AssistChip(onClick = {}, label = { Text(genre, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
            
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Synopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DramaHeader(item: ContentItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primaryContainer)
                    if (item.year.isNotBlank()) {
                        Text(" • ${item.year}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    if (item.status.isNotBlank()) {
                        Text(" • ${item.status}", style = MaterialTheme.typography.labelLarge, color = Color.LightGray)
                    }
                }
            }
        }
        
        Column(modifier = Modifier.padding(16.dp)) {
            if (item.genres.isNotEmpty()) {
                Text("Genres: ${item.genres.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (item.description.isNotBlank()) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DramaEpisodeCard(
    chapter: Chapter,
    isDownloaded: Boolean,
    isRead: Boolean,
    onDownload: () -> Unit,
    onWatch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .tvDpadFocusable(
                shape = RoundedCornerShape(12.dp),
                onClick = onWatch
            )
            .clickable { onWatch() },
        colors = CardDefaults.cardColors(
            containerColor = if (isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                if (chapter.date.isNotBlank()) {
                    Text(text = chapter.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = "Download",
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MoviePlayCard(
    title: String,
    isRead: Boolean,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .tvDpadFocusable(
                shape = RoundedCornerShape(16.dp),
                onClick = onPlay
            )
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
