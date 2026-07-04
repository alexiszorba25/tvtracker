package com.alexis.tvtracker.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexis.tvtracker.MainActivity
import com.alexis.tvtracker.R
import com.alexis.tvtracker.data.AppContainer
import com.alexis.tvtracker.model.MediaType
import com.alexis.tvtracker.util.hasAired
import java.time.LocalDate

class NewEpisodeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        val shows = container.libraryRepository.getLibrary()
            .filter { it.mediaType == MediaType.Tv && !it.watched }
        if (shows.isEmpty()) return Result.success()

        val watched = container.episodeRepository.getAllWatchedEpisodes()
            .groupBy { it.showId }
            .mapValues { (_, episodes) ->
                episodes.map { it.seasonNumber to it.episodeNumber }.toSet()
            }
        val notified = applicationContext.getSharedPreferences("episode_notifications", Context.MODE_PRIVATE)

        shows.forEach { show ->
            runCatching {
                val details = container.tmdbRepository.getTvDetails(show.tmdbId)
                details.seasons
                    .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                    .sortedBy { it.seasonNumber }
                    .forEach { season ->
                        val seasonDetails = container.tmdbRepository.getSeason(show.tmdbId, season.seasonNumber)
                        container.episodeRepository.cacheEpisodes(
                            showId = show.tmdbId,
                            seasonNumber = season.seasonNumber,
                            episodes = seasonDetails.episodes,
                        )
                        val watchedForShow = watched[show.tmdbId].orEmpty()
                        seasonDetails.episodes
                            .sortedBy { it.episodeNumber }
                            .filter { episode ->
                                hasAired(episode.airDate) &&
                                    airedRecently(episode.airDate) &&
                                    (season.seasonNumber to episode.episodeNumber) !in watchedForShow
                            }
                            .forEach { episode ->
                                val key = "${show.tmdbId}-${season.seasonNumber}-${episode.episodeNumber}"
                                if (!notified.getBoolean(key, false)) {
                                    val notificationShown = showNotification(
                                        notificationId = key.hashCode(),
                                        title = show.title,
                                        text = "S${season.seasonNumber} E${episode.episodeNumber} is ready to watch",
                                    )
                                    if (notificationShown) {
                                        notified.edit().putBoolean(key, true).apply()
                                    }
                                }
                            }
                    }
            }
        }

        return Result.success()
    }

    private fun airedRecently(date: String?): Boolean {
        if (date.isNullOrBlank()) return false
        return runCatching {
            val airedAt = LocalDate.parse(date)
            val today = LocalDate.now()
            !airedAt.isAfter(today) && !airedAt.isBefore(today.minusDays(7))
        }.getOrDefault(false)
    }

    private fun showNotification(notificationId: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        return true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New episodes",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "new_episodes"
    }
}
