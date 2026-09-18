package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.models.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE profileId = :profileId AND mangaId = :mangaId")
    fun getTracksForManga(profileId: Long, mangaId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE profileId = :profileId AND mangaId = :mangaId")
    suspend fun getTracksForMangaSync(profileId: Long, mangaId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId")
    fun getTracksForManga(mangaId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId")
    suspend fun getTracksForMangaSync(mangaId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE profileId = :profileId")
    fun getTracksForProfile(profileId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE profileId = :profileId")
    suspend fun getTracksForProfileSync(profileId: Long): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksSync(): List<TrackEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(items: List<TrackEntity>)
}
