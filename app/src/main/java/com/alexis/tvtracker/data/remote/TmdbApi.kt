package com.alexis.tvtracker.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponse

    @GET("trending/tv/week")
    suspend fun trendingTv(): TmdbSearchResponse

    @GET("movie/popular")
    suspend fun popularMovies(): TmdbSearchResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("first_air_date.gte") firstAirDateGte: String,
        @Query("first_air_date.lte") firstAirDateLte: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_null_first_air_dates") includeNullFirstAirDates: Boolean = false,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbSearchResponse

    @GET("movie/upcoming")
    suspend fun upcomingMovies(): TmdbSearchResponse

    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: Int,
        @Query("external_source") externalSource: String,
    ): TmdbFindResponse

    @GET("tv/{series_id}")
    suspend fun getTvDetails(
        @Path("series_id") seriesId: Int,
        @Query("append_to_response") appendToResponse: String = "credits",
    ): TmdbTvDetails

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "credits",
    ): TmdbMovieDetails

    @GET("tv/{series_id}/season/{season_number}")
    suspend fun getSeason(
        @Path("series_id") seriesId: Int,
        @Path("season_number") seasonNumber: Int,
    ): TmdbSeasonDetails
}
