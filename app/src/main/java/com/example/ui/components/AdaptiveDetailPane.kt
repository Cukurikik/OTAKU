package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.models.Chapter
import com.example.data.models.ChapterEntry
import com.example.data.models.ContentItem
import com.example.data.models.ContentType
import com.example.data.models.LibraryEntry
import com.example.ui.util.tvDpadFocusable

/**
 * Static Header Pane displaying poster, metadata, synopsis, alternative sources,
 * and quick-action buttons in a rich card / scrollable column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailStaticHeaderPane(
    item: ContentItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectCategory: () -> Unit,
    onSelectTracker: () -> Unit,
    onSwapSource: () -> Unit,
    onAlternativeSourceClick: (String, String) -> Unit,
    primaryActionTitle: String?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var synopsisExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header Image and Main Titles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Sumber: ${item.source}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Badges for Status, Type, and Year
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (item.type) {
                                        ContentType.COMIC -> "Komik"
                                        ContentType.ANIME -> "Anime"
                                        ContentType.DRAMA -> "Drama"
                                        else -> "Media"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        if (item.status.isNotBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(item.status, style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }

                        if (item.year.isNotBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(item.year, style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action Button (Mulai / Lanjutkan)
            if (primaryActionTitle != null) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (item.type == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                        contentDescription = primaryActionTitle
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = primaryActionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action Buttons Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isFavorite) "Favorit" else "+ Library", maxLines = 1)
                }

                if (isFavorite) {
                    IconButton(
                        onClick = onSelectCategory,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Kategori", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(
                    onClick = onSelectTracker,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "Tracking")
                }

                IconButton(
                    onClick = onSwapSource,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Ganti Sumber", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Genres Wrap / FlowRow
            if (item.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Genre",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.genres.forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            // Synopsis Section
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sinopsis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = { synopsisExpanded = !synopsisExpanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (synopsisExpanded) "Sembunyikan" else "Baca Selengkapnya...")
                }
            }

            // Alternative Sources
            if (item.alternativeSources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tersedia juga di sumber alternatif:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(item.alternativeSources) { alt ->
                        InputChip(
                            selected = false,
                            onClick = { onAlternativeSourceClick(alt.id, alt.sourceId) },
                            label = { Text(alt.sourceName) },
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scrollable List Pane displaying search, sort, and all chapters or episodes.
 */
@Composable
fun DetailEpisodeListPane(
    contentType: ContentType,
    chapters: List<Chapter>,
    chapterEntries: Map<String, ChapterEntry>,
    onReadChapter: (String, String) -> Unit,
    onWatchEpisode: (String, String) -> Unit,
    onDownloadChapter: (String, String) -> Unit,
    onDeleteDownloadedChapter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(false) }

    val filteredChapters = remember(chapters, searchQuery, isAscending) {
        val list = if (searchQuery.isBlank()) {
            chapters
        } else {
            chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        if (isAscending) list.reversed() else list
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar: Title + Total Count + Sort Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (contentType == ContentType.COMIC) "Daftar Chapter" else "Daftar Episode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${chapters.size} ${if (contentType == ContentType.COMIC) "Chapter" else "Episode"} Tersedia",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = { isAscending = !isAscending }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = if (isAscending) "Urutkan Terbaru" else "Urutkan Terlama",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (contentType == ContentType.COMIC) "Cari nomor chapter..." else "Cari nomor episode...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Cari", tint = MaterialTheme.colorScheme.outline)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Chapter / Episode List
            if (filteredChapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Tidak ada chapter/episode yang cocok" else "Belum ada episode/chapter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredChapters, key = { it.id }) { chapter ->
                        val entry = chapterEntries[chapter.id]
                        val isDownloaded = entry?.downloaded == true
                        val isRead = entry?.read == true

                        if (contentType == ContentType.DRAMA) {
                            val watchAction = { onWatchEpisode(chapter.id, chapter.title) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvDpadFocusable(
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = watchAction
                                    )
                                    .clickable(onClick = watchAction),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.title,
                                            fontWeight = if (isRead) FontWeight.Normal else FontWeight.SemiBold,
                                            color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (chapter.date.isNotBlank()) {
                                            Text(
                                                text = chapter.date,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isDownloaded) onDeleteDownloadedChapter(chapter.id)
                                            else onDownloadChapter(chapter.id, chapter.title)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            val openAction = {
                                if (contentType == ContentType.COMIC) onReadChapter(chapter.id, chapter.title)
                                else onWatchEpisode(chapter.id, chapter.title)
                            }
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isRead) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent
                                ),
                                headlineContent = {
                                    Text(
                                        text = chapter.title,
                                        fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                                        color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = {
                                    if (chapter.date.isNotBlank()) {
                                        Text(chapter.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (isDownloaded) onDeleteDownloadedChapter(chapter.id)
                                            else onDownloadChapter(chapter.id, chapter.title)
                                        }) {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                contentDescription = "Download",
                                                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = openAction) {
                                            Icon(
                                                imageVector = if (contentType == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
                                                contentDescription = "Buka",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvDpadFocusable(
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = openAction
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = openAction)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
