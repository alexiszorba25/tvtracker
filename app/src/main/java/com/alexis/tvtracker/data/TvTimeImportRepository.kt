package com.alexis.tvtracker.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.data.local.WatchedEpisodeEntity
import com.alexis.tvtracker.metadata.enqueueEpisodeMetadataWork
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class TvTimeImportFile(
    val name: String,
    val content: String,
)

data class TvTimeImportResult(
    val importedShows: Int,
    val importedEpisodes: Int,
    val skippedShows: Int,
)

data class TvTimeImportProgress(
    val processedShows: Int,
    val totalShows: Int,
    val currentShow: String,
)

class TvTimeImportRepository(
    private val context: Context,
    private val tmdbRepository: TmdbRepository,
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
) {
    fun savePendingImportFiles(files: List<TvTimeImportFile>): Array<String> {
        val directory = pendingImportDirectory()
        directory.mkdirs()
        directory.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }

        return files.map { file ->
            val safeName = file.name.toSafeImportFileName()
            File(directory, safeName).writeText(file.content)
            safeName
        }.toTypedArray()
            .also { fileNames ->
                pendingImportManifestFile().writeText(fileNames.joinToString("\n"))
            }
    }

    fun readPendingImportFiles(fileNames: Array<String>): List<TvTimeImportFile> {
        val directory = pendingImportDirectory()
        return fileNames.map { fileName ->
            val safeName = fileName.toSafeImportFileName()
            val file = File(directory, safeName)
            TvTimeImportFile(name = safeName, content = file.readText())
        }
    }

    fun clearPendingImportFiles(fileNames: Array<String>) {
        val directory = pendingImportDirectory()
        fileNames.forEach { fileName ->
            File(directory, fileName.toSafeImportFileName()).delete()
        }
        pendingImportManifestFile().delete()
    }

    fun pendingImportFileNames(): Array<String> {
        val manifest = pendingImportManifestFile()
        val manifestNames = if (manifest.exists()) {
            manifest.readLines()
                .map { it.trim().toSafeImportFileName() }
                .filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val directoryNames = pendingImportDirectory()
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name != PENDING_IMPORT_MANIFEST_FILE }
            .map { it.name.toSafeImportFileName() }

        return (manifestNames + directoryNames)
            .distinct()
            .filter { File(pendingImportDirectory(), it).exists() }
            .toTypedArray()
    }

    suspend fun import(
        files: List<TvTimeImportFile>,
        onProgress: suspend (TvTimeImportProgress) -> Unit = {},
    ): TvTimeImportResult {
        val userShowFile = files.firstOrNull { it.name == "user_tv_show_data.csv" }
            ?: error("Select user_tv_show_data.csv")
        val watchedFile = files.firstOrNull { it.name == "seen_episode_source.csv" }
            ?: error("Select seen_episode_source.csv")
        val latestFile = files.firstOrNull { it.name == "show_seen_episode_latest.csv" }
        val trackingV2File = files.firstOrNull { it.name == "tracking-prod-records-v2.csv" }

        val showsByName = parseCsv(userShowFile.content)
            .mapNotNull { row ->
                val name = row["tv_show_name"]?.trim().orEmpty()
                val tvdbId = row["tv_show_id"]?.toIntOrNull()
                val tmdbId = row["tmdb_id"]?.toIntOrNull()
                val seenCount = row["nb_episodes_seen"]?.toIntOrNull() ?: 0
                if (name.isBlank() || tvdbId == null) null else name to TvTimeShow(
                    name = name,
                    tvdbId = tvdbId,
                    tmdbId = tmdbId,
                    seenCount = seenCount,
                )
            }
            .toMap()
        val showsById = showsByName.values.associateBy { it.tvdbId }

        val watchedByShow = parseCsv(watchedFile.content)
            .mapNotNull { row ->
                val showName = row["tv_show_name"]?.trim().orEmpty()
                val season = row["episode_season_number"]?.toIntOrNull()
                val episode = row["episode_number"]?.toIntOrNull()
                val watchedAtMillis = row["updated_at"].toWatchedAtMillis()
                    ?: row["created_at"].toWatchedAtMillis()
                    ?: 0L
                if (showName.isBlank() || season == null || episode == null) {
                    null
                } else {
                    showName to TvTimeWatchedEpisode(
                        seasonNumber = season,
                        episodeNumber = episode,
                        watchedAtMillis = watchedAtMillis,
                    )
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, episodes) -> episodes.mergedByEpisode() }
        val latestWatchedAtByShowId = latestFile
            ?.content
            ?.let(::parseCsv)
            .orEmpty()
            .mapNotNull { row ->
                val showId = row["tv_show_id"]?.toIntOrNull()
                val watchedAtMillis = row["updated_at"].toWatchedAtMillis()
                    ?: row["created_at"].toWatchedAtMillis()
                if (showId == null || watchedAtMillis == null) null else showId to watchedAtMillis
            }
            .toMap()
        val trackingEpisodesByShowId = trackingV2File
            ?.content
            ?.let(::parseCsv)
            .orEmpty()
            .mapNotNull { row ->
                val showId = row["s_id"]?.toIntOrNull()
                val season = row["season_number"]?.toIntOrNull()
                val episode = row["episode_number"]?.toIntOrNull()
                val watchedAtMillis = row["created_at"].toWatchedAtMillis()
                    ?: row["updated_at"].toWatchedAtMillis()
                    ?: 0L
                if (showId == null || season == null || episode == null) {
                    null
                } else {
                    showId to TvTimeWatchedEpisode(
                        seasonNumber = season,
                        episodeNumber = episode,
                        watchedAtMillis = watchedAtMillis,
                    )
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, episodes) -> episodes.mergedByEpisode() }
        val trackingOnlyShowNames = trackingEpisodesByShowId.keys.mapNotNull { showsById[it]?.name }

        var importedShows = 0
        var importedEpisodes = 0
        var skippedShows = 0

        val showNamesToImport = (showsByName.values.filter { it.seenCount > 0 }.map { it.name } +
            watchedByShow.keys +
            trackingOnlyShowNames).distinct()

        showNamesToImport.forEachIndexed { index, showName ->
            onProgress(
                TvTimeImportProgress(
                    processedShows = index + 1,
                    totalShows = showNamesToImport.size,
                    currentShow = showName,
                ),
            )
            val tvTimeShow = showsByName[showName]
            val explicitWatchedEpisodes = (
                watchedByShow[showName].orEmpty() +
                    trackingEpisodesByShowId[tvTimeShow?.tvdbId].orEmpty()
                ).mergedByEpisode()
            val show = if (tvTimeShow != null) {
                tvTimeShow.tmdbId
                    ?.let { tmdbRepository.tvByTmdbId(it, enrich = false) }
                    ?: tmdbRepository.findTvByTvdbId(tvTimeShow.tvdbId, showName, enrich = false)
            } else {
                tmdbRepository.search(showName).firstOrNull { it.mediaType == MediaType.Tv }
            }

            if (show == null) {
                skippedShows += 1
                return@forEachIndexed
            }

            libraryRepository.add(show)
            val countBasedEpisodes = if (
                trackingV2File == null &&
                tvTimeShow != null &&
                explicitWatchedEpisodes.size < tvTimeShow.seenCount
            ) {
                val inferredWatchedAt = latestWatchedAtByShowId[tvTimeShow.tvdbId]
                    ?: explicitWatchedEpisodes.maxOfOrNull { it.watchedAtMillis }
                    ?: 0L
                episodeKeysUpToCount(show.id, tvTimeShow.seenCount)
                    .map { (season, episode) ->
                        TvTimeWatchedEpisode(
                            seasonNumber = season,
                            episodeNumber = episode,
                            watchedAtMillis = inferredWatchedAt,
                        )
                    }
            } else {
                emptyList()
            }
            val episodesToMark = (explicitWatchedEpisodes + countBasedEpisodes)
                .mergedByEpisode()

            episodeRepository.markEpisodes(
                showId = show.id,
                episodes = episodesToMark.map { episode ->
                    WatchedEpisodeEntity(
                        showId = show.id,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        watchedAtMillis = episode.watchedAtMillis,
                    )
                },
            )
            importedEpisodes += episodesToMark.size
            importedShows += 1
        }

        enqueueEpisodeMetadataWork(context, ExistingWorkPolicy.REPLACE)
        return TvTimeImportResult(
            importedShows = importedShows,
            importedEpisodes = importedEpisodes,
            skippedShows = skippedShows,
        )
    }

    private suspend fun episodeKeysUpToCount(showId: Int, count: Int): List<Pair<Int, Int>> {
        return runCatching {
            val details = tmdbRepository.getTvDetails(showId)
            details.seasons
                .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                .sortedBy { it.seasonNumber }
                .flatMap { season ->
                    tmdbRepository.getSeason(showId, season.seasonNumber).episodes
                        .sortedBy { it.episodeNumber }
                        .map { episode -> season.seasonNumber to episode.episodeNumber }
                }
                .take(count)
        }.getOrDefault(emptyList())
    }

    private fun pendingImportDirectory(): File {
        return File(context.filesDir, PENDING_IMPORT_DIRECTORY)
    }

    private fun pendingImportManifestFile(): File {
        val directory = pendingImportDirectory()
        directory.mkdirs()
        return File(directory, PENDING_IMPORT_MANIFEST_FILE)
    }
}

private const val PENDING_IMPORT_DIRECTORY = "tvtime-import"
private const val PENDING_IMPORT_MANIFEST_FILE = "pending-files.txt"

private fun String.toSafeImportFileName(): String {
    return substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private data class TvTimeShow(
    val name: String,
    val tvdbId: Int,
    val tmdbId: Int?,
    val seenCount: Int,
)

private data class TvTimeWatchedEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAtMillis: Long,
)

private fun List<TvTimeWatchedEpisode>.mergedByEpisode(): List<TvTimeWatchedEpisode> {
    return groupBy { it.seasonNumber to it.episodeNumber }
        .map { (_, episodes) -> episodes.maxBy { it.watchedAtMillis } }
        .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
}

private fun String?.toWatchedAtMillis(): Long? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching {
        LocalDateTime.parse(value, TV_TIME_DATE_FORMATTER)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()
}

private val TV_TIME_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun parseCsv(content: String): List<Map<String, String>> {
    val rows = content.lineSequence()
        .filter { it.isNotBlank() }
        .map(::parseCsvLine)
        .toList()
    if (rows.isEmpty()) return emptyList()

    val headers = rows.first()
    return rows.drop(1).map { values ->
        headers.mapIndexed { index, header ->
            header to values.getOrElse(index) { "" }
        }.toMap()
    }
}

private fun parseCsvLine(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                current.append('"')
                index += 1
            }
            char == '"' -> inQuotes = !inQuotes
            char == ',' && !inQuotes -> {
                values += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index += 1
    }
    values += current.toString()
    return values
}
