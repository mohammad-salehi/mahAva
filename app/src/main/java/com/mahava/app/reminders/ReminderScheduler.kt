package com.mahava.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mahava.app.MainActivity
import com.mahava.app.MahavaApplication
import com.mahava.app.R
import com.mahava.app.widget.CycleWidgetUpdater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Reminders via WorkManager only — no SCHEDULE_EXACT_ALARM.
 * Notification text is private by default.
 */
class ReminderScheduler(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "mahava_reminders"
        const val WORK_NAME = "mahava_daily_reminder_tick"
    }

    fun ensurePeriodic() {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
}

class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MahavaApplication
        val reminders = app.database.reminderDao().getAll().filter { it.enabled }.associateBy { it.id }
        if (reminders.isEmpty() || !app.reminderScheduler.canPostNotifications()) return Result.success()
        val profile = app.database.profileDao().get() ?: return Result.success()
        if (!profile.onboardingDone) return Result.success()
        val private = profile.privateNotifications
        val today = app.clock.today()
        val nowHour = java.time.LocalTime.now(app.clock.zoneId()).hour
        val sp = applicationContext.getSharedPreferences("mahava_reminders", Context.MODE_PRIVATE)
        val pending = mutableListOf<Pair<Int, String>>()

        reminders["daily_log"]?.let { pref ->
            val already = sp.getLong("daily_notified_day", -1L) == today.toEpochDay()
            val logged = app.database.dailyLogDao().getByDay(today.toEpochDay()) != null
            if (!already && !logged && nowHour >= pref.hour) {
                pending += 1001 to if (private) applicationContext.getString(R.string.notification_private_text)
                    else applicationContext.getString(R.string.notification_daily_text)
                sp.edit().putLong("daily_notified_day", today.toEpochDay()).apply()
            }
        }
        reminders["period"]?.let {
            val periods = app.database.periodDao().getAll()
            val result = app.repository.computeCycle(profile, periods)
            val est = result.prediction as? com.mahava.app.cycle.PredictionKind.Estimate
            val days = result.daysUntilCentralPeriod
            if (est != null && days != null && days in 0..2 && !result.periodOngoing) {
                val key = est.nextPeriodStartCentral.toEpochDay()
                if (sp.getLong("period_notified_central", -1L) != key) {
                    pending += 1002 to if (private) applicationContext.getString(R.string.notification_private_text)
                        else applicationContext.getString(R.string.notification_period_text)
                    sp.edit().putLong("period_notified_central", key).apply()
                }
            }
        }
        reminders["water"]?.let { pref ->
            val already = sp.getLong("water_notified_day", -1L) == today.toEpochDay()
            if (!already && nowHour >= pref.hour) {
                pending += 1003 to if (private) applicationContext.getString(R.string.notification_private_text)
                    else applicationContext.getString(R.string.notification_water_text)
                sp.edit().putLong("water_notified_day", today.toEpochDay()).apply()
            }
        }
        reminders["sleep"]?.let { pref ->
            val already = sp.getLong("sleep_notified_day", -1L) == today.toEpochDay()
            val hour = if (pref.hour == 9) 21 else pref.hour
            if (!already && nowHour >= hour) {
                pending += 1004 to if (private) applicationContext.getString(R.string.notification_private_text)
                    else applicationContext.getString(R.string.notification_sleep_text)
                sp.edit().putLong("sleep_notified_day", today.toEpochDay()).apply()
            }
        }
        if (pending.isEmpty()) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pending.forEach { (id, text) ->
            val notif = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(applicationContext.getString(R.string.app_name))
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext).notify(id, notif)
            } catch (_: SecurityException) { }
        }
        return Result.success()
    }
}

/**
 * Boot, app update and clock changes: re-enqueue reminders, data sync and the partner
 * poller (unique work, so nothing is duplicated) and refresh the widget.
 */
class BootAndTimeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? MahavaApplication ?: return
        app.reminderScheduler.ensurePeriodic()
        CycleWidgetUpdater.requestUpdate(context)
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                MahavaApplication.scheduleBackgroundWork(app)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
