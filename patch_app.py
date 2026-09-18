path = 'app/src/main/java/com/example/OtakuHubApp.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace('AppDatabase.MIGRATION_9_10', 'AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11')

with open(path, 'w') as f:
    f.write(content)

print("Updated OtakuHubApp")
