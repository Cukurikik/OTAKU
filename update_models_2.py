import re

files = [
    'app/src/main/java/com/example/data/models/LibraryModels.kt',
    'app/src/main/java/com/example/data/models/TrackModels.kt',
    'app/src/main/java/com/example/data/models/UpdateEntity.kt',
    'app/src/main/java/com/example/data/models/DownloadEntity.kt',
    'app/src/main/java/com/example/data/models/PageDownloadEntity.kt',
    'app/src/main/java/com/example/data/models/CategoryEntry.kt'
]

for f in files:
    with open(f, 'r') as file:
        content = file.read()
    
    if 'import kotlinx.serialization.Serializable' not in content:
        content = content.replace('import androidx.room.Entity', 'import androidx.room.Entity\nimport kotlinx.serialization.Serializable')

    # Add @Serializable before data class if not present
    # we can just find 'data class' and if it doesn't have @Serializable before it, add it
    
    content = re.sub(r'(?<!@Serializable\n)(data class)', r'@Serializable\n\1', content)
    
    with open(f, 'w') as file:
        file.write(content)
