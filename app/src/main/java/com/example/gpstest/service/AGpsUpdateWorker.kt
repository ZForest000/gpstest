package com.example.gpstest.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.source.AGpsDataSourceImpl
import com.example.gpstest.data.source.AGpsDownloaderImpl
import com.example.gpstest.domain.repository.AGpsRepositoryImpl
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AGpsUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsStore = AGpsSettingsStore(applicationContext)
        val settings = settingsStore.settings.first()
        
        if (!settings.autoUpdateEnabled) {
            return Result.success()
        }
        
        val dataSource = AGpsDataSourceImpl(applicationContext)
        val downloader = AGpsDownloaderImpl()
        val repository = AGpsRepositoryImpl(
            dataSource = dataSource,
            downloader = downloader,
            fileHandler = com.example.gpstest.data.local.AGpsFileHandlerImpl(applicationContext),
            settingsStore = settingsStore
        )
        
        val result = repository.downloadAndInject()
        
        return if (result.isSuccess) {
            settingsStore.updateLastAutoUpdateTime(System.currentTimeMillis())
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "agps_update_work"

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<AGpsUpdateWorker>(
                intervalHours.toLong(),
                TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
