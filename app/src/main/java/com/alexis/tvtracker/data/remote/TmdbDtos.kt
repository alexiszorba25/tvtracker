package com.alexis.tvtracker.data.remote

import com.squareup.moshi.Json

data class TmdbSearchResponse(
    val results: List<TmdbSearchResult> = emptyList(),
)

data class TmdbSearchResult(
    val id: Int,
    @Json(name = "media_type") val mediaType: String?,
    val title: String?,
    val name: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
)

data class TmdbFindResponse(
    @Json(name = "tv_results") val tvResults: List<TmdbSearchResult> = emptyList(),
)

data class TmdbTvDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "last_air_date") val lastAirDate: String?,
    val status: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val seasons: List<TmdbSeasonSummary> = emptyList(),
    val credits: TmdbCredits?,
)

data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val credits: TmdbCredits?,
)

data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList(),
)

data class TmdbCastMember(
    val name: String,
    val order: Int?,
)

data class TmdbSeasonSummary(
    val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String,
    @Json(name = "episode_count") val episodeCount: Int,
)

data class TmdbSeasonDetails(
    val id: Int,
    val name: String,
    @Json(name = "season_number") val seasonNumber: Int,
    val episodes: List<TmdbEpisode> = emptyList(),
)

data class TmdbEpisode(
    val id: Int,
    val name: String,
    val overview: String?,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "air_date") val airDate: String?,
    @Json(name = "still_path") val stillPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
)
