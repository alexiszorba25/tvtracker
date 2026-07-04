package com.alexis.tvtracker.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexis.tvtracker.data.AppContainer
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.ui.library.LibraryScreen
import com.alexis.tvtracker.ui.library.LibraryViewModel
import com.alexis.tvtracker.ui.search.SearchScreen
import com.alexis.tvtracker.ui.search.SearchViewModel
import com.alexis.tvtracker.ui.tv.TvDetailsScreen
import com.alexis.tvtracker.ui.tv.TvDetailsViewModel
import kotlin.math.sign

@Composable
fun TvTrackerApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()
    val showBottomBar = route == Screen.Library.route || route == Screen.Search.route
    var chromeVisible by remember { mutableStateOf(true) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero

                val sameDirection = scrollAccumulator == 0f || scrollAccumulator.sign == delta.sign
                scrollAccumulator = if (sameDirection) {
                    (scrollAccumulator + delta).coerceIn(-160f, 160f)
                } else {
                    delta
                }

                when {
                    scrollAccumulator < -72f && chromeVisible -> {
                        chromeVisible = false
                        scrollAccumulator = 0f
                    }
                    scrollAccumulator > 48f && !chromeVisible -> {
                        chromeVisible = true
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    val bottomOffset by animateDpAsState(
        targetValue = if (showBottomBar && chromeVisible) 0.dp else 72.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottom-bar-offset",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (showBottomBar && chromeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "bottom-bar-alpha",
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier
                        .height(52.dp)
                        .offset(y = bottomOffset)
                        .graphicsLayer { alpha = bottomAlpha }
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    tonalElevation = 2.dp,
                ) {
                    NavigationBarItem(
                        selected = route == Screen.Library.route,
                        onClick = {
                            navController.navigate(Screen.Library.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        },
                        icon = {},
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = route == Screen.Search.route,
                        onClick = {
                            navController.navigate(Screen.Search.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        },
                        icon = {},
                        label = { Text("Search") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollConnection),
        ) {
            composable(Screen.Library.route) {
                val viewModel: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.factory(
                        libraryRepository = container.libraryRepository,
                        episodeRepository = container.episodeRepository,
                        tmdbRepository = container.tmdbRepository,
                        tvTimeImportRepository = container.tvTimeImportRepository,
                        tvTimeExportRepository = container.tvTimeExportRepository,
                        apiKeyRepository = container.apiKeyRepository,
                        uiSettingsRepository = container.uiSettingsRepository,
                    ),
                )
                LibraryScreen(
                    viewModel = viewModel,
                    chromeVisible = chromeVisible,
                    onOpenTv = { id -> navController.navigate(Screen.TvDetails.createRoute(id)) },
                )
            }

            composable(Screen.Search.route) {
                val viewModel: SearchViewModel = viewModel(
                    factory = SearchViewModel.factory(
                        tmdbRepository = container.tmdbRepository,
                        libraryRepository = container.libraryRepository,
                        apiKeyRepository = container.apiKeyRepository,
                    ),
                )
                SearchScreen(viewModel, chromeVisible = chromeVisible)
            }

            composable(
                route = Screen.TvDetails.route,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                val showId = entry.arguments?.getInt("id") ?: return@composable
                val viewModel: TvDetailsViewModel = viewModel(
                    key = "tv-$showId",
                    factory = TvDetailsViewModel.factory(
                        showId = showId,
                        tmdbRepository = container.tmdbRepository,
                        episodeRepository = container.episodeRepository,
                    ),
                )
                TvDetailsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object TvDetails : Screen("tv/{id}") {
        fun createRoute(id: Int) = "tv/$id"
    }
}

fun MediaType.label(): String {
    return when (this) {
        MediaType.Movie -> "Movie"
        MediaType.Tv -> "Series"
    }
}
