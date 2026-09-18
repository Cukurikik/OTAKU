import os, glob, re

def process_file(path):
    with open(path, 'r') as f:
        content = f.read()
    
    orig = content
    
    # LibraryDao
    content = re.sub(r'libraryDao\.getAllLibrary\(\)', r'libraryDao.getAllLibrary(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'libraryDao\.getAllEntriesSync\(\)', r'libraryDao.getAllEntriesSync(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'libraryDao\.getLibraryEntry\((.*?),\s*(.*?)\)', r'libraryDao.getLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'libraryDao\.observeLibraryEntry\((.*?),\s*(.*?)\)', r'libraryDao.observeLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'libraryDao\.deleteLibraryEntry\((.*?),\s*(.*?)\)', r'libraryDao.deleteLibraryEntry(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'libraryDao\.updateUnreadCount\((.*?),\s*([^)]*)\)', r'libraryDao.updateUnreadCount(com.example.data.ProfileManager.currentProfileIdValue, \1, \2)', content)
    content = re.sub(r'libraryDao\.updateAllUnreadCounts\(\)', r'libraryDao.updateAllUnreadCounts(com.example.data.ProfileManager.currentProfileIdValue)', content)
    
    # HistoryDao
    content = re.sub(r'historyDao\.getAllHistory\(\)', r'historyDao.getAllHistory(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'historyDao\.getAllHistorySync\(\)', r'historyDao.getAllHistorySync(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'historyDao\.deleteHistoryById\((.*?)\)', r'historyDao.deleteHistoryById(com.example.data.ProfileManager.currentProfileIdValue, \1)', content)
    content = re.sub(r'historyDao\.getHistoryItemSync\((.*?)\)', r'historyDao.getHistoryItemSync(com.example.data.ProfileManager.currentProfileIdValue, \1)', content)
    content = re.sub(r'historyDao\.deleteAllHistory\(\)', r'historyDao.deleteAllHistory(com.example.data.ProfileManager.currentProfileIdValue)', content)
    
    # DownloadDao
    content = re.sub(r'downloadDao\.getAllDownloads\(\)', r'downloadDao.getAllDownloads(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'downloadDao\.getDownload\((.*?),\s*(.*?),\s*(.*?)\)', r'downloadDao.getDownload(com.example.data.ProfileManager.currentProfileIdValue, \1, \2, \3)', content)
    content = re.sub(r'downloadDao\.getPendingDownloads\(\)', r'downloadDao.getPendingDownloads(com.example.data.ProfileManager.currentProfileIdValue)', content)
    content = re.sub(r'downloadDao\.getAllDownloadsSync\(\)', r'downloadDao.getAllDownloadsSync(com.example.data.ProfileManager.currentProfileIdValue)', content)
    
    # Ensure insert item assigns profileId
    content = re.sub(r'libraryDao\.insertLibraryEntry\((.*?)\)', r'libraryDao.insertLibraryEntry(\1.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue))', content)
    content = re.sub(r'historyDao\.insertHistory\((.*?)\)', r'historyDao.insertHistory(\1.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue))', content)
    # Wait, insertHistory can take a list! 
    # Let's fix insertHistory(List) manually if needed.
    
    if orig != content:
        with open(path, 'w') as f:
            f.write(content)
        print(f"Updated {path}")

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            process_file(os.path.join(root, file))
