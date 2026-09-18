path = 'app/src/main/java/com/example/ui/viewmodels/LibraryViewModel.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('System.currentTimeMillis())\n', 'System.currentTimeMillis()))\n')

with open(path, 'w') as f:
    f.write(c)

