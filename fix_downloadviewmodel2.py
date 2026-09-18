import re

with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'r') as f:
    content = f.read()

# Restore the if/else block properly
# I will just write a clean refreshUiState function
old_func_start = "    private suspend fun refreshUiState(downloads: List<DownloadEntity>) {"
old_func_end = "        _uiDownloads.value = uiList\n    }"
pattern = re.compile(re.escape(old_func_start) + ".*?" + re.escape(old_func_end), re.DOTALL)

new_func = """    private suspend fun refreshUiState(downloads: List<DownloadEntity>) {
        val uiList = mutableListOf<DownloadItemUiState>()
        for (download in downloads) {
            val libraryEntry = libraryDao.getLibraryEntry(download.sourceId, download.mangaId)
            val title = libraryEntry?.title ?: "Unknown Title"
            val thumbnailUrl = libraryEntry?.thumbnailUrl ?: ""
            val chapterEntry = chapterDao.getChapter(download.sourceId, download.mangaId, download.chapterId)
            val chapterTitle = chapterEntry?.name ?: "Chapter ${download.chapterId}"
            
            val isVideo = download.sourceId.lowercase().let { 
                it.contains("anime") || it.contains("otaku") || it.contains("samehadaku") || 
                it.contains("anichin") || it.contains("kura") || it.contains("kaze") || 
                it.contains("anoboy") || it.contains("nime") || it.contains("drama") || 
                it.contains("drakor") || it.contains("viki") || it.contains("licil") || 
                it.contains("film") || it.contains("jucelebrity") 
            }
            
            var progress = 0f
            var bytesDownloaded = 0L
            var bytesTotal = 0L
            var speedKbps = 0L
            val downloadIdKey = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
            
            if (isVideo) {
                if (download.state == "DOWNLOADED") {
                    progress = 1f
                } else if (download.state == "DOWNLOADING") {
                    val downloadId = "${download.sourceId}||${download.mangaId}||${download.chapterId}"
                    progress = com.example.workers.VideoDownloadService.downloadProgressMap[downloadId]?.value ?: 0f
                    bytesDownloaded = com.example.workers.VideoDownloadService.downloadBytesMap[downloadId]?.value ?: 0L
                    bytesTotal = com.example.workers.VideoDownloadService.downloadTotalBytesMap[downloadId]?.value ?: 0L
                }
            } else {
                val pages = pageDownloadDao.getPagesList(download.sourceId, download.mangaId, download.chapterId)
                if (pages.isNotEmpty()) {
                    val downloadedPages = pages.count { it.state == "DOWNLOADED" }
                    progress = downloadedPages.toFloat() / pages.size
                    bytesDownloaded = pages.sumOf { it.bytes }
                } else if (download.state == "DOWNLOADED") {
                    progress = 1f
                }
            }
            
            if (download.state == "DOWNLOADING") {
                val now = System.currentTimeMillis()
                val prevBytes = previousBytesMap[downloadIdKey] ?: bytesDownloaded
                val prevTime = lastRefreshTimeMap[downloadIdKey] ?: now
                val timeDiff = now - prevTime
                if (timeDiff > 0 && bytesDownloaded > prevBytes) {
                    speedKbps = ((bytesDownloaded - prevBytes) / timeDiff) * 1000 / 1024
                }
                previousBytesMap[downloadIdKey] = bytesDownloaded
                lastRefreshTimeMap[downloadIdKey] = now
            } else {
                previousBytesMap.remove(downloadIdKey)
                lastRefreshTimeMap.remove(downloadIdKey)
            }
            
            uiList.add(
                DownloadItemUiState(
                    entity = download,
                    title = title,
                    chapterTitle = chapterTitle,
                    thumbnailUrl = thumbnailUrl,
                    progress = progress.coerceIn(0f, 1f),
                    downloadedBytes = bytesDownloaded,
                    totalBytes = bytesTotal,
                    isVideo = isVideo,
                    downloadSpeedKbps = speedKbps
                )
            )
        }
        _uiDownloads.value = uiList
    }"""

content = pattern.sub(new_func, content)
with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'w') as f:
    f.write(content)
