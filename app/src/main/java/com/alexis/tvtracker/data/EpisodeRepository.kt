package com.alexis.tvtracker.data

import com.alexis.tvtracker.data.local.EpisodeDao
import com.alexis.tvtracker.data.local.CachedEpisodeEntity
import com.alexis.tvtracker.data.local.EpisodeMetadataStatusEntity
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

    fun observeEpisodeMetadataStatus(): Flow<List<EpisodeMetadataStatusEntity>> {
        return dao.observeEpisodeMetadataStatus()
    }

    suspend fun getEpisodeMetadataStatus(): List<EpisodeMetadataStatusEntity> {
        return dao.getEpisodeMetadataStatus()
    }

    suspend fun getCachedEpisodes(): List<CachedEpisodeEntity> {
        return dao.getCachedEpisodes()
    }

    suspend fun getMaxCachedSeasonNumber(showId: Int): Int? {
        return dao.getMaxCachedSeasonNumber(showId)
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

    suspend fun setEpisodeMetadataComplete(showId: Int, complete: Boolean) {
        val now = System.currentTimeMillis()
        dao.upsertMetadataStatus(
            EpisodeMetadataStatusEntity(
                showId = showId,
                complete = complete,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun setEpisodeMetadataProgress(
        showId: Int,
        lastSeasonNumber: Int?,
        totalSeasons: Int,
        complete: Boolean = false,
    ) {
        dao.upsertMetadataStatus(
            EpisodeMetadataStatusEntity(
                showId = showId,
                complete = complete,
                lastSeasonNumber = lastSeasonNumber,
                totalSeasons = totalSeasons,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun markEpisode(
        showId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        watched: Boolean,
        watchedAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (watched) {
            dao.upsert(
                WatchedEpisodeEntity(
                    showId = showId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    watchedAtMillis = watchedAtMillis,
                ),
            )
        } else {
            dao.delete(showId, seasonNumber, episodeNumber)
        }
    }

    suspend fun markEpisodes(
        showId: Int,
        episodes: List<WatchedEpisodeEntity>,
    ) {
        dao.upsertAll(episodes.map { it.copy(showId = showId) })
    }

    suspend fun markSeason(showId: Int, seasonNumber: Int, episodeNumbers: List<Int>, watched: Boolean) {
        if (watched) {
            dao.upsertAll(
                episodeNumbers.map { episodeNumber ->
                    WatchedEpisodeEntity(
                        showId = showId,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        watchedAtMillis = System.currentTimeMillis(),
                    )
                },
            )
        } else {
            dao.deleteSeason(showId, seasonNumber)
        }
    }
}
