package com.alexis.tvtracker.data.local

import androidx.room.Entity
import com.alexis.tvtracker.model.MediaType

@Entity(
    tableName = "library_items",
    primaryKeys = ["tmdbId", "mediaType"],
)
data class LibraryItemEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val releaseDate: String?,
    val voteAverage: Double?,
    val cast: String?,
    val watched: Boolean,
)
