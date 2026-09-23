package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.models.AnimeMalMapping

@Dao
interface AnimeMalMappingDao {
    @Query("SELECT malId FROM anime_mal_mappings WHERE cleanTitle = :cleanTitle LIMIT 1")
    suspend fun getMalIdByTitle(cleanTitle: String): Long?

    @Query("SELECT * FROM anime_mal_mappings WHERE cleanTitle = :cleanTitle LIMIT 1")
    suspend fun getMappingByTitle(cleanTitle: String): AnimeMalMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: AnimeMalMapping)

    @Query("DELETE FROM anime_mal_mappings WHERE cleanTitle = :cleanTitle")
    suspend fun deleteMapping(cleanTitle: String)

    @Query("SELECT * FROM anime_mal_mappings")
    suspend fun getAllMappings(): List<AnimeMalMapping>
}
