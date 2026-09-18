path = 'app/src/main/java/com/example/data/downloads/MediaDownloadListener.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())', 'downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))')
c = c.replace('downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Download failed")', 'downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Download failed"))')
c = c.replace(')))\n', '))\n') # just in case

with open(path, 'w') as f:
    f.write(c)

