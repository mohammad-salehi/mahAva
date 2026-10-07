package com.mahava.app.partner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mahava.app.MainActivity
import com.mahava.app.MahavaApplication
import com.mahava.app.R
import com.mahava.app.content.PartnerAdvice
import com.mahava.app.widget.CycleWidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * No push service (FCM is unreliable in Iran): WorkManager polls the server every ~15 minutes
 * while a pairing exists, and the phone shows LOCAL notifications.
 * - Woman: uploads her snapshot when it changed; hears about pairing requests / unpairing.
 * - Man: fetches changes + her latest status, refreshes the widget, and gets one daily status note.
 */
object PartnerSync {
    const val CHANNEL_ID = "mahava_partner"
    private const val PERIODIC = "mahava_partner_sync"
    private const val NOW = "mahava_partner_sync_now"
    private const val SP = "mahava_partner_sync"
    private const val KEY_AWAIT_UNTIL = "await_request_until"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "همراه", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "وضعیت همراه و درخواست‌های اتصال"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<PartnerSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun syncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<PartnerSyncWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    /** After the woman makes a code, keep polling for the man's request for a while. */
    fun awaitRequest(context: Context, minutes: Int) {
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AWAIT_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()
        ensurePeriodic(context)
    }

    fun isAwaiting(context: Context): Boolean =
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).getLong(KEY_AWAIT_UNTIL, 0L) > System.currentTimeMillis()

    fun notify(context: Context, id: Int, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PARTNER, true)
        }
        val pi = PendingIntent.getActivity(context, 7300 + id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }
}

class PartnerSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MahavaApplication ?: return Result.success()
        if (!app.authRepository.hasSessionTokens()) {
            PartnerSync.cancel(app)
            return Result.success()
        }
        val partner = app.partnerRepository
        return try {
            val poll = partner.pollChanges()
            val role = poll.status.role.ifBlank { app.prefs.getAccountRole() }
            val active = poll.status.pair?.status == "active"
            handleEvents(app, poll, role)
            if (role == "male") {
                if (active) {
                    val share = partner.fetchShare()
                    val updates = poll.events.filter { it.kind == "update" }
                    if (updates.isNotEmpty()) {
                        val changes = updates.flatMap { it.changes.orEmpty() }.toSet()
                        PartnerSync.notify(app, 2, "وضعیت تازه",
                            PartnerAdvice.changeLineFa(share?.partnerName, changes, share?.snapshot))
                    }
                    maybeDaily(app, share)
                }
                CycleWidgetUpdater.requestUpdate(app)
            } else if (active) {
                partner.pushSnapshotIfChanged()
            }
            if (poll.status.pair == null && !PartnerSync.isAwaiting(app)) PartnerSync.cancel(app)
            Result.success()
        } catch (_: Throwable) {
            Result.success() // offline or server busy: try again on the next run
        }
    }

    private suspend fun handleEvents(app: MahavaApplication, poll: PartnerRepository.Poll, role: String) {
        val name = PartnerAdvice.name(poll.status.pair?.partner?.name)
        for (e in poll.events) {
            when (e.kind) {
                "pair_requested" -> PartnerSync.notify(app, 1, "درخواست اتصال تازه",
                    "$name می‌خواهد همراهت شود. برای تأیید، اپ ماه را باز کن.")
                "pair_approved" -> PartnerSync.notify(app, 1, "اتصال برقرار شد",
                    "$name درخواستت را تأیید کرد. حالا وضعیتش را می‌بینی.")
                "pair_rejected" -> PartnerSync.notify(app, 1, "درخواست اتصال", "درخواست اتصال تأیید نشد.")
                "unpaired" -> {
                    app.partnerRepository.clearAfterUnpair()
                    CycleWidgetUpdater.requestUpdate(app)
                    PartnerSync.notify(app, 1, "اتصال همراه قطع شد",
                        if (role == "male") "دیگر وضعیت همراهت را نمی‌بینی." else "اطلاعات مشترکت از سرور پاک شد.")
                }
            }
        }
    }

    private suspend fun maybeDaily(app: MahavaApplication, share: com.mahava.app.network.PartnerShareDto?) {
        val snap = share?.snapshot ?: return
        val today = app.clock.today().toEpochDay()
        val hour = java.time.LocalTime.now(app.clock.zoneId()).hour
        if (hour < 9 || app.prefs.getPartnerDailyDay() == today) return
        app.prefs.setPartnerDailyDay(today)
        PartnerSync.notify(app, 3, "وضعیت امروز", PartnerAdvice.dailyLineFa(share.partnerName, snap))
    }
}
