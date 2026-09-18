package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.models.LibraryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_entries WHERE profileId = :profileId ORDER BY title ASC")
    fun getAllLibrary(profileId: Long): Flow<List<LibraryEntry>>
    @Query("SELECT * FROM library_entries WHERE profileId = :profileId ORDER BY title ASC")
    suspend fun getAllEntriesSync(profileId: Long): List<LibraryEntry>
    
    @Query("SELECT * FROM library_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId")
    suspend fun getLibraryEntry(profileId: Long, sourceId: String, mangaId: String): LibraryEntry?

    @Query("SELECT * FROM library_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId")
    fun observeLibraryEntry(profileId: Long, sourceId: String, mangaId: String): Flow<LibraryEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryEntry(item: LibraryEntry)

    @Query("DELETE FROM library_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId")
    suspend fun deleteLibraryEntry(profileId: Long, sourceId: String, mangaId: String)

    @Query("UPDATE library_entries SET unreadCount = (SELECT COUNT(*) FROM chapter_entries WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND read = 0), lastUpdated = :lastUpdated WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId")
    suspend fun updateUnreadCount(profileId: Long, sourceId: String, mangaId: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE library_entries SET unreadCount = (SELECT COUNT(*) FROM chapter_entries WHERE profileId = library_entries.profileId AND sourceId = library_entries.sourceId AND mangaId = library_entries.mangaId AND read = 0), lastUpdated = :lastUpdated WHERE profileId = :profileId")
    suspend fun updateAllUnreadCounts(profileId: Long, lastUpdated: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(items: List<LibraryEntry>)
}
