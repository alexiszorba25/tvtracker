package com.alexis.tvtracker.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.alexis.tvtracker.data.EpisodeRepository
import com.alexis.tvtracker.data.ApiKeyRepository
import com.alexis.tvtracker.data.LibraryRepository
import com.alexis.tvtracker.data.NextEpisode
import com.alexis.tvtracker.data.TmdbRepository
import com.alexis.tvtracker.data.ThemeMode
import com.alexis.tvtracker.data.TvTimeExportRepository
import com.alexis.tvtracker.data.TvTimeImportFile
import com.alexis.tvtracker.data.TvTimeImportProgress
import com.alexis.tvtracker.data.TvTimeImportRepository
import com.alexis.tvtracker.data.UiSettingsRepository
import com.alexis.tvtracker.data.local.CachedEpisodeEntity
import com.alexis.tvtracker.data.local.LibraryItemEntity
import com.alexis.tvtracker.data.local.WatchedEpisodeEntity
import com.alexis.tvtracker.importer.OUTPUT_ERROR
import com.alexis.tvtracker.importer.OUTPUT_IMPORTED_EPISODES
import com.alexis.tvtracker.importer.OUTPUT_IMPORTED_SHOWS
import com.alexis.tvtracker.importer.OUTPUT_SKIPPED_SHOWS
import com.alexis.tvtracker.importer.PROGRESS_CURRENT_SHOW as IMPORT_PROGRESS_CURRENT_SHOW
import com.alexis.tvtracker.importer.PROGRESS_PROCESSED_SHOWS as IMPORT_PROGRESS_PROCESSED_SHOWS
import com.alexis.tvtracker.importer.PROGRESS_TOTAL_SHOWS as IMPORT_PROGRESS_TOTAL_SHOWS
import com.alexis.tvtracker.importer.TV_TIME_IMPORT_WORK_NAME
import com.alexis.tvtracker.importer.enqueueTvTimeImportWork
import com.alexis.tvtracker.metadata.EPISODE_METADATA_WORK_NAME
import com.alexis.tvtracker.metadata.PROGRESS_CURRENT_SHOW
import com.alexis.tvtracker.metadata.PROGRESS_PROCESSED_SHOWS
import com.alexis.tvtracker.metadata.PROGRESS_TOTAL_SHOWS
import com.alexis.tvtracker.metadata.enqueueEpisodeMetadataWork
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.util.hasAired
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class WatchNextFilter {
    ContinueWatching,
    UpToDate,
}

data class LibraryUiState(
    val items: List<LibraryItemEntity> = emptyList(),
    val query: String = "",
    val watchNextFilter: WatchNextFilter = WatchNextFilter.ContinueWatching,
    val nextEpisodes: Map<Int, NextEpisode> = emptyMap(),
    val completeEpisodeMetadataShowIds: Set<Int> = emptySet(),
    val lastWatchedAtByShow: Map<Int, Long> = emptyMap(),
    val importing: Boolean = false,
    val importProgress: TvTimeImportProgress? = null,
    val postImportMessage: String? = null,
    val exporting: Boolean = false,
    val pendingExportZip: ByteArray? = null,
    val importMessage: String? = null,
    val tvTimeImportWork: TvTimeImportWorkState? = null,
    val episodeMetadataWork: EpisodeMetadataWorkState? = null,
    val tmdbApiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
) {
    val isImporting: Boolean
        get() = importing || tvTimeImportWork?.isActive == true

    val activeImportProgress: TvTimeImportProgress?
        get() = tvTimeImportWork?.progress ?: importProgress

    val continueWatchingItems: List<LibraryItemEntity>
        get() = filteredSeries.filter { item ->
            item.needsEpisodeMetadata || (nextEpisodes[item.tmdbId] != null && !item.hasStaleViewingHistory)
        }

    val staleWatchingItems: List<LibraryItemEntity>
        get() = filteredSeries.filter { item ->
            !item.needsEpisodeMetadata && nextEpisodes[item.tmdbId] != null && item.hasStaleViewingHistory
        }

    val upToDateItems: List<LibraryItemEntity>
        get() = filteredSeries.filter { item ->
            nextEpisodes[item.tmdbId] == null && item.tmdbId in completeEpisodeMetadataShowIds
        }

    private val filteredSeries: List<LibraryItemEntity>
        get() = items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.overview.contains(query, ignoreCase = true) ||
                item.releaseDate.orEmpty().contains(query, ignoreCase = true) ||
                item.mediaType.label.contains(query, ignoreCase = true)
            matchesQuery && item.mediaType == MediaType.Tv
        }

    private val LibraryItemEntity.hasStaleViewingHistory: Boolean
        get() {
            val lastWatchedAt = lastWatchedAtByShow[tmdbId] ?: return false
            return lastWatchedAt < System.currentTimeMillis() - STALE_WATCHING_MILLIS
        }

    val loadingEpisodeMetadataShowIds: Set<Int>
        get() = filteredSeries
            .filter { it.needsEpisodeMetadata }
            .map { it.tmdbId }
            .toSet()

    private val LibraryItemEntity.needsEpisodeMetadata: Boolean
        get() = tmdbId !in completeEpisodeMetadataShowIds && nextEpisodes[tmdbId] == null
}

data class TvTimeImportWorkState(
    val id: String,
    val status: WorkInfo.State,
    val progress: TvTimeImportProgress?,
    val importedShows: Int,
    val importedEpisodes: Int,
    val skippedShows: Int,
    val error: String?,
) {
    val isActive: Boolean
        get() = status == WorkInfo.State.ENQUEUED ||
            status == WorkInfo.State.RUNNING ||
            status == WorkInfo.State.BLOCKED

    val successMessage: String?
        get() = if (status == WorkInfo.State.SUCCEEDED) {
            "Imported $importedEpisodes episodes from $importedShows shows. Skipped $skippedShows. Watch Next will keep loading in the background."
        } else {
            null
        }

    val failureMessage: String?
        get() = if (status == WorkInfo.State.FAILED || status == WorkInfo.State.CANCELLED) {
            error ?: "TV Time import failed"
        } else {
            null
        }
}

data class EpisodeMetadataWorkState(
    val status: WorkInfo.State,
    val processedShows: Int,
    val totalShows: Int,
    val currentShow: String,
) {
    val isActive: Boolean
        get() = status == WorkInfo.State.ENQUEUED ||
            status == WorkInfo.State.RUNNING ||
            status == WorkInfo.State.BLOCKED

    val hasFailed: Boolean
        get() = status == WorkInfo.State.FAILED ||
            status == WorkInfo.State.CANCELLED
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
    private val tmdbRepository: TmdbRepository,
    private val tvTimeImportRepository: TvTimeImportRepository,
    private val tvTimeExportRepository: TvTimeExportRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val uiSettingsRepository: UiSettingsRepository,
    private val applicationContext: Context,
) : ViewModel() {
    private val filters = MutableStateFlow(LibraryUiState())
    private val consumedTvTimeImportWorkMessages = mutableSetOf<String>()
    private var libraryMetadataJob: Job? = null
    private var libraryMetadataSignature: String? = null
    private val settings = combine(
        apiKeyRepository.apiKey,
        uiSettingsRepository.themeMode,
    ) { apiKey, themeMode ->
        apiKey to themeMode
    }
    private val episodeData = combine(
        episodeRepository.observeAllWatchedEpisodes(),
        episodeRepository.observeCachedEpisodes(),
        episodeRepository.observeEpisodeMetadataStatus(),
    ) { watched, cachedEpisodes, metadataStatuses ->
        LibraryEpisodeData(
            watched = watched,
            cachedEpisodes = cachedEpisodes,
            completeEpisodeMetadataShowIds = metadataStatuses
                .filter { it.complete }
                .map { it.showId }
                .toSet(),
        )
    }
    private val episodeMetadataWork = WorkManager.getInstance(applicationContext)
        .getWorkInfosForUniqueWorkFlow(EPISODE_METADATA_WORK_NAME)
        .map { workInfos ->
            val workInfo = workInfos.firstOrNull()
            if (workInfo == null) {
                null
            } else {
                EpisodeMetadataWorkState(
                    status = workInfo.state,
                    processedShows = workInfo.progress.getInt(PROGRESS_PROCESSED_SHOWS, 0),
                    totalShows = workInfo.progress.getInt(PROGRESS_TOTAL_SHOWS, 0),
                    currentShow = workInfo.progress.getString(PROGRESS_CURRENT_SHOW).orEmpty(),
                )
            }
        }
    private val tvTimeImportWork = WorkManager.getInstance(applicationContext)
        .getWorkInfosForUniqueWorkFlow(TV_TIME_IMPORT_WORK_NAME)
        .map { workInfos ->
            val workInfo = workInfos.firstOrNull()
            if (workInfo == null) {
                null
            } else {
                val processedShows = workInfo.progress.getInt(IMPORT_PROGRESS_PROCESSED_SHOWS, 0)
                val totalShows = workInfo.progress.getInt(IMPORT_PROGRESS_TOTAL_SHOWS, 0)
                val currentShow = workInfo.progress.getString(IMPORT_PROGRESS_CURRENT_SHOW).orEmpty()
                TvTimeImportWorkState(
                    id = workInfo.id.toString(),
                    status = workInfo.state,
                    progress = if (processedShows > 0 || totalShows > 0 || currentShow.isNotBlank()) {
                        TvTimeImportProgress(
                            processedShows = processedShows,
                            totalShows = totalShows,
                            currentShow = currentShow,
                        )
                    } else {
                        null
                    },
                    importedShows = workInfo.outputData.getInt(OUTPUT_IMPORTED_SHOWS, 0),
                    importedEpisodes = workInfo.outputData.getInt(OUTPUT_IMPORTED_EPISODES, 0),
                    skippedShows = workInfo.outputData.getInt(OUTPUT_SKIPPED_SHOWS, 0),
                    error = workInfo.outputData.getString(OUTPUT_ERROR),
                )
            }
        }
    private val backgroundWork = combine(
        tvTimeImportWork,
        episodeMetadataWork,
    ) { tvTimeImportWork, episodeMetadataWork ->
        tvTimeImportWork to episodeMetadataWork
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.library,
        filters,
        episodeData,
        settings,
        backgroundWork,
    ) { library, filterState, episodeData, settings, backgroundWork ->
        val (tvTimeImportWork, episodeMetadataWork) = backgroundWork
        refreshMissingLibraryMetadata(library)
        filterState.copy(
            items = library,
            nextEpisodes = calculateNextEpisodes(library, episodeData.watched, episodeData.cachedEpisodes),
            completeEpisodeMetadataShowIds = episodeData.completeEpisodeMetadataShowIds,
            lastWatchedAtByShow = episodeData.watched
                .groupBy { it.showId }
                .mapValues { (_, episodes) -> episodes.maxOf { it.watchedAtMillis } },
            tvTimeImportWork = tvTimeImportWork,
            episodeMetadataWork = episodeMetadataWork,
            tmdbApiKey = settings.first,
            themeMode = settings.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
    }

    fun setWatchNextFilter(filter: WatchNextFilter) {
        filters.update { it.copy(watchNextFilter = filter) }
    }

    fun setWatched(id: Int, mediaType: MediaType, watched: Boolean) {
        viewModelScope.launch {
            libraryRepository.setWatched(id, mediaType, watched)
        }
    }

    fun remove(id: Int, mediaType: MediaType) {
        viewModelScope.launch {
            libraryRepository.remove(id, mediaType)
        }
    }

    fun markNextEpisodeWatched(nextEpisode: NextEpisode) {
        viewModelScope.launch {
            episodeRepository.markEpisode(
                showId = nextEpisode.showId,
                seasonNumber = nextEpisode.seasonNumber,
                episodeNumber = nextEpisode.episodeNumber,
                watched = true,
            )
        }
    }

    fun importTvTime(files: List<TvTimeImportFile>) {
        if (!apiKeyRepository.hasApiKey()) {
            filters.update { it.copy(importMessage = "Add your TMDb API key before importing TV Time data.") }
            return
        }
        viewModelScope.launch {
            filters.update {
                it.copy(
                    importing = true,
                    importProgress = null,
                    postImportMessage = null,
                    importMessage = null,
                )
            }
            runCatching {
                val fileNames = tvTimeImportRepository.savePendingImportFiles(files)
                enqueueTvTimeImportWork(applicationContext, fileNames)
            }.onSuccess {
                    filters.update {
                        it.copy(
                            importing = false,
                            importProgress = null,
                            postImportMessage = "Import started. You can leave the app; TV Time data and episode metadata will keep loading in the background.",
                            importMessage = "TV Time import started in the background.",
                        )
                    }
                }
                .onFailure { throwable ->
                    filters.update {
                        it.copy(
                            importing = false,
                            importProgress = null,
                            postImportMessage = null,
                            importMessage = throwable.message ?: "TV Time import failed",
                        )
                    }
                }
        }
    }

    fun resumeBackgroundWork() {
        val pendingFileNames = tvTimeImportRepository.pendingImportFileNames()
        if (pendingFileNames.isNotEmpty() && apiKeyRepository.hasApiKey()) {
            enqueueTvTimeImportWork(
                context = applicationContext,
                fileNames = pendingFileNames,
                policy = ExistingWorkPolicy.KEEP,
            )
        }
        enqueueEpisodeMetadataWork(applicationContext, ExistingWorkPolicy.KEEP)
    }

    fun consumeTvTimeImportWorkMessage(workId: String) {
        consumedTvTimeImportWorkMessages += workId
    }

    fun shouldShowTvTimeImportWorkMessage(workId: String): Boolean {
        return workId !in consumedTvTimeImportWorkMessages
    }

    fun clearImportMessage() {
        filters.update { it.copy(importMessage = null) }
    }

    fun clearPostImportMessage() {
        filters.update { it.copy(postImportMessage = null) }
    }

    fun prepareExport() {
        viewModelScope.launch {
            filters.update { it.copy(exporting = true, importMessage = null, pendingExportZip = null) }
            runCatching { tvTimeExportRepository.exportZip() }
                .onSuccess { zip ->
                    filters.update {
                        it.copy(
                            exporting = false,
                            pendingExportZip = zip,
                            importMessage = "Choose where to save the TV Time export zip.",
                        )
                    }
                }
                .onFailure { throwable ->
                    filters.update {
                        it.copy(
                            exporting = false,
                            importMessage = throwable.message ?: "Export failed",
                        )
                    }
                }
        }
    }

    fun consumePreparedExport() {
        filters.update { it.copy(pendingExportZip = null) }
    }

    fun exportSaved() {
        filters.update { it.copy(importMessage = "Export saved.") }
    }

    fun saveTmdbApiKey(value: String) {
        apiKeyRepository.saveTmdbApiKey(value)
        filters.update { it.copy(importMessage = "TMDb API key saved.") }
    }

    fun setThemeMode(mode: ThemeMode) {
        uiSettingsRepository.setThemeMode(mode)
    }

    private fun calculateNextEpisodes(
        items: List<LibraryItemEntity>,
        watched: List<WatchedEpisodeEntity>,
        cachedEpisodes: List<CachedEpisodeEntity>,
    ): Map<Int, NextEpisode> {
        val series = items.filter { it.mediaType == MediaType.Tv }
        if (series.isEmpty()) return emptyMap()

        val watchedByShow = watched
            .groupBy { it.showId }
            .mapValues { (_, episodes) ->
                episodes.map { it.seasonNumber to it.episodeNumber }.toSet()
            }
        val cachedByShow = cachedEpisodes
            .groupBy { it.showId }
            .mapValues { (_, episodes) ->
                episodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            }

        return series.mapNotNull { item ->
            val watchedForShow = watchedByShow[item.tmdbId].orEmpty()
            cachedByShow[item.tmdbId]
                ?.firstOrNull { episode ->
                    hasAired(episode.airDate) &&
                        (episode.seasonNumber to episode.episodeNumber) !in watchedForShow
                }
                ?.let { episode ->
                    item.tmdbId to NextEpisode(
                        showId = item.tmdbId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        title = episode.title,
                        airDate = episode.airDate,
                    )
                }
        }.toMap()
    }

    private fun refreshMissingLibraryMetadata(items: List<LibraryItemEntity>) {
        val missingItems = items.filter { it.voteAverage == null || it.cast.isNullOrBlank() }
        val signature = missingItems.joinToString { "${it.mediaType}-${it.tmdbId}" }
        if (signature == libraryMetadataSignature) return
        libraryMetadataSignature = signature
        if (missingItems.isEmpty()) return

        libraryMetadataJob?.cancel()
        libraryMetadataJob = viewModelScope.launch {
            missingItems.forEach { item ->
                val enriched = runCatching {
                    tmdbRepository.enrich(
                        com.alexis.tvtracker.model.SearchItem(
                            id = item.tmdbId,
                            mediaType = item.mediaType,
                            title = item.title,
                            overview = item.overview,
                            posterPath = item.posterPath,
                            releaseDate = item.releaseDate,
                            voteAverage = item.voteAverage,
                            cast = item.cast,
                        ),
                    )
                }.getOrNull()
                if (enriched != null) {
                    libraryRepository.updateMetadata(
                        id = item.tmdbId,
                        mediaType = item.mediaType,
                        voteAverage = enriched.voteAverage,
                        cast = enriched.cast,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            libraryRepository: LibraryRepository,
            episodeRepository: EpisodeRepository,
            tmdbRepository: TmdbRepository,
            tvTimeImportRepository: TvTimeImportRepository,
            tvTimeExportRepository: TvTimeExportRepository,
            apiKeyRepository: ApiKeyRepository,
            uiSettingsRepository: UiSettingsRepository,
            applicationContext: Context,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(
                        libraryRepository = libraryRepository,
                        episodeRepository = episodeRepository,
                        tmdbRepository = tmdbRepository,
                        tvTimeImportRepository = tvTimeImportRepository,
                        tvTimeExportRepository = tvTimeExportRepository,
                        apiKeyRepository = apiKeyRepository,
                        uiSettingsRepository = uiSettingsRepository,
                        applicationContext = applicationContext,
                    ) as T
                }
            }
        }
    }
}

private data class LibraryEpisodeData(
    val watched: List<WatchedEpisodeEntity>,
    val cachedEpisodes: List<CachedEpisodeEntity>,
    val completeEpisodeMetadataShowIds: Set<Int>,
)

private val MediaType.label: String
    get() = when (this) {
        MediaType.Movie -> "movie"
        MediaType.Tv -> "series"
    }

private val STALE_WATCHING_MILLIS = TimeUnit.DAYS.toMillis(30)
