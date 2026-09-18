package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.sources.BrowseFilter
import com.example.data.sources.ContentSource
import com.example.data.sources.FilterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    source: ContentSource,
    currentFilter: BrowseFilter,
    onApplyFilter: (BrowseFilter) -> Unit,
    onDismissRequest: () -> Unit
) {
    val supportedFilters = source.supportedFilters
    if (supportedFilters.isEmpty()) {
        onDismissRequest()
        return
    }

    var selectedOrderBy by remember { mutableStateOf(currentFilter.orderBy) }
    var selectedStatus by remember { mutableStateOf(currentFilter.status) }
    var selectedType by remember { mutableStateOf(currentFilter.type) }
    var selectedGenres by remember { mutableStateOf(currentFilter.genres) }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Filter - ${source.sourceName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            if (supportedFilters.contains(FilterType.ORDER)) {
                Text("Urutkan Berdasarkan", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val orderOptions = listOf("popular" to "Populer", "update" to "Terbaru", "new" to "Baru Rilis")
                    items(orderOptions) { (key, label) ->
                        FilterChip(
                            selected = selectedOrderBy == key,
                            onClick = { selectedOrderBy = key },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (supportedFilters.contains(FilterType.STATUS)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusOptions = listOf(null to "Semua", "ongoing" to "Berjalan (Ongoing)", "completed" to "Tamat (Completed)")
                    items(statusOptions) { (key, label) ->
                        FilterChip(
                            selected = selectedStatus == key,
                            onClick = { selectedStatus = key },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (supportedFilters.contains(FilterType.TYPE)) {
                Text("Tipe", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val typeOptions = listOf(null to "Semua", "manga" to "Manga", "manhwa" to "Manhwa", "manhua" to "Manhua")
                    items(typeOptions) { (key, label) ->
                        FilterChip(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // You could also add dynamic Genre lists based on source.supportedGenres in the future
            // For now we assume genre filter is complex and might need specific source mappings.

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    onApplyFilter(BrowseFilter(
                        orderBy = selectedOrderBy,
                        status = selectedStatus,
                        type = selectedType,
                        genres = selectedGenres
                    ))
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Terapkan Filter")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
