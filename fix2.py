import re

def insert_profileId(path, pattern, replacement):
    with open(path, 'r') as f:
        c = f.read()
    c = c.replace(pattern, replacement)
    with open(path, 'w') as f:
        f.write(c)

insert_profileId('app/src/main/java/com/example/ui/viewmodels/DetailViewModel.kt', 
                 'LibraryEntry(', 
                 'LibraryEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 
                 'LibraryEntry(', 
                 'LibraryEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/ReaderViewModel.kt', 
                 'HistoryItem(', 
                 'HistoryItem(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/PlayerViewModel.kt', 
                 'HistoryItem(', 
                 'HistoryItem(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 
                 'DownloadEntity(', 
                 'DownloadEntity(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/workers/MangaDownloadWorker.kt', 
                 'DownloadEntity(', 
                 'DownloadEntity(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/workers/VideoDownloadService.kt', 
                 'DownloadEntity(', 
                 'DownloadEntity(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/DetailViewModel.kt', 
                 'DownloadEntity(', 
                 'DownloadEntity(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

print("Fixed constructors")
