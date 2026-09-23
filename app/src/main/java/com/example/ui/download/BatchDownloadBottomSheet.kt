package com.example.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Chapter
import com.example.download.AnimeBatchDownloadManager
import com.example.download.BatchEpisodeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadBottomSheet(
    animeTitle: String,
    chapters: List<Chapter>,
    sourceId: String = "",
    mangaId: String = "",
    onDismiss: () -> Unit,
    onDownloadEnqueued: (count: Int, quality: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val batchManager = remember { AnimeBatchDownloadManager(context) }

    val episodeNumbers = remember(chapters) {
        chapters.mapIndexed { idx, ch ->
            val num = Regex("""(?i)(?:ep(?:isode)?|ch(?:apter)?|\b)\s*(\d+)""").find(ch.title)?.groupValues?.get(1)?.toIntOrNull() ?: (idx + 1)
            BatchEpisodeItem(
                episodeNumber = num,
                episodeTitle = ch.title.ifBlank { "Episode $num" },
                episodeUrl = ch.url,
                chapterId = ch.id
            )
        }.ifEmpty {
            (1..12).map { num ->
                BatchEpisodeItem(
                    episodeNumber = num,
                    episodeTitle = "Episode $num",
                    chapterId = "ep_$num"
                )
            }
        }
    }

    var selectedEpisodes by remember { mutableStateOf(episodeNumbers.map { it.episodeNumber }.toSet()) }
    var selectedQuality by remember { mutableStateOf("720p") }
    var onlyWifi by remember { mutableStateOf(false) }

    val availableQualities = listOf("1080p", "720p", "480p", "360p", "Auto")

    // Estimasi ukuran penyimpanan
    val estimatedMbPerEpisode = when (selectedQuality) {
        "1080p" -> 550
        "720p" -> 260
        "480p" -> 140
        "360p" -> 80
        else -> 260
    }
    val totalEstimatedMb = selectedEpisodes.size * estimatedMbPerEpisode
    val estimatedStorageText = if (totalEstimatedMb >= 1024) {
        "%.1f GB".format(totalEstimatedMb / 1024f)
    } else {
        "$totalEstimatedMb MB"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("batch_download_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Batch Download Episode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("batch_download_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Selector Kualitas Video (HLS Quality Selector)
            Text(
                text = "Pilih Resolusi Video",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableQualities.forEach { quality ->
                    val isSelected = selectedQuality == quality
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedQuality = quality },
                        label = { Text(quality) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("quality_chip_$quality")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Preset Filter Rentang Episode
            Text(
                text = "Pilih Episode (${selectedEpisodes.size}/${episodeNumbers.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { selectedEpisodes = episodeNumbers.map { it.episodeNumber }.toSet() },
                    label = { Text("Semua") },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (selectedEpisodes.size == episodeNumbers.size) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("preset_select_all")
                )

                if (episodeNumbers.size > 12) {
                    SuggestionChip(
                        onClick = {
                            selectedEpisodes = episodeNumbers.take(12).map { it.episodeNumber }.toSet()
                        },
                        label = { Text("1 - 12") },
                        modifier = Modifier.testTag("preset_select_1_12")
                    )

                    SuggestionChip(
                        onClick = {
                            selectedEpisodes = episodeNumbers.drop(12).take(12).map { it.episodeNumber }.toSet()
                        },
                        label = { Text("13 - 24") },
                        modifier = Modifier.testTag("preset_select_13_24")
                    )
                }

                SuggestionChip(
                    onClick = { selectedEpisodes = emptySet() },
                    label = { Text("Hapus") },
                    modifier = Modifier.testTag("preset_clear_all")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Grid Episode Picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(8.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(episodeNumbers, key = { it.episodeNumber }) { epItem ->
                        val isSelected = selectedEpisodes.contains(epItem.episodeNumber)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedEpisodes = if (isSelected) {
                                        selectedEpisodes - epItem.episodeNumber
                                    } else {
                                        selectedEpisodes + epItem.episodeNumber
                                    }
                                }
                                .testTag("episode_grid_item_${epItem.episodeNumber}")
                        ) {
                            Text(
                                text = "${epItem.episodeNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Estimasi Penyimpanan & Pengaturan Jaringan
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Estimasi Penyimpanan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$estimatedStorageText (~${estimatedMbPerEpisode}MB/ep)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (onlyWifi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hanya Wi-Fi",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Switch(
                            checked = onlyWifi,
                            onCheckedChange = { onlyWifi = it },
                            modifier = Modifier.testTag("switch_wifi_only")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Download Button
            Button(
                onClick = {
                    if (selectedEpisodes.isNotEmpty()) {
                        val chosenItems = episodeNumbers.filter { selectedEpisodes.contains(it.episodeNumber) }
                        batchManager.enqueueBatchItems(
                            animeTitle = animeTitle,
                            episodes = chosenItems,
                            targetQuality = selectedQuality,
                            sourceId = sourceId,
                            mangaId = mangaId,
                            requiresWifi = onlyWifi
                        )
                        onDownloadEnqueued(selectedEpisodes.size, selectedQuality)
                        onDismiss()
                    }
                },
                enabled = selectedEpisodes.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("batch_download_submit_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedEpisodes.isEmpty()) "Pilih Minimal 1 Episode" else "Unduh ${selectedEpisodes.size} Episode ($selectedQuality)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
