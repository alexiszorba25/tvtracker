package com.alexis.tvtracker.model

data class SearchItem(
    val id: Int,
    val mediaType: MediaType,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    val cast: String?,
)
