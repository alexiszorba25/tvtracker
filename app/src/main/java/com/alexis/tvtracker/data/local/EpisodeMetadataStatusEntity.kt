package com.alexis.tvtracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episode_metadata_status")
data class EpisodeMetadataStatusEntity(
    @PrimaryKey val showId: Int,
    val complete: Boolean,
    val lastSeasonNumber: Int? = null,
    val totalSeasons: Int? = null,
    val updatedAtMillis: Long = 0L,
)
