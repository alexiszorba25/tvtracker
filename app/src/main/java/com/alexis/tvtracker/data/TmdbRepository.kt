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

    suspend fun findTvByTvdbId(
        tvdbId: Int,
        expectedTitle: String? = null,
        enrich: Boolean = true,
    ): SearchItem? {
        val externalMatch = api.findByExternalId(tvdbId, "tvdb_id")
            .tvResults
            .toSearchItems(defaultMediaType = MediaType.Tv)
            .firstOrNull()
            ?.let { item -> if (enrich) item.withDetails() else item }
        if (expectedTitle.isNullOrBlank() || externalMatch?.title.isLikelySameTitle(expectedTitle)) {
            return externalMatch
        }
        return search(expectedTitle)
            .firstOrNull { it.mediaType == MediaType.Tv && it.title.isLikelySameTitle(expectedTitle) }
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

    suspend fun tvByTmdbId(id: Int, enrich: Boolean = true): SearchItem? {
        return runCatching {
            val details = api.getTvDetails(id)
            SearchItem(
                id = details.id,
                mediaType = MediaType.Tv,
                title = details.name,
                overview = details.overview.orEmpty(),
                posterPath = details.posterPath,
                releaseDate = details.firstAirDate,
                voteAverage = details.voteAverage,
                cast = if (enrich) details.credits?.cast?.mainCast() else null,
            )
        }.getOrNull()
    }

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

private fun String?.isLikelySameTitle(other: String): Boolean {
    val left = this.normalizedTitle()
    val right = other.normalizedTitle()
    if (left.isBlank() || right.isBlank()) return false
    if (left == right) return true
    if ((left.length >= 5 && right.contains(left)) || (right.length >= 5 && left.contains(right))) {
        return true
    }
    val leftTokens = left.split(" ").filter { it.isNotBlank() }.toSet()
    val rightTokens = right.split(" ").filter { it.isNotBlank() }.toSet()
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false
    val overlap = leftTokens.intersect(rightTokens).size.toDouble()
    val total = leftTokens.union(rightTokens).size.toDouble()
    return overlap / total >= 0.75
}

private fun String?.normalizedTitle(): String {
    return orEmpty()
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
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
