import re

def fix(path):
    with open(path, 'r') as f:
        c = f.read()
    
    # DetailViewModel.kt
    c = c.replace('System.currentTimeMillis(.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue))))', 'System.currentTimeMillis())))')
    c = c.replace('libraryDao.insertLibraryEntry(\n', 'libraryDao.insertLibraryEntry(') # just in case
    
    with open(path, 'w') as f:
        f.write(c)

fix('app/src/main/java/com/example/ui/viewmodels/DetailViewModel.kt')
fix('app/src/main/java/com/example/ui/viewmodels/LibraryViewModel.kt')
