package com.alexis.tvtracker.metadata

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alexis.tvtracker.data.AppContainer
import com.alexis.tvtracker.model.MediaType
import kotlinx.coroutines.CancellationException

class EpisodeMetadataWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        if (!container.apiKeyRepository.hasApiKey()) {
            Log.w(TAG, "Skipping episode metadata cache: missing TMDb API key")
            return Result.success()
        }

        val statusesByShow = container.episodeRepository.getEpisodeMetadataStatus()
            .associateBy { it.showId }
        val completeShowIds = statusesByShow
            .filterValues { it.complete }
            .keys
        val shows = container.libraryRepository.getLibrary()
            .filter { it.mediaType == MediaType.Tv && it.tmdbId !in completeShowIds }

        Log.i(TAG, "Caching episode metadata for ${shows.size} shows")
        setProgress(
            workDataOf(
                PROGRESS_PROCESSED_SHOWS to 0,
                PROGRESS_TOTAL_SHOWS to shows.size,
                PROGRESS_CURRENT_SHOW to "",
            ),
        )

        shows.forEachIndexed { index, show ->
            if (isStopped) return Result.retry()
            setProgress(
                workDataOf(
                    PROGRESS_PROCESSED_SHOWS to index,
                    PROGRESS_TOTAL_SHOWS to shows.size,
                    PROGRESS_CURRENT_SHOW to show.title,
                ),
            )
            try {
                val details = container.tmdbRepository.getTvDetails(show.tmdbId)
                val seasons = details.seasons
                    .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                    .sortedBy { it.seasonNumber }
                val savedSeason = statusesByShow[show.tmdbId]?.lastSeasonNumber ?: 0
                val cachedSeason = container.episodeRepository.getMaxCachedSeasonNumber(show.tmdbId) ?: 0
                var lastSeasonNumber = maxOf(savedSeason, cachedSeason)
                container.episodeRepository.setEpisodeMetadataProgress(
                    showId = show.tmdbId,
                    lastSeasonNumber = lastSeasonNumber.takeIf { it > 0 },
                    totalSeasons = seasons.size,
                )

                seasons
                    .filter { it.seasonNumber > lastSeasonNumber }
                    .forEach { season ->
                        if (isStopped) return Result.retry()
                        val seasonDetails = container.tmdbRepository.getSeason(show.tmdbId, season.seasonNumber)
                        container.episodeRepository.cacheEpisodes(
                            showId = show.tmdbId,
                            seasonNumber = season.seasonNumber,
                            episodes = seasonDetails.episodes,
                        )
                        lastSeasonNumber = season.seasonNumber
                        container.episodeRepository.setEpisodeMetadataProgress(
                            showId = show.tmdbId,
                            lastSeasonNumber = lastSeasonNumber,
                            totalSeasons = seasons.size,
                        )
                    }
                container.episodeRepository.setEpisodeMetadataProgress(
                    showId = show.tmdbId,
                    lastSeasonNumber = seasons.lastOrNull()?.seasonNumber,
                    totalSeasons = seasons.size,
                    complete = true,
                )
                Log.d(TAG, "Cached episode metadata for ${show.title} (${show.tmdbId})")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to cache episode metadata for ${show.title} (${show.tmdbId})", error)
            }
            setProgress(
                workDataOf(
                    PROGRESS_PROCESSED_SHOWS to index + 1,
                    PROGRESS_TOTAL_SHOWS to shows.size,
                    PROGRESS_CURRENT_SHOW to show.title,
                ),
            )
        }

        Log.i(TAG, "Finished episode metadata cache")
        return Result.success()
    }
}

fun enqueueEpisodeMetadataWork(
    context: Context,
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    val request = OneTimeWorkRequestBuilder<EpisodeMetadataWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        EPISODE_METADATA_WORK_NAME,
        policy,
        request,
    )
}

const val EPISODE_METADATA_WORK_NAME = "episode-metadata-cache"
const val PROGRESS_CURRENT_SHOW = "currentShow"
const val PROGRESS_PROCESSED_SHOWS = "processedShows"
const val PROGRESS_TOTAL_SHOWS = "totalShows"

private const val TAG = "EpisodeMetadataWorker"
