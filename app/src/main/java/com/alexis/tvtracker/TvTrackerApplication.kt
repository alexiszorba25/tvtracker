package com.alexis.tvtracker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alexis.tvtracker.data.AppContainer
import com.alexis.tvtracker.notifications.NewEpisodeWorker
import java.util.concurrent.TimeUnit

class TvTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleNewEpisodeChecks()
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
