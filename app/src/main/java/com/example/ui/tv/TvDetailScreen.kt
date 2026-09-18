package com.example.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.ui.viewmodels.DetailViewModel
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onReadClick: (String, String) -> Unit,
    onPlayClick: (String, String) -> Unit
) {
    val detailState by viewModel.item.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isComic = detailState?.type == com.example.data.models.ContentType.COMIC

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Left Info Panel
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            Button(onClick = onBack, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Kembali")
            }
            
            if (detailState != null) {
                AsyncImage(
                    model = detailState!!.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = detailState!!.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = detailState!!.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Right Episodes Panel
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp, top = 24.dp, bottom = 24.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Daftar ${if (isComic) "Chapter" else "Episode"}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
            }
            items(chapters) { chapter ->
                ListItem(
                    selected = false,
                    onClick = {
                        if (isComic) {
                            onReadClick(chapter.id, chapter.title)
                        } else {
                            onPlayClick(chapter.id, chapter.title)
                        }
                    },
                    headlineContent = {
                        Text(chapter.title)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
