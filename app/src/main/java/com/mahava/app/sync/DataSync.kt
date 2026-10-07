package com.mahava.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mahava.app.MahavaApplication
import java.util.concurrent.TimeUnit

/**
 * Background jobs for the full data sync:
 * - "now": runs as soon as there is internet after a change (offline queue; survives reboot)
 * - "periodic": every 6 hours, picks up changes made on another phone
 */
object DataSync {
    private const val NOW = "mahava_data_sync_now"
    private const val PERIODIC = "mahava_data_sync_periodic"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun syncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<DataSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(NOW)
        wm.cancelUniqueWork(PERIODIC)
    }
}

class DataSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MahavaApplication ?: return Result.success()
        return when (app.dataSync.sync()) {
            is SyncOutcome.Failed -> if (runAttemptCount < 8) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}
