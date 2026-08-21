package com.jeremiah.phoneusage.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jeremiah.phoneusage.data.UsageRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic local collection. WorkManager survives reboots and app updates, and unlike a
 * long-lived foreground service it is not subject to the daily foreground-service timeout
 * introduced in Android 15.
 */
class CollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { UsageRepository(applicationContext).collect() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val UNIQUE_NAME = "usage-collection"
        private const val INTERVAL_MINUTES = 30L

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<CollectWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
