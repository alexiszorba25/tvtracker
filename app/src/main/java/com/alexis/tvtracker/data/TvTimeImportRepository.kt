package com.alexis.tvtracker.data

import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.model.SearchItem

data class TvTimeImportFile(
    val name: String,
    val content: String,
)

data class TvTimeImportResult(
    val importedShows: Int,
    val importedEpisodes: Int,
    val skippedShows: Int,
)

class TvTimeImportRepository(
    private val tmdbRepository: TmdbRepository,
    private val libraryRepository: LibraryRepository,
    private val episodeRepository: EpisodeRepository,
) {
    suspend fun import(files: List<TvTimeImportFile>): TvTimeImportResult {
        val userShowFile = files.firstOrNull { it.name == "user_tv_show_data.csv" }
            ?: error("Select user_tv_show_data.csv")
        val watchedFile = files.firstOrNull { it.name == "seen_episode_source.csv" }
            ?: error("Select seen_episode_source.csv")

        val showsByName = parseCsv(userShowFile.content)
            .mapNotNull { row ->
                val name = row["tv_show_name"]?.trim().orEmpty()
                val tvdbId = row["tv_show_id"]?.toIntOrNull()
                val seenCount = row["nb_episodes_seen"]?.toIntOrNull() ?: 0
                if (name.isBlank() || tvdbId == null) null else name to TvTimeShow(
                    name = name,
                    tvdbId = tvdbId,
                    seenCount = seenCount,
                )
            }
            .toMap()

        val watchedByShow = parseCsv(watchedFile.content)
            .mapNotNull { row ->
                val showName = row["tv_show_name"]?.trim().orEmpty()
                val season = row["episode_season_number"]?.toIntOrNull()
                val episode = row["episode_number"]?.toIntOrNull()
                if (showName.isBlank() || season == null || episode == null) {
                    null
                } else {
                    showName to (season to episode)
                }
            }
            .groupBy({ it.first }, { it.second })

        var importedShows = 0
        var importedEpisodes = 0
        var skippedShows = 0

        val showNamesToImport = (showsByName.values.filter { it.seenCount > 0 }.map { it.name } +
            watchedByShow.keys).distinct()

        showNamesToImport.forEach { showName ->
            val tvTimeShow = showsByName[showName]
            val explicitWatchedEpisodes = watchedByShow[showName].orEmpty().distinct()
            val show = if (tvTimeShow != null) {
                tmdbRepository.findTvByTvdbId(tvTimeShow.tvdbId)
            } else {
                tmdbRepository.search(showName).firstOrNull { it.mediaType == MediaType.Tv }
            }

            if (show == null) {
                skippedShows += 1
                return@forEach
            }

            libraryRepository.add(show)
            val allEpisodes = cacheShowEpisodes(show)
            val countBasedEpisodes = if (tvTimeShow != null && explicitWatchedEpisodes.size < tvTimeShow.seenCount) {
                allEpisodes.take(tvTimeShow.seenCount)
            } else {
                emptyList()
            }
            val episodesToMark = (explicitWatchedEpisodes + countBasedEpisodes).distinct()

            episodesToMark.forEach { (season, episode) ->
                episodeRepository.markEpisode(
                    showId = show.id,
                    seasonNumber = season,
                    episodeNumber = episode,
                    watched = true,
                )
                importedEpisodes += 1
            }
            if (allEpisodes.isNotEmpty() && allEpisodes.all { it in episodesToMark }) {
                libraryRepository.setWatched(show.id, MediaType.Tv, true)
            }
            importedShows += 1
        }

        return TvTimeImportResult(
            importedShows = importedShows,
            importedEpisodes = importedEpisodes,
            skippedShows = skippedShows,
        )
    }

    private suspend fun cacheShowEpisodes(show: SearchItem): List<Pair<Int, Int>> {
        return runCatching {
            val details = tmdbRepository.getTvDetails(show.id)
            details.seasons
                .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                .sortedBy { it.seasonNumber }
                .flatMap { season ->
                    val episodes = tmdbRepository.getSeason(show.id, season.seasonNumber).episodes
                    episodeRepository.cacheEpisodes(show.id, season.seasonNumber, episodes)
                    episodes
                        .sortedBy { it.episodeNumber }
                        .map { episode -> season.seasonNumber to episode.episodeNumber }
                }
        }.getOrDefault(emptyList())
    }
}

private data class TvTimeShow(
    val name: String,
    val tvdbId: Int,
    val seenCount: Int,
)

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
