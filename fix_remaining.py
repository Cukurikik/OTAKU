import re

def strip(path, pattern):
    with open(path, 'r') as f:
        c = f.read()
    c = c.replace(pattern, '')
    with open(path, 'w') as f:
        f.write(c)

# GenericAnimeScraper.kt
strip('app/src/main/java/com/example/data/sources/anime/GenericAnimeScraper.kt', 'com.example.data.ProfileManager.currentProfileIdValue, ')

# MigrationViewModel.kt
strip('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 'com.example.data.ProfileManager.currentProfileIdValue, ')
