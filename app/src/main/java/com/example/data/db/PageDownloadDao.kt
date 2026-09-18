package com.example.data.db

import androidx.room.*
import com.example.data.models.PageDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDownloadDao {
    @Query("SELECT * FROM page_downloads WHERE sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId ORDER BY pageIndex ASC")
    fun getPages(sourceId: String, mangaId: String, chapterId: String): Flow<List<PageDownloadEntity>>

    @Query("SELECT * FROM page_downloads WHERE sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId ORDER BY pageIndex ASC")
    suspend fun getPagesList(sourceId: String, mangaId: String, chapterId: String): List<PageDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageDownloadEntity>)

    @Update
    suspend fun updatePage(page: PageDownloadEntity)
    
    @Delete
    suspend fun deletePages(pages: List<PageDownloadEntity>)

    @Query("DELETE FROM page_downloads WHERE sourceId = :sourceId AND mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun deletePagesByChapter(sourceId: String, mangaId: String, chapterId: String)

    @Query("SELECT * FROM page_downloads")
    suspend fun getAllPageDownloadsSync(): List<PageDownloadEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageDownloads(items: List<PageDownloadEntity>)
}
