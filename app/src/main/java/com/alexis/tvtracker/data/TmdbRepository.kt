package com.alexis.tvtracker.data

import com.alexis.tvtracker.data.remote.TmdbApi
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.model.SearchItem
import java.time.LocalDate

class TmdbRepository(
    private val api: TmdbApi,
    private val apiKeyRepository: ApiKeyRepository,
) {
    private var cachedSuggestions: List<SearchItem> = emptyList()
    private var cachedUpcoming: List<SearchItem> = emptyList()

    fun hasApiKey(): Boolean = apiKeyRepository.hasApiKey()

    fun cachedDiscovery(): Pair<List<SearchItem>, List<SearchItem>> {
        return cachedSuggestions to cachedUpcoming
    }

    suspend fun search(query: String): List<SearchItem> {
        if (query.isBlank()) return emptyList()
        return api.searchMulti(query.trim()).results.toSearchItems().withDetails(limit = 8)
    }

    suspend fun suggestions(forceRefresh: Boolean = false): List<SearchItem> {
        if (!forceRefresh && cachedSuggestions.isNotEmpty()) return cachedSuggestions
        val tv = api.trendingTv().results.toSearchItems(defaultMediaType = MediaType.Tv)
        val movies = api.popularMovies().results.toSearchItems(defaultMediaType = MediaType.Movie)
        return (tv.take(5) + movies.take(5))
            .distinctBy { "${it.mediaType}-${it.id}" }
            .withDetails(limit = 10)
            .also { cachedSuggestions = it }
    }

    suspend fun upcoming(forceRefresh: Boolean = false): List<SearchItem> {
        if (!forceRefresh && cachedUpcoming.isNotEmpty()) return cachedUpcoming
        val today = LocalDate.now()
        val tv = api.discoverTv(
            firstAirDateGte = today.toString(),
            firstAirDateLte = today.plusDays(90).toString(),
        ).results.toSearchItems(defaultMediaType = MediaType.Tv)
        val movies = api.upcomingMovies().results.toSearchItems(defaultMediaType = MediaType.Movie)
        return (tv.take(5) + movies.take(5))
            .distinctBy { "${it.mediaType}-${it.id}" }
            .withDetails(limit = 10)
            .also { cachedUpcoming = it }
    }

    suspend fun findTvByTvdbId(tvdbId: Int): SearchItem? {
        return api.findByExternalId(tvdbId, "tvdb_id")
            .tvResults
            .toSearchItems(defaultMediaType = MediaType.Tv)
            .firstOrNull()
            ?.withDetails()
    }

    suspend fun firstUnwatchedEpisode(
        showId: Int,
        watchedEpisodes: Set<Pair<Int, Int>>,
    ): NextEpisode? {
        val details = api.getTvDetails(showId)
        val seasons = details.seasons
            .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .sortedBy { it.seasonNumber }

        for (season in seasons) {
            val episodes = api.getSeason(showId, season.seasonNumber).episodes
                .sortedBy { it.episodeNumber }
            val next = episodes.firstOrNull { episode ->
                season.seasonNumber to episode.episodeNumber !in watchedEpisodes
            }
            if (next != null) {
                return NextEpisode(
                    showId = showId,
                    seasonNumber = season.seasonNumber,
                    episodeNumber = next.episodeNumber,
                    title = next.name,
                    airDate = next.airDate,
                )
            }
        }
        return null
    }

    private fun List<com.alexis.tvtracker.data.remote.TmdbSearchResult>.toSearchItems(
        defaultMediaType: MediaType? = null,
    ): List<SearchItem> {
        return this
            .mapNotNull { result ->
                val mediaType = when (result.mediaType) {
                    "movie" -> MediaType.Movie
                    "tv" -> MediaType.Tv
                    else -> defaultMediaType ?: return@mapNotNull null
                }
                SearchItem(
                    id = result.id,
                    mediaType = mediaType,
                    title = result.title ?: result.name ?: return@mapNotNull null,
                    overview = result.overview.orEmpty(),
                    posterPath = result.posterPath,
                    releaseDate = result.releaseDate ?: result.firstAirDate,
                    voteAverage = result.voteAverage,
                    cast = null,
                )
            }
    }

    suspend fun getTvDetails(id: Int) = api.getTvDetails(id)

    suspend fun getSeason(showId: Int, seasonNumber: Int) = api.getSeason(showId, seasonNumber)

    suspend fun enrich(item: SearchItem): SearchItem = item.withDetails()

    private suspend fun List<SearchItem>.withDetails(limit: Int): List<SearchItem> {
        return mapIndexed { index, item ->
            if (index < limit) item.withDetails() else item
        }
    }

    private suspend fun SearchItem.withDetails(): SearchItem {
        return runCatching {
            when (mediaType) {
                MediaType.Tv -> {
                    val details = api.getTvDetails(id)
                    copy(
                        voteAverage = details.voteAverage ?: voteAverage,
                        cast = details.credits?.cast?.mainCast(),
                    )
                }
                MediaType.Movie -> {
                    val details = api.getMovieDetails(id)
                    copy(
                        voteAverage = details.voteAverage ?: voteAverage,
                        cast = details.credits?.cast?.mainCast(),
                    )
                }
            }
        }.getOrDefault(this)
    }
}

fun List<com.alexis.tvtracker.data.remote.TmdbCastMember>.mainCast(): String? {
    return sortedBy { it.order ?: Int.MAX_VALUE }
        .map { it.name }
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString(", ")
        .ifBlank { null }
}

data class NextEpisode(
    val showId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val airDate: String?,
)
