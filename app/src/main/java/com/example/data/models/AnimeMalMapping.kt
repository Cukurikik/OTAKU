package com.example.data.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Entity(tableName = "anime_mal_mappings")
data class AnimeMalMapping(
    @PrimaryKey val cleanTitle: String,
    val malId: Long,
    val animeTitle: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
