package com.alexis.tvtracker.data.local

import androidx.room.Entity

@Entity(
    tableName = "watched_episodes",
    primaryKeys = ["showId", "seasonNumber", "episodeNumber"],
)
data class WatchedEpisodeEntity(
    val showId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAtMillis: Long = 0L,
)
