import re

with open('app/src/main/java/com/example/ui/viewmodels/ReaderViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'val imagePath = p.localUri?.let { "file://$it" } ?: p.remoteUrl',
    'val imagePath = p.localUri?.let { if (it.startsWith("cbz://")) it else "file://$it" } ?: p.remoteUrl'
)

with open('app/src/main/java/com/example/ui/viewmodels/ReaderViewModel.kt', 'w') as f:
    f.write(text)
