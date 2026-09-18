path = 'app/src/main/java/com/example/data/downloads/MediaDownloadListener.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())\n', 'downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))\n')
c = c.replace('downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Download failed")\n', 'downloadDao.updateDownload(entity.copy(state = "ERROR", errorMessage = "Download failed"))\n')

with open(path, 'w') as f:
    f.write(c)

