package com.example.data.db

import androidx.room.*
import com.example.data.models.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY priority DESC, createdAt ASC")
    fun getAllDownloads(profileId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun getDownload(profileId: Long, sourceId: String, mangaId: String, chapterId: String): DownloadEntity?
    
    @Query("SELECT * FROM downloads WHERE profileId = :profileId AND state IN ('QUEUE', 'DOWNLOADING') ORDER BY priority DESC, createdAt ASC")
    suspend fun getPendingDownloads(profileId: Long): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE profileId = :profileId")
    suspend fun getAllDownloadsSync(profileId: Long): List<DownloadEntity>
    
    @Query("SELECT * FROM downloads WHERE state IN ('QUEUE', 'DOWNLOADING')")
    suspend fun getActiveDownloadsAllProfiles(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloads(items: List<DownloadEntity>)
}
