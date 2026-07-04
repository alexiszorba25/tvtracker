package com.alexis.tvtracker.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexis.tvtracker.data.NextEpisode
import com.alexis.tvtracker.data.ThemeMode
import com.alexis.tvtracker.data.TvTimeImportFile
import com.alexis.tvtracker.data.local.LibraryItemEntity
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.ui.common.AppHeader
import com.alexis.tvtracker.ui.common.ChromeVisibility
import com.alexis.tvtracker.ui.common.MetadataBlock
import com.alexis.tvtracker.ui.common.MediaPoster
import com.alexis.tvtracker.ui.common.RatingText
import com.alexis.tvtracker.ui.common.MoreVertButton
import com.alexis.tvtracker.ui.label
import com.alexis.tvtracker.util.isReleased

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, chromeVisible: Boolean, onOpenTv: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val library = state.filteredItems
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingZip by remember { mutableStateOf<ByteArray?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val files = uris.mapNotNull { uri -> context.readImportFile(uri) }
        if (files.isNotEmpty()) {
            viewModel.importTvTime(files)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val zip = pendingZip
        if (uri != null && zip != null && context.writeExportFile(uri, zip)) {
            viewModel.exportSaved()
        }
        pendingZip = null
        viewModel.consumePreparedExport()
    }

    LaunchedEffect(state.importMessage) {
        val message = state.importMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportMessage()
        }
    }

    LaunchedEffect(state.pendingExportZip) {
        val zip = state.pendingExportZip
        if (zip != null) {
            pendingZip = zip
            exportLauncher.launch("tv-tracker-export.zip")
        }
    }

    if (showImportDialog) {
        ImportTvTimeDialog(
            onDismiss = { showImportDialog = false },
            onContinue = {
                showImportDialog = false
                importLauncher.launch(
                    arrayOf(
                        "text/*",
                        "text/comma-separated-values",
                        "application/csv",
                        "application/vnd.ms-excel",
                    ),
                )
            },
        )
    }

    if (showApiKeyDialog) {
        TmdbApiKeyDialog(
            currentValue = state.tmdbApiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { value ->
                showApiKeyDialog = false
                viewModel.saveTmdbApiKey(value)
            },
        )
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            currentMode = state.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChromeVisibility(visible = chromeVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryHeader(
                        busy = state.importing || state.exporting,
                        importing = state.importing,
                        exporting = state.exporting,
                        onImport = { showImportDialog = true },
                        onExport = viewModel::prepareExport,
                        onApiKey = { showApiKeyDialog = true },
                        onTheme = { showThemeDialog = true },
                    )

                    if (state.importing) {
                        ImportingBanner()
                    }

                    LibraryFilters(
                        query = state.query,
                        hideWatched = state.hideWatched,
                        typeFilter = state.typeFilter,
                        onQueryChange = viewModel::setQuery,
                        onHideWatchedChange = viewModel::setHideWatched,
                        onTypeFilterChange = viewModel::setTypeFilter,
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyLibrary(hasApiKey = state.tmdbApiKey.isNotBlank())
            } else if (library.isEmpty()) {
                EmptyFilteredLibrary()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = library,
                        key = { "${it.mediaType}-${it.tmdbId}" },
                    ) { item ->
                        LibraryRow(
                            item = item,
                            onOpen = {
                                if (item.mediaType == MediaType.Tv) onOpenTv(item.tmdbId)
                            },
                            onWatchedChange = { watched ->
                                viewModel.setWatched(item.tmdbId, item.mediaType, watched)
                            },
                            canMarkWatched = item.mediaType == MediaType.Tv || isReleased(item.releaseDate),
                            nextEpisode = state.nextEpisodes[item.tmdbId],
                            onWatchNext = { nextEpisode ->
                                viewModel.markNextEpisodeWatched(nextEpisode)
                            },
                            onRemove = { viewModel.remove(item.tmdbId, item.mediaType) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Importing TV Time data...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This can take a few minutes while shows are matched and episodes are saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ImportTvTimeDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from TV Time") },
        text = {
            Text(
                "Select the two CSV files from your TV Time data export: user_tv_show_data.csv and seen_episode_source.csv. The app will import watched series and episodes into your local library.",
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Select files")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TmdbApiKeyDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TMDb API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter your TMDb API key. It is saved only on this device and used for search, imports, metadata, and notifications.")
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value) }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = { onSelect(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.System -> "Use system setting"
                                    ThemeMode.Light -> "Light"
                                    ThemeMode.Dark -> "Dark"
                                },
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun LibraryHeader(
    busy: Boolean,
    importing: Boolean,
    exporting: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onApiKey: () -> Unit,
    onTheme: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    AppHeader(
        title = "TV Tracker",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row {
            MoreVertButton(onClick = { menuOpen = true }, enabled = !busy)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("TMDb API key") },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        onApiKey()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Theme") },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        onTheme()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (importing) "Importing..." else "Import TV Time CSVs") },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        onImport()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (exporting) "Exporting..." else "Export TV Time ZIP") },
                    enabled = !busy,
                    onClick = {
                        menuOpen = false
                        onExport()
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryFilters(
    query: String,
    hideWatched: Boolean,
    typeFilter: LibraryTypeFilter,
    onQueryChange: (String) -> Unit,
    onHideWatchedChange: (Boolean) -> Unit,
    onTypeFilterChange: (LibraryTypeFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Filter library") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = typeFilter == LibraryTypeFilter.All,
                    onClick = { onTypeFilterChange(LibraryTypeFilter.All) },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = typeFilter == LibraryTypeFilter.Series,
                    onClick = { onTypeFilterChange(LibraryTypeFilter.Series) },
                    label = { Text("TV") },
                )
                FilterChip(
                    selected = typeFilter == LibraryTypeFilter.Movies,
                    onClick = { onTypeFilterChange(LibraryTypeFilter.Movies) },
                    label = { Text("Film") },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Watched", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = !hideWatched,
                    onCheckedChange = { showWatched -> onHideWatchedChange(!showWatched) },
                )
            }
        }
    }
}

private fun android.content.Context.readImportFile(uri: Uri): TvTimeImportFile? {
    val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: return null

    val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: return null
    return TvTimeImportFile(name = name, content = content)
}

private fun android.content.Context.writeExportFile(uri: Uri, bytes: ByteArray): Boolean {
    return runCatching {
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        true
    }.getOrDefault(false)
}

@Composable
private fun EmptyLibrary(hasApiKey: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasApiKey) {
                    "Search for a movie or series, or import your TV Time CSVs from the menu."
                } else {
                    "Open the menu, add your TMDb API key, then search or import your TV Time CSVs."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyFilteredLibrary() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = "No titles match these filters.",
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryRow(
    item: LibraryItemEntity,
    onOpen: () -> Unit,
    onWatchedChange: (Boolean) -> Unit,
    canMarkWatched: Boolean,
    nextEpisode: NextEpisode?,
    onWatchNext: (NextEpisode) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.mediaType == MediaType.Tv, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            MediaPoster(
                posterPath = item.posterPath,
                title = item.title,
                modifier = Modifier.size(width = 72.dp, height = 108.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRemove) {
                        Text("X")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = {}, label = { Text(item.mediaType.label()) })
                    item.releaseDate?.takeIf { it.isNotBlank() }?.let {
                        AssistChip(onClick = {}, label = { Text(it.take(4)) })
                    }
                    RatingText(item.voteAverage)
                }
                MetadataBlock(voteAverage = null, cast = item.cast)
                if (item.mediaType == MediaType.Movie) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.watched,
                            enabled = canMarkWatched,
                            onCheckedChange = onWatchedChange,
                        )
                        Text(
                            when {
                                item.watched -> "Watched"
                                canMarkWatched -> "Not watched"
                                else -> "Not released"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (nextEpisode != null) {
                    Text(
                        text = "Next: S${nextEpisode.seasonNumber} E${nextEpisode.episodeNumber} ${nextEpisode.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.mediaType == MediaType.Tv) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpen,
                            modifier = Modifier.weight(0.42f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                            )
                        }
                        Button(
                            onClick = { nextEpisode?.let(onWatchNext) },
                            enabled = nextEpisode != null,
                            modifier = Modifier.weight(0.58f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = "Watch next",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
