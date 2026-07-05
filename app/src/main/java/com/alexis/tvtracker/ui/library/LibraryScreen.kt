package com.alexis.tvtracker.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alexis.tvtracker.data.NextEpisode
import com.alexis.tvtracker.data.ThemeMode
import com.alexis.tvtracker.data.TvTimeImportFile
import com.alexis.tvtracker.data.TvTimeImportProgress
import com.alexis.tvtracker.data.local.LibraryItemEntity
import com.alexis.tvtracker.ui.common.AppHeader
import com.alexis.tvtracker.ui.common.ChromeVisibility
import com.alexis.tvtracker.ui.common.MediaPoster
import com.alexis.tvtracker.ui.common.RatingText
import com.alexis.tvtracker.ui.common.MoreVertButton
import com.alexis.tvtracker.ui.label
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, chromeVisible: Boolean, onOpenTv: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val activeItems = when (state.watchNextFilter) {
        WatchNextFilter.ContinueWatching -> state.continueWatchingItems
        WatchNextFilter.UpToDate -> state.upToDateItems
    }
    val hasVisibleItems = activeItems.isNotEmpty() ||
        (state.watchNextFilter == WatchNextFilter.ContinueWatching && state.staleWatchingItems.isNotEmpty())
    val continueWatchingListState = rememberLazyListState()
    val upToDateListState = rememberLazyListState()
    val activeListState = when (state.watchNextFilter) {
        WatchNextFilter.ContinueWatching -> continueWatchingListState
        WatchNextFilter.UpToDate -> upToDateListState
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingZip by remember { mutableStateOf<ByteArray?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val files = uris.flatMap { uri -> context.readImportFiles(uri) }
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

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resumeBackgroundWork()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.importMessage) {
        val message = state.importMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportMessage()
        }
    }

    LaunchedEffect(state.tvTimeImportWork?.id, state.tvTimeImportWork?.successMessage) {
        val workState = state.tvTimeImportWork
        val message = workState?.successMessage
        if (workState != null && message != null && viewModel.shouldShowTvTimeImportWorkMessage(workState.id)) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeTvTimeImportWorkMessage(workState.id)
        }
    }

    LaunchedEffect(state.tvTimeImportWork?.id, state.tvTimeImportWork?.failureMessage) {
        val workState = state.tvTimeImportWork
        val message = workState?.failureMessage
        if (workState != null && message != null && viewModel.shouldShowTvTimeImportWorkMessage(workState.id)) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeTvTimeImportWorkMessage(workState.id)
        }
    }

    LaunchedEffect(state.pendingExportZip) {
        val zip = state.pendingExportZip
        if (zip != null) {
            pendingZip = zip
            exportLauncher.launch("tv-tracker-export.zip")
        }
    }

    LaunchedEffect(state.postImportMessage) {
        if (state.postImportMessage != null) {
            delay(3_000)
            viewModel.clearPostImportMessage()
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
                        "application/zip",
                        "application/x-zip-compressed",
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
                        busy = state.isImporting || state.exporting,
                        importing = state.isImporting,
                        exporting = state.exporting,
                        onImport = { showImportDialog = true },
                        onExport = viewModel::prepareExport,
                        onApiKey = { showApiKeyDialog = true },
                        onTheme = { showThemeDialog = true },
                    )

                    if (state.isImporting) {
                        ImportingBanner(progress = state.activeImportProgress)
                    }

                    state.postImportMessage?.let {
                        PostImportBanner(message = it)
                    }

                    state.episodeMetadataWork
                        ?.takeIf { it.isActive || it.hasFailed }
                        ?.let { workState ->
                            EpisodeMetadataBanner(workState)
                        }

                    LibraryFilters(
                        query = state.query,
                        selectedFilter = state.watchNextFilter,
                        onQueryChange = viewModel::setQuery,
                        onFilterChange = viewModel::setWatchNextFilter,
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyLibrary(hasApiKey = state.tmdbApiKey.isNotBlank())
            } else if (!hasVisibleItems) {
                EmptyFilteredLibrary()
            } else {
                LazyColumn(
                    state = activeListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = activeItems,
                        key = { "${state.watchNextFilter}-${it.tmdbId}" },
                    ) { item ->
                        WatchNextRow(
                            item = item,
                            nextEpisode = state.nextEpisodes[item.tmdbId],
                            metadataLoading = item.tmdbId in state.loadingEpisodeMetadataShowIds,
                            onOpen = { onOpenTv(item.tmdbId) },
                            onWatchNext = viewModel::markNextEpisodeWatched,
                        )
                    }
                    if (
                        state.watchNextFilter == WatchNextFilter.ContinueWatching &&
                        state.staleWatchingItems.isNotEmpty()
                    ) {
                        item(key = "stale-heading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Haven't Watched In A While",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = state.staleWatchingItems.size.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = state.staleWatchingItems,
                            key = { "stale-${it.tmdbId}" },
                        ) { item ->
                            WatchNextRow(
                                item = item,
                                nextEpisode = state.nextEpisodes[item.tmdbId],
                                metadataLoading = item.tmdbId in state.loadingEpisodeMetadataShowIds,
                                onOpen = { onOpenTv(item.tmdbId) },
                                onWatchNext = viewModel::markNextEpisodeWatched,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeMetadataBanner(workState: EpisodeMetadataWorkState) {
    val isActive = workState.isActive
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
                text = if (isActive) {
                    "Loading episode metadata in background..."
                } else {
                    "Episode metadata loading stopped."
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    isActive && workState.totalShows > 0 && workState.currentShow.isNotBlank() ->
                        "Caching ${workState.processedShows}/${workState.totalShows}: ${workState.currentShow}"
                    isActive && workState.totalShows > 0 ->
                        "Caching ${workState.processedShows}/${workState.totalShows} shows"
                    isActive ->
                        "Waiting for network..."
                    else ->
                        "Open the app again or run the TV Time import to retry."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isActive && workState.totalShows > 0) {
                LinearProgressIndicator(
                    progress = {
                        workState.processedShows.toFloat() / workState.totalShows.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isActive) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PostImportBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportingBanner(progress: TvTimeImportProgress?) {
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
                text = if (progress == null) {
                    "Preparing TV Time import..."
                } else {
                    "Importing ${progress.processedShows}/${progress.totalShows}: ${progress.currentShow}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress == null || progress.totalShows <= 0) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.processedShows.toFloat() / progress.totalShows.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                "Select a TV Tracker export ZIP, or select user_tv_show_data.csv and seen_episode_source.csv. For better episode history and recent activity, also include tracking-prod-records-v2.csv and show_seen_episode_latest.csv.",
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
    selectedFilter: WatchNextFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (WatchNextFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search Library") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedFilter == WatchNextFilter.ContinueWatching,
                    onClick = { onFilterChange(WatchNextFilter.ContinueWatching) },
                    label = {
                        Text(
                            text = "Continue Watching",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = selectedFilter == WatchNextFilter.UpToDate,
                    onClick = { onFilterChange(WatchNextFilter.UpToDate) },
                    label = {
                        Text(
                            text = "Up To Date",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun android.content.Context.readImportFiles(uri: Uri): List<TvTimeImportFile> {
    val name = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: return emptyList()

    return if (name.endsWith(".zip", ignoreCase = true)) {
        readImportZip(uri)
    } else {
        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return emptyList()
        listOf(TvTimeImportFile(name = name, content = content))
    }
}

private fun android.content.Context.readImportZip(uri: Uri): List<TvTimeImportFile> {
    return runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                buildList {
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                            add(
                                TvTimeImportFile(
                                    name = entry.name.substringAfterLast('/').substringAfterLast('\\'),
                                    content = zip.readCurrentEntryText(),
                                ),
                            )
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun ZipInputStream.readCurrentEntryText(): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
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
private fun WatchNextRow(
    item: LibraryItemEntity,
    nextEpisode: NextEpisode?,
    metadataLoading: Boolean,
    onOpen: () -> Unit,
    onWatchNext: (NextEpisode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180, easing = FastOutSlowInEasing)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaPoster(
                posterPath = item.posterPath,
                title = item.title,
                modifier = Modifier
                    .size(width = 88.dp, height = 132.dp)
                    .clickable(onClick = onOpen),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetadataText(text = item.mediaType.label())
                    item.releaseDate?.takeIf { it.length >= 4 }?.let { date ->
                        MetadataText(text = date.take(4))
                    }
                    RatingText(item.voteAverage)
                }
                if (nextEpisode != null) {
                    Text(
                        text = "S${nextEpisode.seasonNumber} E${nextEpisode.episodeNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = nextEpisode.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (metadataLoading) {
                    Text(
                        text = "Loading episode metadata...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "No aired episodes pending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (nextEpisode != null) {
                MarkWatchedButton(onClick = { onWatchNext(nextEpisode) })
            }
        }
    }
}

@Composable
private fun MetadataText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MarkWatchedButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
