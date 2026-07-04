package com.alexis.tvtracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM watched_episodes WHERE showId = :showId")
    fun observeWatchedEpisodes(showId: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes")
    fun observeAllWatchedEpisodes(): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes")
    suspend fun getAllWatchedEpisodes(): List<WatchedEpisodeEntity>

    @Query("SELECT * FROM cached_episodes")
    fun observeCachedEpisodes(): Flow<List<CachedEpisodeEntity>>

    @Upsert
    suspend fun upsert(episode: WatchedEpisodeEntity)

    @Upsert
    suspend fun upsertAll(episodes: List<WatchedEpisodeEntity>)

    @Upsert
    suspend fun upsertCachedEpisodes(episodes: List<CachedEpisodeEntity>)

    @Query("DELETE FROM watched_episodes WHERE showId = :showId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun delete(showId: Int, seasonNumber: Int, episodeNumber: Int)

    @Query("DELETE FROM watched_episodes WHERE showId = :showId AND seasonNumber = :seasonNumber")
    suspend fun deleteSeason(showId: Int, seasonNumber: Int)
}
