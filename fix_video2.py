path = 'app/src/main/java/com/example/workers/VideoDownloadService.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('errorMessage = finalException?.message)\n', 'errorMessage = finalException?.message))\n')
c = c.replace('startedAt = System.currentTimeMillis())\n', 'startedAt = System.currentTimeMillis()))\n')
# Wait, currently it has System.currentTimeMillis() \n
c = c.replace('startedAt = System.currentTimeMillis())\n', 'startedAt = System.currentTimeMillis()))\n')
# Let's just do a clean replacement
with open(path, 'w') as f:
    f.write(c)

