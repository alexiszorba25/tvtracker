package com.alexis.tvtracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alexis.tvtracker.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY title COLLATE NOCASE")
    fun observeLibrary(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items ORDER BY title COLLATE NOCASE")
    suspend fun getLibrary(): List<LibraryItemEntity>

    @Upsert
    suspend fun upsert(item: LibraryItemEntity)

    @Query("UPDATE library_items SET watched = :watched WHERE tmdbId = :id AND mediaType = :mediaType")
    suspend fun setWatched(id: Int, mediaType: MediaType, watched: Boolean)

    @Query("UPDATE library_items SET voteAverage = :voteAverage, cast = :cast WHERE tmdbId = :id AND mediaType = :mediaType")
    suspend fun updateMetadata(id: Int, mediaType: MediaType, voteAverage: Double?, cast: String?)

    @Query("DELETE FROM library_items WHERE tmdbId = :id AND mediaType = :mediaType")
    suspend fun delete(id: Int, mediaType: MediaType)
}
