import os, re

def fix(path):
    with open(path, 'r') as f:
        c = f.read()
    orig = c

    # libraryDao
    c = c.replace('libraryDao.observeLibraryEntry(', 'libraryDao.observeLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('libraryDao.getLibraryEntry(', 'libraryDao.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('libraryDao.deleteLibraryEntry(', 'libraryDao.deleteLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('libraryDao.updateUnreadCount(', 'libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('libraryDao.updateAllUnreadCounts(', 'libraryDao.updateAllUnreadCounts(com.example.data.ProfileManager.currentProfileIdValue') # may have no args
    c = c.replace('libraryDao.getAllLibrary(', 'libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('libraryDao.getAllEntriesSync(', 'libraryDao.getAllEntriesSync(com.example.data.ProfileManager.currentProfileIdValue')
    
    # historyDao
    c = c.replace('historyDao.getAllHistory(', 'historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('historyDao.getAllHistorySync(', 'historyDao.getAllHistorySync(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('historyDao.getHistoryItemSync(', 'historyDao.getHistoryItemSync(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('historyDao.deleteHistoryById(', 'historyDao.deleteHistoryById(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('historyDao.deleteAllHistory(', 'historyDao.deleteAllHistory(com.example.data.ProfileManager.currentProfileIdValue')
    
    # downloadDao
    c = c.replace('downloadDao.getAllDownloads(', 'downloadDao.getAllDownloads(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('downloadDao.getPendingDownloads(', 'downloadDao.getPendingDownloads(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('downloadDao.getAllDownloadsSync(', 'downloadDao.getAllDownloadsSync(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('downloadDao.getDownload(', 'downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, ')
    
    # chapterDao
    c = c.replace('chapterDao.getChapters(', 'chapterDao.getChapters(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.getChaptersList(', 'chapterDao.getChaptersList(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.getChapter(', 'chapterDao.getChapter(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.updateProgress(', 'chapterDao.updateProgress(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.getUnreadCount(', 'chapterDao.getUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.markAllChaptersAsRead(', 'chapterDao.markAllChaptersAsRead(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.updateChapterReadStatus(', 'chapterDao.updateChapterReadStatus(com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('chapterDao.getReadChaptersSourceIds(', 'chapterDao.getReadChaptersSourceIds(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('chapterDao.getAllChaptersSync(', 'chapterDao.getAllChaptersSync(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('chapterDao.getAllChaptersFlow(', 'chapterDao.getAllChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('chapterDao.getReadChaptersFlow(', 'chapterDao.getReadChaptersFlow(com.example.data.ProfileManager.currentProfileIdValue')
    c = c.replace('chapterDao.getReadChaptersSync(', 'chapterDao.getReadChaptersSync(com.example.data.ProfileManager.currentProfileIdValue')

    # Fix empty arg functions that were matched
    c = c.replace('com.example.data.ProfileManager.currentProfileIdValue)', 'com.example.data.ProfileManager.currentProfileIdValue)') # Wait, if it had empty args, it was `getAllLibrary()` -> replaced with `getAllLibrary(com.example.data... )` which is fine.

    if orig != c:
        with open(path, 'w') as f:
            f.write(c)

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            fix(os.path.join(root, file))
