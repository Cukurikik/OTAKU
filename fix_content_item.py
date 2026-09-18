path = 'app/src/main/java/com/example/data/models/ContentItem.kt'
with open(path, 'r') as f:
    c = f.read()

c = c.replace('@Serializable\n@Entity(tableName = "history_items")\n@Entity(\n    tableName = "history_items",\n    primaryKeys = ["profileId", "id"]\n)\n@Serializable\ndata class HistoryItem', '@Entity(\n    tableName = "history_items",\n    primaryKeys = ["profileId", "id"]\n)\n@Serializable\ndata class HistoryItem')

with open(path, 'w') as f:
    f.write(c)
