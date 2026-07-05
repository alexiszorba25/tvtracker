package com.alexis.tvtracker.data

import com.alexis.tvtracker.model.MediaType
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TvTimeExportRepository(
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
) {
    suspend fun exportZip(): ByteArray {
        val library = libraryRepository.getLibrary()
        val watchedEpisodes = episodeRepository.getAllWatchedEpisodes()
        val shows = library.filter { it.mediaType == MediaType.Tv }
        val watchedByShow = watchedEpisodes.groupBy { it.showId }

        val userShowCsv = buildString {
            appendLine("user_id,tv_show_id,tmdb_id,is_followed,is_favorited,nb_episodes_seen,tv_show_name")
            shows.forEach { show ->
                appendCsvRow(
                    "0",
                    show.tmdbId.toString(),
                    show.tmdbId.toString(),
                    "1",
                    "0",
                    watchedByShow[show.tmdbId].orEmpty().size.toString(),
                    show.title,
                )
            }
        }

        val seenEpisodeCsv = buildString {
            appendLine("created_at,updated_at,tv_show_name,episode_season_number,episode_number,user_id,episode_id,source")
            shows.forEach { show ->
                watchedByShow[show.tmdbId].orEmpty()
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                    .forEach { episode ->
                        val watchedAt = episode.watchedAtMillis.toTvTimeDate()
                        appendCsvRow(
                            watchedAt,
                            watchedAt,
                            show.title,
                            episode.seasonNumber.toString(),
                            episode.episodeNumber.toString(),
                            "0",
                            "${show.tmdbId}-${episode.seasonNumber}-${episode.episodeNumber}",
                            "tv_tracker",
                        )
                    }
            }
        }

        val latestEpisodeCsv = buildString {
            appendLine("tv_show_id,tv_show_name,created_at,updated_at")
            shows.forEach { show ->
                val latestWatchedAt = watchedByShow[show.tmdbId]
                    .orEmpty()
                    .maxOfOrNull { it.watchedAtMillis }
                if (latestWatchedAt != null) {
                    appendCsvRow(
                        show.tmdbId.toString(),
                        show.title,
                        latestWatchedAt.toTvTimeDate(),
                        latestWatchedAt.toTvTimeDate(),
                    )
                }
            }
        }

        val trackingRecordsCsv = buildString {
            appendLine("s_id,season_number,episode_number,created_at,updated_at")
            shows.forEach { show ->
                watchedByShow[show.tmdbId].orEmpty()
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                    .forEach { episode ->
                        val watchedAt = episode.watchedAtMillis.toTvTimeDate()
                        appendCsvRow(
                            show.tmdbId.toString(),
                            episode.seasonNumber.toString(),
                            episode.episodeNumber.toString(),
                            watchedAt,
                            watchedAt,
                        )
                    }
            }
        }

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("user_tv_show_data.csv"))
                zip.write(userShowCsv.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("seen_episode_source.csv"))
                zip.write(seenEpisodeCsv.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("show_seen_episode_latest.csv"))
                zip.write(latestEpisodeCsv.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("tracking-prod-records-v2.csv"))
                zip.write(trackingRecordsCsv.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("tracking-prod-records.csv"))
                zip.write(trackingRecordsCsv.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }
}

private val TV_TIME_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun Long.toTvTimeDate(): String {
    val millis = takeIf { it > 0 } ?: System.currentTimeMillis()
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
        .format(TV_TIME_DATE_FORMATTER)
}

private fun StringBuilder.appendCsvRow(vararg values: String) {
    appendLine(values.joinToString(",") { it.csvEscape() })
}

private fun String.csvEscape(): String {
    val needsQuotes = any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    val escaped = replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
