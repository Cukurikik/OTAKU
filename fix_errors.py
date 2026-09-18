import os, re

def fix_file(path):
    with open(path, 'r') as f:
        c = f.read()
    orig = c

    # Fix syntax errors (missing closing parenthesis)
    c = c.replace('downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis())\n', 'downloadDao.updateDownload(entity.copy(state = "DOWNLOADED", completedAt = System.currentTimeMillis()))\n')
    
    # Check DetailViewModel.kt
    c = c.replace('libraryDao.insertLibraryEntry(entry.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis())\n', 'libraryDao.insertLibraryEntry(entry.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis()))\n')
    
    # Check LibraryViewModel.kt
    c = c.replace('libraryDao.insertLibraryEntry(it.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis())\n', 'libraryDao.insertLibraryEntry(it.copy(categoryId = categoryId, lastUpdated = System.currentTimeMillis()))\n')

    # Fix API getLibraryEntry
    c = c.replace('api.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, ', 'api.getLibraryEntry(')
    
    # Fix ContentRepository getChapters
    c = c.replace('repository.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'repository.getChapters(')
    c = c.replace('source.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'source.getChapters(')
    c = c.replace('ext.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'ext.getChapters(')
    c = c.replace('targetSrc.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ', 'targetSrc.getChapters(')
    c = c.replace('manager.getDownload(com.example.data.ProfileManager.currentProfileIdValue, ', 'manager.getDownload(')
    
    if orig != c:
        with open(path, 'w') as f:
            f.write(c)

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            fix_file(os.path.join(root, file))

