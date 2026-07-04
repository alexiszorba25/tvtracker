package com.alexis.tvtracker.data

import com.alexis.tvtracker.data.local.LibraryDao
import com.alexis.tvtracker.data.local.LibraryItemEntity
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.model.SearchItem
import kotlinx.coroutines.flow.Flow

class LibraryRepository(private val dao: LibraryDao) {
    val library: Flow<List<LibraryItemEntity>> = dao.observeLibrary()

    suspend fun getLibrary(): List<LibraryItemEntity> {
        return dao.getLibrary()
    }

    suspend fun add(item: SearchItem) {
        dao.upsert(
            LibraryItemEntity(
                tmdbId = item.id,
                mediaType = item.mediaType,
                title = item.title,
                overview = item.overview,
                posterPath = item.posterPath,
                releaseDate = item.releaseDate,
                voteAverage = item.voteAverage,
                cast = item.cast,
                watched = false,
            ),
        )
    }

    suspend fun setWatched(id: Int, mediaType: MediaType, watched: Boolean) {
        dao.setWatched(id, mediaType, watched)
    }

    suspend fun updateMetadata(id: Int, mediaType: MediaType, voteAverage: Double?, cast: String?) {
        dao.updateMetadata(id, mediaType, voteAverage, cast)
    }

    suspend fun remove(id: Int, mediaType: MediaType) {
        dao.delete(id, mediaType)
    }
}
