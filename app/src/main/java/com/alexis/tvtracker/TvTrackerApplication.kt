package com.alexis.tvtracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alexis.tvtracker.data.AppContainer
import com.alexis.tvtracker.importer.enqueueTvTimeImportWork
import com.alexis.tvtracker.metadata.enqueueEpisodeMetadataWork
import com.alexis.tvtracker.notifications.NewEpisodeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TvTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var searchPrefetchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleNewEpisodeChecks()
        resumePendingTvTimeImport()
        enqueueEpisodeMetadataWork(this, ExistingWorkPolicy.REPLACE)
        prefetchSearchDiscovery()
    }

    fun prefetchSearchDiscovery() {
        if (!container.apiKeyRepository.hasApiKey()) return
        if (searchPrefetchJob?.isActive == true) return
        val (popular, upcoming) = container.tmdbRepository.cachedDiscovery()
        if (popular.isNotEmpty() && upcoming.isNotEmpty()) return

        searchPrefetchJob = applicationScope.launch {
            runCatching {
                container.tmdbRepository.suggestions(forceRefresh = false)
                container.tmdbRepository.upcoming(forceRefresh = false)
            }
        }
    }

    private fun resumePendingTvTimeImport() {
        val pendingFileNames = container.tvTimeImportRepository.pendingImportFileNames()
        if (pendingFileNames.isNotEmpty() && container.apiKeyRepository.hasApiKey()) {
            enqueueTvTimeImportWork(this, pendingFileNames, ExistingWorkPolicy.KEEP)
        }
    }

    private fun scheduleNewEpisodeChecks() {
        val request = PeriodicWorkRequestBuilder<NewEpisodeWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "new-episode-checks",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
