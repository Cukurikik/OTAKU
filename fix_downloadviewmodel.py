import re

with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'r') as f:
    content = f.read()

wrong = '''                previousBytesMap.remove(downloadIdKey)
                lastRefreshTimeMap.remove(downloadIdKey)
            } else {'''

fixed = '''                previousBytesMap.remove(downloadIdKey)
                lastRefreshTimeMap.remove(downloadIdKey)
            }
            } else {'''

content = content.replace(wrong, fixed)

with open('app/src/main/java/com/example/ui/viewmodels/DownloadViewModel.kt', 'w') as f:
    f.write(content)
