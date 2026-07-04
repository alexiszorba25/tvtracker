package com.alexis.tvtracker.data

import com.alexis.tvtracker.data.local.EpisodeDao
import com.alexis.tvtracker.data.local.CachedEpisodeEntity
import com.alexis.tvtracker.data.local.WatchedEpisodeEntity
import com.alexis.tvtracker.data.remote.TmdbEpisode
import kotlinx.coroutines.flow.Flow

class EpisodeRepository(private val dao: EpisodeDao) {
    fun observeWatchedEpisodes(showId: Int): Flow<List<WatchedEpisodeEntity>> {
        return dao.observeWatchedEpisodes(showId)
    }

    fun observeAllWatchedEpisodes(): Flow<List<WatchedEpisodeEntity>> {
        return dao.observeAllWatchedEpisodes()
    }

    suspend fun getAllWatchedEpisodes(): List<WatchedEpisodeEntity> {
        return dao.getAllWatchedEpisodes()
    }

    fun observeCachedEpisodes(): Flow<List<CachedEpisodeEntity>> {
        return dao.observeCachedEpisodes()
    }

    suspend fun cacheEpisodes(showId: Int, seasonNumber: Int, episodes: List<TmdbEpisode>) {
        dao.upsertCachedEpisodes(
            episodes.map { episode ->
                CachedEpisodeEntity(
                    showId = showId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    title = episode.name,
                    airDate = episode.airDate,
                )
            },
        )
    }

    suspend fun markEpisode(showId: Int, seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        if (watched) {
            dao.upsert(
                WatchedEpisodeEntity(
                    showId = showId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                ),
            )
        } else {
            dao.delete(showId, seasonNumber, episodeNumber)
        }
    }

    suspend fun markSeason(showId: Int, seasonNumber: Int, episodeNumbers: List<Int>, watched: Boolean) {
        if (watched) {
            dao.upsertAll(
                episodeNumbers.map { episodeNumber ->
                    WatchedEpisodeEntity(
                        showId = showId,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                    )
                },
            )
        } else {
            dao.deleteSeason(showId, seasonNumber)
        }
    }
}
