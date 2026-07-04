package com.alexis.tvtracker.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.tvtracker.data.EpisodeRepository
import com.alexis.tvtracker.data.ApiKeyRepository
import com.alexis.tvtracker.data.LibraryRepository
import com.alexis.tvtracker.data.NextEpisode
import com.alexis.tvtracker.data.TmdbRepository
import com.alexis.tvtracker.data.ThemeMode
import com.alexis.tvtracker.data.TvTimeExportRepository
import com.alexis.tvtracker.data.TvTimeImportFile
import com.alexis.tvtracker.data.TvTimeImportRepository
import com.alexis.tvtracker.data.UiSettingsRepository
import com.alexis.tvtracker.data.local.CachedEpisodeEntity
import com.alexis.tvtracker.data.local.LibraryItemEntity
import com.alexis.tvtracker.data.local.WatchedEpisodeEntity
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.util.hasAired
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTypeFilter {
    All,
    Series,
    Movies,
}

data class LibraryUiState(
    val items: List<LibraryItemEntity> = emptyList(),
    val query: String = "",
    val hideWatched: Boolean = true,
    val typeFilter: LibraryTypeFilter = LibraryTypeFilter.All,
    val nextEpisodes: Map<Int, NextEpisode> = emptyMap(),
    val importing: Boolean = false,
    val exporting: Boolean = false,
    val pendingExportZip: ByteArray? = null,
    val importMessage: String? = null,
    val tmdbApiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
) {
    val filteredItems: List<LibraryItemEntity>
        get() = items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.overview.contains(query, ignoreCase = true) ||
                item.releaseDate.orEmpty().contains(query, ignoreCase = true) ||
                item.mediaType.label.contains(query, ignoreCase = true)
            val matchesWatched = !hideWatched || !item.watched
            val matchesType = when (typeFilter) {
                LibraryTypeFilter.All -> true
                LibraryTypeFilter.Series -> item.mediaType == MediaType.Tv
                LibraryTypeFilter.Movies -> item.mediaType == MediaType.Movie
            }
            matchesQuery && matchesWatched && matchesType
        }
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
    private val tmdbRepository: TmdbRepository,
    private val tvTimeImportRepository: TvTimeImportRepository,
    private val tvTimeExportRepository: TvTimeExportRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val uiSettingsRepository: UiSettingsRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(LibraryUiState())
    private var metadataJob: Job? = null
    private var metadataRefreshSignature: String? = null
    private var libraryMetadataJob: Job? = null
    private var libraryMetadataSignature: String? = null
    private val settings = combine(
        apiKeyRepository.apiKey,
        uiSettingsRepository.themeMode,
    ) { apiKey, themeMode ->
        apiKey to themeMode
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.library,
        filters,
        episodeRepository.observeAllWatchedEpisodes(),
        episodeRepository.observeCachedEpisodes(),
        settings,
    ) { library, filterState, watched, cachedEpisodes, settings ->
        refreshMissingEpisodeMetadata(library, cachedEpisodes)
        refreshMissingLibraryMetadata(library)
        filterState.copy(
            items = library,
            nextEpisodes = calculateNextEpisodes(library, watched, cachedEpisodes),
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

    fun setHideWatched(hideWatched: Boolean) {
        filters.update { it.copy(hideWatched = hideWatched) }
    }

    fun setTypeFilter(typeFilter: LibraryTypeFilter) {
        filters.update { it.copy(typeFilter = typeFilter) }
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
        viewModelScope.launch {
            filters.update { it.copy(importing = true, importMessage = null) }
            runCatching { tvTimeImportRepository.import(files) }
                .onSuccess { result ->
                    filters.update {
                        it.copy(
                            importing = false,
                            importMessage = "Imported ${result.importedEpisodes} episodes from ${result.importedShows} shows. Skipped ${result.skippedShows}.",
                        )
                    }
                }
                .onFailure { throwable ->
                    filters.update {
                        it.copy(
                            importing = false,
                            importMessage = throwable.message ?: "TV Time import failed",
                        )
                    }
                }
        }
    }

    fun clearImportMessage() {
        filters.update { it.copy(importMessage = null) }
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
        val series = items.filter { it.mediaType == MediaType.Tv && !it.watched }
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

    private fun refreshMissingEpisodeMetadata(
        items: List<LibraryItemEntity>,
        cachedEpisodes: List<CachedEpisodeEntity>,
    ) {
        val series = items.filter { it.mediaType == MediaType.Tv && !it.watched }
        val cachedShowIds = cachedEpisodes.map { it.showId }.toSet()
        val missingSeries = series.filter { it.tmdbId !in cachedShowIds }
        val signature = missingSeries.joinToString { it.tmdbId.toString() }
        if (signature == metadataRefreshSignature) return
        metadataRefreshSignature = signature
        if (missingSeries.isEmpty()) return

        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            missingSeries.forEach { item ->
                runCatching {
                    val details = tmdbRepository.getTvDetails(item.tmdbId)
                    details.seasons
                        .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                        .sortedBy { it.seasonNumber }
                        .forEach { season ->
                            val episodes = tmdbRepository.getSeason(item.tmdbId, season.seasonNumber).episodes
                            episodeRepository.cacheEpisodes(item.tmdbId, season.seasonNumber, episodes)
                        }
                }
            }
        }
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
                    ) as T
                }
            }
        }
    }
}

private val MediaType.label: String
    get() = when (this) {
        MediaType.Movie -> "movie"
        MediaType.Tv -> "series"
    }
