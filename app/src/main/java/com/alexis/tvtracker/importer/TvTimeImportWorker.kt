package com.alexis.tvtracker.importer

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alexis.tvtracker.data.AppContainer
import kotlinx.coroutines.CancellationException

class TvTimeImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        if (!container.apiKeyRepository.hasApiKey()) {
            return Result.failure(workDataOf(OUTPUT_ERROR to "Add your TMDb API key before importing TV Time data."))
        }

        val fileNames = inputData.getStringArray(INPUT_FILE_NAMES).orEmpty().toList().toTypedArray()
        if (fileNames.isEmpty()) {
            return Result.failure(workDataOf(OUTPUT_ERROR to "Select TV Time CSV files before importing."))
        }

        return runCatching {
            val files = container.tvTimeImportRepository.readPendingImportFiles(fileNames)
            val result = container.tvTimeImportRepository.import(files) { progress ->
                setProgress(
                    workDataOf(
                        PROGRESS_PROCESSED_SHOWS to progress.processedShows,
                        PROGRESS_TOTAL_SHOWS to progress.totalShows,
                        PROGRESS_CURRENT_SHOW to progress.currentShow,
                    ),
                )
            }
            container.tvTimeImportRepository.clearPendingImportFiles(fileNames)
            Result.success(
                workDataOf(
                    OUTPUT_IMPORTED_SHOWS to result.importedShows,
                    OUTPUT_IMPORTED_EPISODES to result.importedEpisodes,
                    OUTPUT_SKIPPED_SHOWS to result.skippedShows,
                ),
            )
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "TV Time import failed", error)
            Result.failure(workDataOf(OUTPUT_ERROR to (error.message ?: "TV Time import failed")))
        }
    }
}

fun enqueueTvTimeImportWork(
    context: Context,
    fileNames: Array<String>,
    policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
) {
    val request = OneTimeWorkRequestBuilder<TvTimeImportWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setInputData(workDataOf(INPUT_FILE_NAMES to fileNames))
        .build()

    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        TV_TIME_IMPORT_WORK_NAME,
        policy,
        request,
    )
}

const val TV_TIME_IMPORT_WORK_NAME = "tv-time-import"
const val INPUT_FILE_NAMES = "fileNames"
const val OUTPUT_ERROR = "error"
const val OUTPUT_IMPORTED_EPISODES = "importedEpisodes"
const val OUTPUT_IMPORTED_SHOWS = "importedShows"
const val OUTPUT_SKIPPED_SHOWS = "skippedShows"
const val PROGRESS_CURRENT_SHOW = "currentShow"
const val PROGRESS_PROCESSED_SHOWS = "processedShows"
const val PROGRESS_TOTAL_SHOWS = "totalShows"

private const val TAG = "TvTimeImportWorker"
