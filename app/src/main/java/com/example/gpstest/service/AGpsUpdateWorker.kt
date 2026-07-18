package com.example.gpstest.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gpstest.AppDependencies
import com.example.gpstest.GpsTestApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期性后台任务，定期下载并注入 A-GPS 预测数据。
 *
 * 自动更新间隔由 [AGpsSettings.updateIntervalHours] 控制。
 * 失败时 WorkManager 自动以指数退避重试。
 * 未启用自动更新时直接返回 [Result.success] 跳过执行。
 */
class AGpsUpdateWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    private val dependencies: AppDependencies
        get() = (applicationContext as GpsTestApplication).dependencies

    override suspend fun doWork(): Result {
        val settingsStore = dependencies.agpsSettingsStore
        val settings = settingsStore.settings.first()

        if (!settings.autoUpdateEnabled) {
            return Result.success()
        }

        val repository = dependencies.agpsRepository

        // 先 hydrate，再注入；addRecord 也会 store-merge，双保险防历史被空内存覆盖
        repository.hydrateHistory()
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

        // UPDATE 策略：更改间隔时替换旧的周期性任务而非创建重复任务
        fun schedule(
            context: Context,
            intervalHours: Int,
        ) {
            val request =
                PeriodicWorkRequestBuilder<AGpsUpdateWorker>(
                    intervalHours.toLong(),
                    TimeUnit.HOURS,
                ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
