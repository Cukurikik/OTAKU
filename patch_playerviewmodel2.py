import re

with open('app/src/main/java/com/example/ui/viewmodels/PlayerViewModel.kt', 'r') as f:
    content = f.read()

target_old = '''                val download = downloadDao.getDownload(sourceId, itemId, _currentEpisodeId.value)
                if (download != null && download.state == "DOWNLOADED") {
                    val fileMp4 = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub/$itemId-${_currentEpisodeId.value}.mp4")
                    val fileTs = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "OtakuHub/$itemId-${_currentEpisodeId.value}.ts")
                    val existingFile = if (fileTs.exists()) fileTs else if (fileMp4.exists()) fileMp4 else null
                    if (existingFile != null) {
                        _streamInfo.value = PlayerState.Success(VideoStreamInfo(_currentEpisodeId.value, embedUrl = "file://${existingFile.absolutePath}"))
                        return@launch
                    }
                }'''

target_new = '''                val download = downloadDao.getDownload(sourceId, itemId, _currentEpisodeId.value)
                if (download != null && download.state == "DOWNLOADED") {
                    val downloadId = "${sourceId}||${itemId}||${_currentEpisodeId.value}"
                    val exoDownload = com.example.data.downloads.ExoDownloadManager.getManager(context).downloadIndex.getDownload(downloadId)
                    if (exoDownload != null && exoDownload.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                        val offlineUri = exoDownload.request.uri.toString()
                        val info = VideoStreamInfo(
                            episodeId = _currentEpisodeId.value,
                            embedUrl = offlineUri,
                            qualities = listOf(com.example.data.sources.VideoQuality("Offline Cache", offlineUri))
                        )
                        _streamInfo.value = PlayerState.Success(info)
                        return@launch
                    }
                }'''

content = content.replace(target_old, target_new)

with open('app/src/main/java/com/example/ui/viewmodels/PlayerViewModel.kt', 'w') as f:
    f.write(content)
