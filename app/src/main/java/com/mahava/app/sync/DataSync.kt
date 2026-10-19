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
 *
 * A job never sits in a long backoff: only network/server hiccups are retried, at most
 * [MAX_ATTEMPTS] times (30 s, 1, 2, 4 min), and every new change replaces a waiting job.
 */
object DataSync {
    private const val NOW = "mahava_data_sync_now"
    private const val PERIODIC = "mahava_data_sync_periodic"
    /** Bumped when the periodic request changes; re-enqueues once so an old backoff is dropped. */
    private const val PERIODIC_VERSION = 2
    internal const val MAX_ATTEMPTS = 5
    /** Short debounce: several quick edits become one round. */
    private const val NOW_DELAY_MS = 1_500L
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun syncNow(context: Context) {
        if (com.mahava.app.BuildConfig.MAH_DEMO) return // offline demo: no server jobs
        val req = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(network)
            .setInitialDelay(NOW_DELAY_MS, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE, not APPEND: a new change must never wait behind an older job that is in backoff.
        // Cancelling a running round is safe: nothing is marked synced until a round finishes, and
        // re-sending a record the server already has (same time, same data) is a no-op there.
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensurePeriodic(context: Context) {
        if (com.mahava.app.BuildConfig.MAH_DEMO) return // offline demo: no server jobs
        val req = PeriodicWorkRequestBuilder<DataSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val sp = context.getSharedPreferences("mahava_data_sync_jobs", Context.MODE_PRIVATE)
        val fresh = sp.getInt("periodic_version", 0) < PERIODIC_VERSION
        // Older builds could leave the periodic job in a multi-hour backoff; replace it once.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            if (fresh) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
        if (fresh) sp.edit().putInt("periodic_version", PERIODIC_VERSION).apply()
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(NOW)
        wm.cancelUniqueWork(PERIODIC)
    }

    /**
     * Retry only what can get better by itself soon (network, timeout, 5xx, rate limit), and only a
     * few times. A record the server refused, an expired login or a required update would just
     * back off for hours; the next change, app start or the periodic round tries again instead.
     */
    fun shouldRetry(outcome: SyncOutcome, runAttemptCount: Int): Boolean {
        if (outcome !is SyncOutcome.Failed) return false
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) return false
        return outcome.reason in setOf(
            SyncFailure.NETWORK, SyncFailure.TIMEOUT, SyncFailure.SERVER, SyncFailure.RATE_LIMIT, SyncFailure.UNKNOWN
        )
    }
}

class DataSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MahavaApplication ?: return Result.success()
        val outcome = app.dataSync.sync()
        val retry = DataSync.shouldRetry(outcome, runAttemptCount)
        SyncLog.i("worker attempt=${runAttemptCount + 1} outcome=${SyncLog.describe(outcome)} retry=$retry")
        return if (retry) Result.retry() else Result.success()
    }
}
