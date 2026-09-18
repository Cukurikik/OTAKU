import re

with open('app/src/main/java/com/example/workers/MangaDownloadWorker.kt', 'r') as f:
    content = f.read()

integrity_old = '''                }
                
                if (tempCbzFile.renameTo(cbzFile)) {
                    // Success, clean up staging'''
integrity_new = '''                }
                
                if (tempCbzFile.length() < 1024) { // Basic integrity check: at least 1KB
                    throw Exception("CBZ file is too small, likely corrupt")
                }
                
                if (tempCbzFile.renameTo(cbzFile)) {
                    // Success, clean up staging'''
content = content.replace(integrity_old, integrity_new)

with open('app/src/main/java/com/example/workers/MangaDownloadWorker.kt', 'w') as f:
    f.write(content)
