package com.alexis.tvtracker.data

import com.alexis.tvtracker.model.MediaType
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
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
            appendLine("user_id,tv_show_id,is_followed,is_favorited,nb_episodes_seen,tv_show_name")
            shows.forEach { show ->
                appendCsvRow(
                    "0",
                    show.tmdbId.toString(),
                    "1",
                    "0",
                    watchedByShow[show.tmdbId].orEmpty().size.toString(),
                    show.title,
                )
            }
        }

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val seenEpisodeCsv = buildString {
            appendLine("created_at,updated_at,tv_show_name,episode_season_number,episode_number,user_id,episode_id,source")
            shows.forEach { show ->
                watchedByShow[show.tmdbId].orEmpty()
                    .sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                    .forEach { episode ->
                        appendCsvRow(
                            now,
                            now,
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

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("user_tv_show_data.csv"))
                zip.write(userShowCsv.toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("seen_episode_source.csv"))
                zip.write(seenEpisodeCsv.toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }
}

private fun StringBuilder.appendCsvRow(vararg values: String) {
    appendLine(values.joinToString(",") { it.csvEscape() })
}

private fun String.csvEscape(): String {
    val needsQuotes = any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    val escaped = replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
