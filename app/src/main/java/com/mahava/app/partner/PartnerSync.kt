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
import com.mahava.app.notice.AppNotices
import com.mahava.app.util.PersianDigits
import com.mahava.app.widget.CycleWidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * No push service (FCM is unreliable in Iran): WorkManager polls the server every ~15 minutes
 * while a pairing exists, and the phone shows LOCAL notifications.
 * - Woman: uploads her snapshot when it changed; pairing news is shown inside the app only.
 * - Man: fetches changes + her latest status, refreshes the widget, gets system notifications
 *   (only while the app is closed) and one daily status note.
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
            val ch = NotificationChannel(CHANNEL_ID, "همسر", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "وضعیت همسرت و خبرهای اتصال"
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

    /**
     * Handle one poll (from the background worker or while the app is open). Every approval and
     * alert goes into the in-app notification list (and pops up as a dialog in the app). Only the
     * husband also gets system notifications, and only while the app is closed.
     */
    suspend fun process(app: MahavaApplication, poll: PartnerRepository.Poll) {
        AppNotices.load(app)
        val partner = app.partnerRepository
        val role = poll.status.role.ifBlank { app.prefs.getAccountRole() }
        val male = role == "male"
        val active = poll.status.pair?.status == "active"
        val system = male && !AppNotices.foreground
        val phone = PersianDigits.toPersian(poll.status.pair?.partner?.phoneMasked.orEmpty())
        for (e in poll.events) {
            when (e.kind) {
                "pair_requested" -> AppNotices.add(app, e.kind, "درخواست اتصال همسر",
                    "همسرت${if (phone.isNotBlank()) " ($phone)" else ""} می‌خواهد به تو وصل شود.", id = "ev-${e.id}")
                "pair_approved" -> {
                    val body = "همسرت اتصال را تأیید کرد. حالا همهٔ چیزهایی را که ثبت می‌کند می‌بینی."
                    AppNotices.add(app, e.kind, "اتصال برقرار شد", body, popup = true, id = "ev-${e.id}")
                    if (system) notify(app, 1, "اتصال برقرار شد", body)
                }
                "pair_rejected" -> {
                    val body = "همسرت درخواست اتصال را تأیید نکرد."
                    AppNotices.add(app, e.kind, "اتصال انجام نشد", body, popup = true, id = "ev-${e.id}")
                    if (system) notify(app, 1, "اتصال انجام نشد", body)
                }
                "unpaired" -> {
                    partner.clearAfterUnpair()
                    CycleWidgetUpdater.requestUpdate(app)
                    val body = if (male) "همسرت اتصال را قطع کرد. دیگر اطلاعاتش را نمی‌بینی."
                    else "همسرت اتصال را قطع کرد. دیگر اطلاعاتت را نمی‌بیند و اطلاعات مشترک از سرور پاک شد."
                    AppNotices.add(app, e.kind, "اتصال همسر قطع شد", body, popup = true, id = "ev-${e.id}")
                    if (system) notify(app, 1, "اتصال همسر قطع شد", body)
                }
            }
        }
        if (male) {
            if (active) {
                val updates = poll.events.filter { it.kind == "update" }
                // Always refresh share while paired. Swallow errors here — the home screen
                // surfaces them when the user opens / retries.
                val share = try { partner.fetchShare() } catch (_: Throwable) { null }
                if (updates.isNotEmpty()) {
                    val changes = updates.flatMap { it.changes.orEmpty() }.toSet()
                    val line = PartnerAdvice.changeLineFa(changes, share?.snapshot)
                    val day = app.clock.today().toEpochDay()
                    AppNotices.add(app, "update", "وضعیت تازهٔ همسرت", line, id = "upd-$day")
                    if (system) notify(app, 2, "وضعیت تازهٔ همسرت", line)
                }
                if (share != null) maybeDaily(app, share, system)
            }
            CycleWidgetUpdater.requestUpdate(app)
        } else if (active) {
            partner.pushSnapshotIfChanged()
        }
    }

    private suspend fun maybeDaily(app: MahavaApplication, share: com.mahava.app.network.PartnerShareDto, system: Boolean) {
        val snap = share.snapshot ?: return
        val today = app.clock.today().toEpochDay()
        val hour = java.time.LocalTime.now(app.clock.zoneId()).hour
        if (hour < 9 || app.prefs.getPartnerDailyDay() == today) return
        app.prefs.setPartnerDailyDay(today)
        val line = PartnerAdvice.dailyLineFa(snap)
        AppNotices.add(app, "daily", "وضعیت امروز همسرت", line, id = "daily-$today")
        if (system) notify(app, 3, "وضعیت امروز همسرت", line)
    }
}

class PartnerSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MahavaApplication ?: return Result.success()
        if (!app.authRepository.hasSessionTokens()) {
            PartnerSync.cancel(app)
            return Result.success()
        }
        return try {
            val poll = app.partnerRepository.pollChanges()
            PartnerSync.process(app, poll)
            if (poll.status.pair == null && !PartnerSync.isAwaiting(app)) PartnerSync.cancel(app)
            Result.success()
        } catch (_: Throwable) {
            Result.success() // offline or server busy: try again on the next run
        }
    }
}
