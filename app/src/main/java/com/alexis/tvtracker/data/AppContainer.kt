package com.alexis.tvtracker.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexis.tvtracker.data.local.TvTrackerDatabase
import com.alexis.tvtracker.data.remote.TmdbApi
import com.alexis.tvtracker.data.remote.TmdbAuthInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val apiKeyRepository = ApiKeyRepository(context.applicationContext)
    val uiSettingsRepository = UiSettingsRepository(context.applicationContext)

    private val database = getDatabase(context.applicationContext)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(TmdbAuthInterceptor { apiKeyRepository.currentApiKey() })
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    private val tmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TmdbApi::class.java)

    val libraryRepository = LibraryRepository(database.libraryDao())
    val episodeRepository = EpisodeRepository(database.episodeDao())
    val tmdbRepository = TmdbRepository(tmdbApi, apiKeyRepository)
    val tvTimeImportRepository = TvTimeImportRepository(
        context = context.applicationContext,
        tmdbRepository = tmdbRepository,
        libraryRepository = libraryRepository,
        episodeRepository = episodeRepository,
    )
    val tvTimeExportRepository = TvTimeExportRepository(
        libraryRepository = libraryRepository,
        episodeRepository = episodeRepository,
    )

    private companion object {
        @Volatile
        private var databaseInstance: TvTrackerDatabase? = null

        fun getDatabase(context: Context): TvTrackerDatabase {
            return databaseInstance ?: synchronized(this) {
                databaseInstance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TvTrackerDatabase::class.java,
                    "tv-tracker.db",
                )
                    .enableMultiInstanceInvalidation()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { databaseInstance = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_episodes (
                        showId INTEGER NOT NULL,
                        seasonNumber INTEGER NOT NULL,
                        episodeNumber INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        airDate TEXT,
                        PRIMARY KEY(showId, seasonNumber, episodeNumber)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_items ADD COLUMN voteAverage REAL")
                db.execSQL("ALTER TABLE library_items ADD COLUMN cast TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_episodes ADD COLUMN watchedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS episode_metadata_status (
                        showId INTEGER NOT NULL PRIMARY KEY,
                        complete INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episode_metadata_status ADD COLUMN lastSeasonNumber INTEGER")
                db.execSQL("ALTER TABLE episode_metadata_status ADD COLUMN totalSeasons INTEGER")
                db.execSQL("ALTER TABLE episode_metadata_status ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
