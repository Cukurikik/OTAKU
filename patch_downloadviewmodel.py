import re

with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'r') as f:
    content = f.read()

# 1. Add speed to UI state
state_old = '''    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isVideo: Boolean
)'''
state_new = '''    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isVideo: Boolean,
    val downloadSpeedKbps: Long = 0L
)'''
content = content.replace(state_old, state_new)

# 2. Add previous bytes tracker in ViewModel
tracker_old = '''    private val _uiDownloads = MutableStateFlow<List<DownloadItemUiState>>(emptyList())
    val uiDownloads: StateFlow<List<DownloadItemUiState>> = _uiDownloads'''
tracker_new = '''    private val _uiDownloads = MutableStateFlow<List<DownloadItemUiState>>(emptyList())
    val uiDownloads: StateFlow<List<DownloadItemUiState>> = _uiDownloads
    
    private val previousBytesMap = mutableMapOf<String, Long>()
    private val lastRefreshTimeMap = mutableMapOf<String, Long>()'''
content = content.replace(tracker_old, tracker_new)

# 3. Calculate speed in refreshUiState
refresh_old = '''        var progress = 0f
            var bytesDownloaded = 0L
            var bytesTotal = 0L'''
refresh_new = '''        var progress = 0f
            var bytesDownloaded = 0L
            var bytesTotal = 0L
            var speedKbps = 0L
            val downloadIdKey = "${download.sourceId}||${download.mangaId}||${download.chapterId}"'''
content = content.replace(refresh_old, refresh_new)

video_speed_old = '''                    bytesDownloaded = com.example.workers.VideoDownloadService.downloadBytesMap[downloadId]?.value ?: 0L
                    bytesTotal = com.example.workers.VideoDownloadService.downloadTotalBytesMap[downloadId]?.value ?: 0L
                }
            }'''
video_speed_new = '''                    bytesDownloaded = com.example.workers.VideoDownloadService.downloadBytesMap[downloadId]?.value ?: 0L
                    bytesTotal = com.example.workers.VideoDownloadService.downloadTotalBytesMap[downloadId]?.value ?: 0L
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
            }'''
content = content.replace(video_speed_old, video_speed_new)

add_old = '''                    totalBytes = bytesTotal,
                    isVideo = isVideo
                )
            )'''
add_new = '''                    totalBytes = bytesTotal,
                    isVideo = isVideo,
                    downloadSpeedKbps = speedKbps
                )
            )'''
content = content.replace(add_old, add_new)

with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'w') as f:
    f.write(content)
