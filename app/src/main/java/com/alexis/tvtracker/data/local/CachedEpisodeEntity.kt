package com.alexis.tvtracker.data.local

import androidx.room.Entity

@Entity(
    tableName = "cached_episodes",
    primaryKeys = ["showId", "seasonNumber", "episodeNumber"],
)
data class CachedEpisodeEntity(
    val showId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val airDate: String?,
)
