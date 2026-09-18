import re

with open('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 'r') as f:
    content = f.read()

# Replace toSrc.sourceId with targetSrc.sourceId in chapterEntities mapping
content = content.replace("sourceId = toSrc.sourceId,", "sourceId = targetSrc.sourceId,")

# Also for newHistory and libraryDao.deleteLibraryEntry - wait, deleteLibraryEntry uses entry.sourceId which is correct
content = content.replace("sourceId = toSrc.sourceId", "sourceId = targetSrc.sourceId")
content = content.replace("source = toSrc.sourceName", "source = targetSrc.sourceName")

with open('app/src/main/java/com/example/ui/viewmodels/MigrationViewModel.kt', 'w') as f:
    f.write(content)
