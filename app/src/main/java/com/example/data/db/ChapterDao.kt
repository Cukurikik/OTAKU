package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.models.ChapterEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId ORDER BY number DESC")
    fun getChapters(profileId: Long, sourceId: String, mangaId: String): Flow<List<ChapterEntry>>
    
    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId ORDER BY number DESC")
    suspend fun getChaptersList(profileId: Long, sourceId: String, mangaId: String): List<ChapterEntry>

    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun getChapter(profileId: Long, sourceId: String, mangaId: String, chapterId: String): ChapterEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(item: ChapterEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(items: List<ChapterEntry>)

    @Update
    suspend fun updateChapter(item: ChapterEntry)

    @Query("UPDATE chapter_entries SET lastPageRead = :lastPageRead, lastReadAt = :lastReadAt, read = :isRead WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun updateProgress(profileId: Long, sourceId: String, mangaId: String, chapterId: String, lastPageRead: Int, lastReadAt: Long, isRead: Boolean)

    @androidx.room.Delete
    suspend fun deleteChapters(items: List<ChapterEntry>)

    @Query("SELECT COUNT(*) FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND read = 0")
    suspend fun getUnreadCount(profileId: Long, sourceId: String, mangaId: String): Int

    @Query("UPDATE chapter_entries SET read = 1 WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId")
    suspend fun markAllChaptersAsRead(profileId: Long, sourceId: String, mangaId: String)

    @Query("SELECT sourceId FROM chapter_entries WHERE profileId = :profileId AND read = 1")
    fun getReadChaptersSourceIds(profileId: Long): Flow<List<String>>

    @Query("UPDATE chapter_entries SET read = :read WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun updateChapterReadStatus(profileId: Long, sourceId: String, mangaId: String, chapterId: String, read: Boolean)

    @androidx.room.Transaction
    suspend fun refreshChapters(
        insertList: List<ChapterEntry>,
        deleteList: List<ChapterEntry>
    ) {
        insertChapters(insertList)
        if (deleteList.isNotEmpty()) {
            deleteChapters(deleteList)
        }
    }

    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId")
    suspend fun getAllChaptersSync(profileId: Long): List<ChapterEntry>

    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId")
    fun getAllChaptersFlow(profileId: Long): Flow<List<ChapterEntry>>

    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId AND read = 1")
    fun getReadChaptersFlow(profileId: Long): Flow<List<ChapterEntry>>

    @Query("SELECT * FROM chapter_entries WHERE profileId = :profileId AND read = 1")
    suspend fun getReadChaptersSync(profileId: Long): List<ChapterEntry>
}
