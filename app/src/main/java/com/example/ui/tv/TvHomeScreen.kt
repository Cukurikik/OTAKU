package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.data.models.ContentItem
import com.example.ui.viewmodels.HomeViewModel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.catalog.HubMode

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onItemClick: (String, String) -> Unit
) {
    val items by viewModel.items.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Simple Left Navigation Rail
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Otaku Hub TV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 32.dp))
            
            HubMode.entries.forEach { candidate ->
                Button(
                    onClick = { viewModel.setMode(candidate) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = if (mode == candidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (mode == candidate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(candidate.label)
                }
            }
        }

        // Content Grid
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (isLoading && items.isEmpty()) {
                // Show loading placeholder
            } else if (items.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items) { item ->
                        TvContentCard(
                            item = item,
                            onClick = { onItemClick(item.id, item.sourceId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvContentCard(item: ContentItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.7f),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f)),
                            startY = 0.5f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.type.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.LightGray
                )
            }
        }
    }
}
