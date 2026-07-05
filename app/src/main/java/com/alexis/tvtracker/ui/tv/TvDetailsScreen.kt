package com.alexis.tvtracker.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alexis.tvtracker.data.remote.TmdbEpisode
import com.alexis.tvtracker.ui.common.MediaPoster
import com.alexis.tvtracker.ui.common.RatingText
import com.alexis.tvtracker.util.hasAired

@Composable
fun TvDetailsScreen(viewModel: TvDetailsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MediaPoster(
                    posterPath = state.posterPath,
                    title = state.title.ifBlank { "Series" },
                    modifier = Modifier.size(width = 96.dp, height = 144.dp),
                )
                PosterMetadata(voteAverage = state.voteAverage, yearRange = state.yearRange)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.title.ifBlank { "Series" },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (state.overview.isNotBlank()) {
                    Text(
                        text = state.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                state.cast?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        state.error?.let {
            InfoPanel(it)
        }

        if (state.seasons.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.seasons.forEach { season ->
                    FilterChip(
                        selected = state.selectedSeason == season.seasonNumber,
                        onClick = { viewModel.selectSeason(season.seasonNumber) },
                        label = { Text("S${season.seasonNumber}") },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.setSeasonWatched(true) },
                enabled = state.episodes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mark season watched")
            }
            OutlinedButton(
                onClick = { viewModel.setSeasonWatched(false) },
                enabled = state.episodes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear season")
            }
        }

        if (state.loading && state.episodes.isEmpty()) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.episodes, key = { it.id }) { episode ->
                    val key = EpisodeKey(
                        seasonNumber = state.selectedSeason ?: 0,
                        episodeNumber = episode.episodeNumber,
                    )
                    EpisodeRow(
                        episode = episode,
                        watched = key in state.watchedEpisodes,
                        available = hasAired(episode.airDate),
                        onWatchedChange = { watched ->
                            viewModel.setEpisodeWatched(
                                seasonNumber = key.seasonNumber,
                                episodeNumber = key.episodeNumber,
                                watched = watched,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterMetadata(voteAverage: Double?, yearRange: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatingText(voteAverage, style = MaterialTheme.typography.labelSmall)
        yearRange?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: TmdbEpisode,
    watched: Boolean,
    available: Boolean,
    onWatchedChange: (Boolean) -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (watched) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "episode-watched-color",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(112.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EpisodeStill(
                    stillPath = episode.stillPath,
                    title = episode.name,
                    modifier = Modifier.size(width = 112.dp, height = 72.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RatingText(
                        voteAverage = episode.voteAverage,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    episode.airDate?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = if (available) it else "$it - not aired yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "E${episode.episodeNumber} ${episode.name}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode.overview.orEmpty().ifBlank { "No overview available." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = watched,
                enabled = available,
                onCheckedChange = onWatchedChange,
            )
        }
    }
}

@Composable
private fun EpisodeStill(stillPath: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (stillPath != null) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w300$stillPath",
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "E",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
