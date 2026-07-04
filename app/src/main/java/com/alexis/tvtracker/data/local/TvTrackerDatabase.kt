package com.alexis.tvtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LibraryItemEntity::class,
        WatchedEpisodeEntity::class,
        CachedEpisodeEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class TvTrackerDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun episodeDao(): EpisodeDao
}
