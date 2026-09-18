path = 'app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('targetSrc.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'targetSrc.getChapters(')
with open(path, 'w') as f:
    f.write(c)

path = 'app/src/main/java/com/example/data/sources/anime/GenericAnimeScraper.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('source.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'source.getChapters(')
c = c.replace('ext.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'ext.getChapters(')
with open(path, 'w') as f:
    f.write(c)

path = 'app/src/main/java/com/example/workers/VideoDownloadService.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('errorMessage = finalException?.message))', 'errorMessage = finalException?.message)')
with open(path, 'w') as f:
    f.write(c)

