path = 'app/src/main/java/com/example/data/db/AppDatabase.kt'
with open(path, 'r') as f:
    content = f.read()

replacements = [
    ('import com.example.data.models.UpdateEntity', 'import com.example.data.models.UpdateEntity\nimport com.example.data.models.UserProfile'),
    ('entities = [HistoryItem::class, LibraryEntry::class, ChapterEntry::class, DownloadEntity::class, PageDownloadEntity::class, CategoryEntry::class, TrackEntity::class, UpdateEntity::class]', 'entities = [HistoryItem::class, LibraryEntry::class, ChapterEntry::class, DownloadEntity::class, PageDownloadEntity::class, CategoryEntry::class, TrackEntity::class, UpdateEntity::class, UserProfile::class]'),
    ('version = 10', 'version = 11'),
    ('abstract fun updateDao(): UpdateDao', 'abstract fun updateDao(): UpdateDao\n    abstract fun userProfileDao(): UserProfileDao'),
    ('        val MIGRATION_9_10 = object : Migration(9, 10) {', '''        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // To avoid complex migrations, we will simply drop and recreate the tables that need profileId
                // because SQLite does not support adding primary keys. 
                // But wait, actually destructive migration is not allowed. We MUST copy data!
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `avatarUrl` TEXT, `pin` TEXT, `isKidsMode` INTEGER NOT NULL, `isPinProtected` INTEGER NOT NULL)")
                
                // Add default profile
                db.execSQL("INSERT INTO `user_profiles` (`id`, `name`, `isKidsMode`, `isPinProtected`) VALUES (1, 'Main Profile', 0, 0)")
                
                // For simplicity, we just add the profileId column to tables. But wait!
                // To change primary keys, we have to create new tables, copy data, and drop old ones.
                
                // Library
                db.execSQL("CREATE TABLE IF NOT EXISTS `library_entries_new` (`profileId` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `addedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER, `favorite` INTEGER NOT NULL, `categoryId` INTEGER, `status` TEXT NOT NULL, `unreadCount` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `sourceId`, `mangaId`))")
                db.execSQL("INSERT INTO `library_entries_new` (`profileId`, `sourceId`, `mangaId`, `title`, `thumbnailUrl`, `addedAt`, `lastUpdatedAt`, `favorite`, `categoryId`, `status`, `unreadCount`, `lastUpdated`) SELECT 1, `sourceId`, `mangaId`, `title`, `thumbnailUrl`, `addedAt`, `lastUpdatedAt`, `favorite`, `categoryId`, `status`, `unreadCount`, `lastUpdated` FROM `library_entries`")
                db.execSQL("DROP TABLE `library_entries`")
                db.execSQL("ALTER TABLE `library_entries_new` RENAME TO `library_entries`")
                
                // Chapter
                db.execSQL("CREATE TABLE IF NOT EXISTS `chapter_entries_new` (`profileId` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `dateUpload` INTEGER, `number` REAL, `read` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `lastReadAt` INTEGER, `downloaded` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `sourceId`, `mangaId`, `chapterId`))")
                db.execSQL("INSERT INTO `chapter_entries_new` SELECT 1, * FROM `chapter_entries`")
                db.execSQL("DROP TABLE `chapter_entries`")
                db.execSQL("ALTER TABLE `chapter_entries_new` RENAME TO `chapter_entries`")
                
                // History
                db.execSQL("CREATE TABLE IF NOT EXISTS `history_items_new` (`profileId` INTEGER NOT NULL, `id` TEXT NOT NULL, `title` TEXT NOT NULL, `source` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `type` TEXT NOT NULL, `progress` REAL NOT NULL, `lastChapterOrEpisode` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `status` TEXT NOT NULL, `year` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `id`))")
                db.execSQL("INSERT INTO `history_items_new` SELECT 1, * FROM `history_items`")
                db.execSQL("DROP TABLE `history_items`")
                db.execSQL("ALTER TABLE `history_items_new` RENAME TO `history_items`")
                
                // Download
                db.execSQL("CREATE TABLE IF NOT EXISTS `downloads_new` (`profileId` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `state` TEXT NOT NULL, `priority` INTEGER NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, PRIMARY KEY(`profileId`, `sourceId`, `mangaId`, `chapterId`))")
                db.execSQL("INSERT INTO `downloads_new` SELECT 1, * FROM `downloads`")
                db.execSQL("DROP TABLE `downloads`")
                db.execSQL("ALTER TABLE `downloads_new` RENAME TO `downloads`")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {''')
]

for old, new in replacements:
    content = content.replace(old, new)

with open(path, 'w') as f:
    f.write(content)

print("Updated AppDatabase")
