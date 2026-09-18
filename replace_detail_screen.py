import re

with open('app/src/main/java/com/example/ui/screens/DetailScreen.kt', 'r') as f:
    content = f.read()

target = """        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    when (item!!.type) {
                        ContentType.COMIC -> ComicHeader(item!!)
                        ContentType.ANIME -> AnimeHeader(item!!)
                        ContentType.DRAMA -> DramaHeader(item!!)
                        else -> ComicHeader(item!!) // fallback
                    }
                    if (item!!.alternativeSources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tersedia juga di (Alternatif):",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(item!!.alternativeSources) { alt ->
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
                        text = if (item!!.type == ContentType.COMIC) "Chapters" else "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (item!!.type == ContentType.DRAMA) {
                    // Drama uses a card-based episode layout
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
                    // Comic and Anime use a list-based layout
                    items(chapters) { chapter ->
                        val entry = chapterEntries[chapter.id]
                        ListItem(
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
                                        else if (item?.type == ContentType.COMIC) viewModel.downloadChapter(chapter.id)
                                        else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.DOWNLOAD)
                                    }) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download, 
                                            contentDescription = "Download",
                                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { 
                                        if (item!!.type == ContentType.COMIC) onReadChapter(chapter.id, chapter.title)
                                        else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.PLAY)
                                    }) {
                                        Icon(
                                            imageVector = if (item!!.type == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
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
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }"""

replacement = """        } else {
            fun androidx.compose.foundation.lazy.LazyListScope.headerSection() {
                item {
                    when (item!!.type) {
                        ContentType.COMIC -> ComicHeader(item!!)
                        ContentType.ANIME -> AnimeHeader(item!!)
                        ContentType.DRAMA -> DramaHeader(item!!)
                        else -> ComicHeader(item!!) // fallback
                    }
                    if (item!!.alternativeSources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tersedia juga di (Alternatif):",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(item!!.alternativeSources) { alt ->
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
            }

            fun androidx.compose.foundation.lazy.LazyListScope.chaptersSection() {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (item!!.type == ContentType.COMIC) "Chapters" else "Episodes",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (item!!.type == ContentType.DRAMA) {
                    // Drama uses a card-based episode layout
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
                    // Comic and Anime use a list-based layout
                    items(chapters) { chapter ->
                        val entry = chapterEntries[chapter.id]
                        ListItem(
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
                                        else if (item?.type == ContentType.COMIC) viewModel.downloadChapter(chapter.id)
                                        else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.DOWNLOAD)
                                    }) {
                                        Icon(
                                            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download, 
                                            contentDescription = "Download",
                                            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { 
                                        if (item!!.type == ContentType.COMIC) onReadChapter(chapter.id, chapter.title)
                                        else viewModel.resolveVideoStream(chapter.id, chapter.title, StreamAction.PLAY)
                                    }) {
                                        Icon(
                                            imageVector = if (item!!.type == ContentType.COMIC) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.PlayArrow,
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
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val isExpanded = maxWidth >= 600.dp
                if (isExpanded) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane: Metadata & Synopsis
                        LazyColumn(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                            headerSection()
                        }
                        
                        // Right Pane: Chapters/Episodes
                        LazyColumn(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                            chaptersSection()
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        headerSection()
                        chaptersSection()
                    }
                }
            }
        }"""

content = content.replace(target, replacement)
with open('app/src/main/java/com/example/ui/screens/DetailScreen.kt', 'w') as f:
    f.write(content)
print("Done replacing.")
