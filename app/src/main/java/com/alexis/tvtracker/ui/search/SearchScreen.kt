package com.alexis.tvtracker.ui.search

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexis.tvtracker.model.SearchItem
import com.alexis.tvtracker.ui.common.AppHeader
import com.alexis.tvtracker.ui.common.ChromeVisibility
import com.alexis.tvtracker.ui.common.MetadataBlock
import com.alexis.tvtracker.ui.common.MediaPoster
import com.alexis.tvtracker.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, chromeVisible: Boolean) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.addedMessage) {
        val message = state.addedMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearAddedMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChromeVisibility(visible = chromeVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppHeader(title = "TV Tracker", modifier = Modifier.fillMaxWidth())

                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("Search movies or series") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshingSuggestions,
                onRefresh = viewModel::refreshSuggestions,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.missingApiKey -> InfoPanel("Add your TMDb API key from the Library menu to enable search.")
                    state.loading -> CenteredProgress()
                    state.error != null -> InfoPanel(state.error)
                    state.query.isBlank() -> SuggestionsList(
                        loading = state.loadingSuggestions,
                        suggestions = state.suggestions,
                        upcoming = state.upcoming,
                        selectedSection = state.selectedSection,
                        onSectionChange = viewModel::setSection,
                        addedIds = state.addedIds,
                        onAdd = viewModel::addToLibrary,
                    )
                    state.results.isEmpty() -> InfoPanel("No results found.")
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.results, key = { it.key }) { item ->
                            SearchResultRow(
                                item = item,
                                added = item.key in state.addedIds,
                                onAdd = { viewModel.addToLibrary(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsList(
    loading: Boolean,
    suggestions: List<SearchItem>,
    upcoming: List<SearchItem>,
    selectedSection: SearchSection,
    onSectionChange: (SearchSection) -> Unit,
    addedIds: Set<String>,
    onAdd: (SearchItem) -> Unit,
) {
    val items = when (selectedSection) {
        SearchSection.Popular -> suggestions
        SearchSection.ComingSoon -> upcoming
    }

    when {
        loading -> CenteredProgress()
        suggestions.isEmpty() && upcoming.isEmpty() -> InfoPanel("Suggestions will appear here.")
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedSection == SearchSection.Popular,
                    onClick = { onSectionChange(SearchSection.Popular) },
                    label = { Text("Popular") },
                )
                FilterChip(
                    selected = selectedSection == SearchSection.ComingSoon,
                    onClick = { onSectionChange(SearchSection.ComingSoon) },
                    label = { Text("Coming soon") },
                )
            }
            if (items.isEmpty()) {
                InfoPanel("Nothing to show here yet.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items, key = { "${selectedSection.name}-${it.key}" }) { item ->
                        SearchResultRow(
                            item = item,
                            added = item.key in addedIds,
                            onAdd = { onAdd(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InfoPanel(text: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text.orEmpty(),
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(item: SearchItem, added: Boolean, onAdd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MediaPoster(
                posterPath = item.posterPath,
                title = item.title,
                modifier = Modifier.size(width = 60.dp, height = 90.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(item.mediaType.label()) })
                    item.releaseDate?.takeIf { it.isNotBlank() }?.let {
                        AssistChip(onClick = {}, label = { Text(it.take(4)) })
                    }
                }
                MetadataBlock(voteAverage = item.voteAverage, cast = item.cast)
                Text(
                    text = item.overview.ifBlank { "No overview available." },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onAdd, enabled = !added) {
                    Text(if (added) "Added" else "Add")
                }
            }
        }
    }
}
