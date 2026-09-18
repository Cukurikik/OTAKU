import re

with open('app/src/main/java/com/example/ui/screens/DownloadQueueScreen.kt', 'r') as f:
    content = f.read()

size_text_old = '''                val sizeText = if (uiState.totalBytes > 0) {
                    val downloadedMb = uiState.downloadedBytes / (1024 * 1024f)
                    val totalMb = uiState.totalBytes / (1024 * 1024f)
                    " - %.1f MB / %.1f MB".format(downloadedMb, totalMb)
                } else ""
                
                Text(
                    text = stateText + sizeText, '''
size_text_new = '''                val sizeText = if (uiState.totalBytes > 0) {
                    val downloadedMb = uiState.downloadedBytes / (1024 * 1024f)
                    val totalMb = uiState.totalBytes / (1024 * 1024f)
                    " - %.1f MB / %.1f MB".format(downloadedMb, totalMb)
                } else ""
                
                val speedText = if (entity.state == "DOWNLOADING" && uiState.downloadSpeedKbps > 0) {
                    " (${uiState.downloadSpeedKbps} KB/s)"
                } else ""
                
                Text(
                    text = stateText + sizeText + speedText, '''
content = content.replace(size_text_old, size_text_new)

with open('app/src/main/java/com/example/ui/screens/DownloadQueueScreen.kt', 'w') as f:
    f.write(content)
