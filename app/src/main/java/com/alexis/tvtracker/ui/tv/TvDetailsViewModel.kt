package com.alexis.tvtracker.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexis.tvtracker.data.EpisodeRepository
import com.alexis.tvtracker.data.TmdbRepository
import com.alexis.tvtracker.data.mainCast
import com.alexis.tvtracker.data.local.WatchedEpisodeEntity
import com.alexis.tvtracker.data.remote.TmdbEpisode
import com.alexis.tvtracker.data.remote.TmdbSeasonSummary
import com.alexis.tvtracker.util.hasAired
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvDetailsUiState(
    val loading: Boolean = true,
    val title: String = "",
    val overview: String = "",
    val voteAverage: Double? = null,
    val cast: String? = null,
    val seasons: List<TmdbSeasonSummary> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
    val watchedEpisodes: Set<EpisodeKey> = emptySet(),
    val error: String? = null,
)

data class EpisodeKey(
    val seasonNumber: Int,
    val episodeNumber: Int,
)

private data class RemoteTvState(
    val loading: Boolean = true,
    val title: String = "",
    val overview: String = "",
    val voteAverage: Double? = null,
    val cast: String? = null,
    val seasons: List<TmdbSeasonSummary> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<TmdbEpisode> = emptyList(),
    val error: String? = null,
)

class TvDetailsViewModel(
    private val showId: Int,
    private val tmdbRepository: TmdbRepository,
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {
    private val remoteState = MutableStateFlow(RemoteTvState())

    val uiState: StateFlow<TvDetailsUiState> = combine(
        remoteState,
        episodeRepository.observeWatchedEpisodes(showId),
    ) { remote, watched ->
        remote.toUiState(watched)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TvDetailsUiState(),
    )

    init {
        loadDetails()
    }

    fun selectSeason(seasonNumber: Int) {
        viewModelScope.launch {
            remoteState.update { it.copy(selectedSeason = seasonNumber, loading = true, error = null) }
            runCatching { tmdbRepository.getSeason(showId, seasonNumber) }
                .onSuccess { season ->
                    episodeRepository.cacheEpisodes(
                        showId = showId,
                        seasonNumber = season.seasonNumber,
                        episodes = season.episodes,
                    )
                    remoteState.update {
                        it.copy(
                            loading = false,
                            selectedSeason = season.seasonNumber,
                            episodes = season.episodes.sortedBy { episode -> episode.episodeNumber },
                        )
                    }
                }
                .onFailure { throwable ->
                    remoteState.update {
                        it.copy(loading = false, error = throwable.message ?: "Season failed to load")
                    }
                }
        }
    }

    fun setEpisodeWatched(seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        viewModelScope.launch {
            episodeRepository.markEpisode(showId, seasonNumber, episodeNumber, watched)
        }
    }

    fun setSeasonWatched(watched: Boolean) {
        val state = uiState.value
        val seasonNumber = state.selectedSeason ?: return
        viewModelScope.launch {
            episodeRepository.markSeason(
                showId = showId,
                seasonNumber = seasonNumber,
                episodeNumbers = if (watched) {
                    state.episodes.filter { hasAired(it.airDate) }.map { it.episodeNumber }
                } else {
                    state.episodes.map { it.episodeNumber }
                },
                watched = watched,
            )
        }
    }

    private fun loadDetails() {
        viewModelScope.launch {
            remoteState.update { it.copy(loading = true, error = null) }
            runCatching { tmdbRepository.getTvDetails(showId) }
                .onSuccess { details ->
                    val seasons = details.seasons
                        .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                        .sortedBy { it.seasonNumber }
                    val firstSeason = seasons.firstOrNull()?.seasonNumber
                    remoteState.update {
                        it.copy(
                            loading = false,
                            title = details.name,
                            overview = details.overview.orEmpty(),
                            voteAverage = details.voteAverage,
                            cast = details.credits?.cast?.mainCast(),
                            seasons = seasons,
                            selectedSeason = firstSeason,
                        )
                    }
                    if (firstSeason != null) {
                        selectSeason(firstSeason)
                    }
                }
                .onFailure { throwable ->
                    remoteState.update {
                        it.copy(loading = false, error = throwable.message ?: "Series failed to load")
                    }
                }
        }
    }

    companion object {
        fun factory(
            showId: Int,
            tmdbRepository: TmdbRepository,
            episodeRepository: EpisodeRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TvDetailsViewModel(showId, tmdbRepository, episodeRepository) as T
                }
            }
        }
    }
}

private fun RemoteTvState.toUiState(watched: List<WatchedEpisodeEntity>): TvDetailsUiState {
    return TvDetailsUiState(
        loading = loading,
        title = title,
        overview = overview,
        voteAverage = voteAverage,
        cast = cast,
        seasons = seasons,
        selectedSeason = selectedSeason,
        episodes = episodes,
        watchedEpisodes = watched.map { EpisodeKey(it.seasonNumber, it.episodeNumber) }.toSet(),
        error = error,
    )
}
