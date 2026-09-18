path = 'app/src/main/java/com/example/workers/VideoDownloadService.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())', 'downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))')

with open(path, 'w') as f:
    f.write(c)

