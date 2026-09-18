package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.models.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_items WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getAllHistory(profileId: Long): Flow<List<HistoryItem>>
    @Query("SELECT * FROM history_items WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getAllHistorySync(profileId: Long): List<HistoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryItem)

    @Query("DELETE FROM history_items WHERE profileId = :profileId AND id = :id")
    suspend fun deleteHistoryById(profileId: Long, id: String)

    @Query("SELECT * FROM history_items WHERE profileId = :profileId AND id = :id LIMIT 1")
    fun getHistoryItemSync(profileId: Long, id: String): HistoryItem?
    
    @Query("DELETE FROM history_items WHERE profileId = :profileId")
    suspend fun deleteAllHistory(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(items: List<HistoryItem>)
}
