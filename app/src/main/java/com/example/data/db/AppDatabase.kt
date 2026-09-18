package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.models.ChapterEntry
import com.example.data.models.DownloadEntity
import com.example.data.models.PageDownloadEntity
import com.example.data.models.HistoryItem
import com.example.data.models.LibraryEntry
import com.example.data.models.CategoryEntry

import com.example.data.models.TrackEntity
import com.example.data.models.UpdateEntity
import com.example.data.models.UserProfile

@Database(entities = [HistoryItem::class, LibraryEntry::class, ChapterEntry::class, DownloadEntity::class, PageDownloadEntity::class, CategoryEntry::class, TrackEntity::class, UpdateEntity::class, UserProfile::class, com.example.data.models.PendingSyncEntity::class], version = 16, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun libraryDao(): LibraryDao
    abstract fun chapterDao(): ChapterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun pageDownloadDao(): PageDownloadDao
    abstract fun categoryDao(): CategoryDao
    abstract fun trackDao(): TrackDao
    abstract fun updateDao(): UpdateDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun pendingSyncsDao(): PendingSyncDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_entries` (`sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `addedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER, `favorite` INTEGER NOT NULL, `categoryId` INTEGER, PRIMARY KEY(`sourceId`, `mangaId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_entries` (`sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `dateUpload` INTEGER, `number` REAL, `read` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `lastReadAt` INTEGER, `downloaded` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `mangaId`, `chapterId`))"
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `status` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `filePath` TEXT, PRIMARY KEY(`sourceId`, `mangaId`, `chapterId`))"
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Not migrating anything for now except enabling exportSchema and keeping the same schema
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop old downloads table and create new one, since this is alpha and we can afford data loss for downloads
                db.execSQL("DROP TABLE IF EXISTS `downloads`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `state` TEXT NOT NULL, `priority` INTEGER NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `startedAt` INTEGER, `completedAt` INTEGER, PRIMARY KEY(`sourceId`, `mangaId`, `chapterId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `page_downloads` (`sourceId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, `remoteUrl` TEXT NOT NULL, `localUri` TEXT, `state` TEXT NOT NULL, `bytes` INTEGER NOT NULL, `checksum` TEXT, PRIMARY KEY(`sourceId`, `mangaId`, `chapterId`, `pageIndex`), FOREIGN KEY(`sourceId`, `mangaId`, `chapterId`) REFERENCES `downloads`(`sourceId`, `mangaId`, `chapterId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_downloads_sourceId_mangaId_chapterId` ON `page_downloads` (`sourceId`, `mangaId`, `chapterId`)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL)"
                )
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `status` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `unreadCount` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` TEXT NOT NULL, `trackerId` TEXT NOT NULL, `remoteId` INTEGER NOT NULL, `title` TEXT NOT NULL, `lastChapterRead` REAL NOT NULL, `totalChapters` INTEGER NOT NULL, `status` INTEGER NOT NULL, `score` REAL NOT NULL)"
                )
            }
        }
        
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `updates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, `title` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `thumbnailUrl` TEXT, `dateFetch` INTEGER NOT NULL, `read` INTEGER NOT NULL)"
                )
            }
        }
        
        val MIGRATION_10_11 = object : Migration(10, 11) {
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
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `lastUpdated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `history_items` ADD COLUMN `lastUpdated` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_entries_profileId` ON `library_entries` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_entries_sourceId_mangaId` ON `library_entries` (`sourceId`, `mangaId`)")
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_items_profileId` ON `history_items` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_items_timestamp` ON `history_items` (`timestamp`)")
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_entries_profileId` ON `chapter_entries` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_entries_sourceId_mangaId` ON `chapter_entries` (`sourceId`, `mangaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_entries_profileId_sourceId_mangaId` ON `chapter_entries` (`profileId`, `sourceId`, `mangaId`)")
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_profileId` ON `downloads` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_sourceId_mangaId_chapterId` ON `downloads` (`sourceId`, `mangaId`, `chapterId`)")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `profileId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_profileId` ON `tracks` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_profileId_mangaId` ON `tracks` (`profileId`, `mangaId`)")
            }
        }
        
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_syncs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `trackerId` TEXT NOT NULL, `remoteId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `progress` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `directUrl` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `headersJson` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `cookies` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `mimeType` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `qualityLabel` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subtitlesJson` TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_sync_actions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaId` TEXT NOT NULL, `trackerType` TEXT NOT NULL, `progress` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, `remoteId` INTEGER NOT NULL, `itemId` TEXT NOT NULL)"
                )
                try {
                    db.execSQL(
                        "INSERT INTO `pending_sync_actions` (`id`, `mediaId`, `trackerType`, `progress`, `updatedAt`, `profileId`, `remoteId`, `itemId`) SELECT `id`, `itemId`, `trackerId`, `progress`, strftime('%s','now') * 1000, `profileId`, `remoteId`, `itemId` FROM `pending_syncs`"
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
