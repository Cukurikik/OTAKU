import re

def insert_profileId(path, pattern, replacement):
    with open(path, 'r') as f:
        c = f.read()
    c = c.replace(pattern, replacement)
    with open(path, 'w') as f:
        f.write(c)

insert_profileId('app/src/main/java/com/example/data/LibraryUpdateWorker.kt', 
                 'ChapterEntry(', 
                 'ChapterEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/ReaderViewModel.kt', 
                 'ChapterEntry(', 
                 'ChapterEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/DetailViewModel.kt', 
                 'ChapterEntry(', 
                 'ChapterEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

insert_profileId('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 
                 'ChapterEntry(', 
                 'ChapterEntry(profileId = com.example.data.ProfileManager.currentProfileIdValue, ')

