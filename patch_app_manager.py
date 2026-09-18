path = 'app/src/main/java/com/example/OtakuHubApp.kt'
with open(path, 'r') as f:
    content = f.read()

replacements = [
    ('lateinit var repository: ContentRepository', 'lateinit var repository: ContentRepository\n    lateinit var profileManager: com.example.data.ProfileManager'),
    ('repository = ContentRepository(', 'profileManager = com.example.data.ProfileManager(this)\n        repository = ContentRepository(')
]
for old, new in replacements:
    content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)
