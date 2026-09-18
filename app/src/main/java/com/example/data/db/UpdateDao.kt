package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.models.UpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateDao {
    @Query("SELECT * FROM updates ORDER BY dateFetch DESC")
    fun getRecentUpdates(): Flow<List<UpdateEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(updates: List<UpdateEntity>)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(update: UpdateEntity)

    @Update
    suspend fun update(update: UpdateEntity)

    @Query("UPDATE updates SET read = :read WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun markAsRead(mangaId: String, chapterId: String, read: Boolean)

    @Query("UPDATE updates SET read = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM updates WHERE mangaId = :mangaId AND chapterId = :chapterId")
    suspend fun delete(mangaId: String, chapterId: String)
    
    @Query("DELETE FROM updates")
    suspend fun deleteAll()

    @Query("SELECT * FROM updates")
    suspend fun getAllUpdatesSync(): List<UpdateEntity>
}
