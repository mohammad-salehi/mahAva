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
        const val MORNING_WORK = "mahava_recommendation_0800"
        /** The daily recommendation always goes out at 08:00 local time; there is no setting. */
        const val RECOMMENDATION_HOUR = 8

        /**
         * Reminders that only make sense at one time of day. Older installs saved 09:00 for
         * every reminder, so the stored hour is ignored for these.
         */
        fun fixedTime(id: String): Pair<Int, Int>? = when (id) {
            "sleep" -> 22 to 30   // wind down before bed
            "water" -> 13 to 0    // around noon
            else -> null
        }

        /** Delivery time (hour, minute) for a reminder: fixed for sleep/water, else the saved time. */
        fun timeFor(pref: com.mahava.app.data.db.ReminderPrefEntity): Pair<Int, Int> =
            fixedTime(pref.id) ?: (pref.hour to pref.minute)

        /** Minutes since midnight a reminder becomes due. */
        fun dueMinute(pref: com.mahava.app.data.db.ReminderPrefEntity): Int =
            timeFor(pref).let { (h, m) -> h * 60 + m }

        /** Milliseconds from [now] to the next 08:00 (tomorrow when it is already 08:00 or later). */
        fun delayUntilNextMorning(now: java.time.ZonedDateTime): Long {
            var next = now.toLocalDate().atTime(RECOMMENDATION_HOUR, 0).atZone(now.zone)
            if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(RECOMMENDATION_HOUR, 0).atZone(now.zone)
            return java.time.Duration.between(now, next).toMillis()
        }
    }

    /** One exact-ish job at the next 08:00; re-planned after every run, on boot and on clock changes. */
    fun scheduleMorning(policy: androidx.work.ExistingWorkPolicy = androidx.work.ExistingWorkPolicy.REPLACE) {
        val delay = delayUntilNextMorning(java.time.ZonedDateTime.now())
        val req = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MORNING_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MORNING_WORK, policy, req)
    }

    fun ensurePeriodic() {
        // Check a few times a day so the morning tip / daily recommendation fires reliably.
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
        scheduleMorning()
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(MORNING_WORK)
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
        // The 08:00 job plans tomorrow's run first (appended, so it doesn't cancel this run).
        if (tags.contains(ReminderScheduler.MORNING_WORK)) runCatching { app.reminderScheduler.scheduleMorning(androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE) }
        if (!app.reminderScheduler.canPostNotifications()) return Result.success()
        val profile = app.database.profileDao().get() ?: return Result.success()
        if (!profile.onboardingDone) return Result.success()
        // Husband accounts don't get cycle tips / women's reminders.
        if (app.prefs.getAccountRole() == "male") return Result.success()
        val reminders = app.database.reminderDao().getAll().filter { it.enabled }.associateBy { it.id }
        val private = profile.privateNotifications
        val today = app.clock.today()
        val nowHour = java.time.LocalTime.now(app.clock.zoneId()).hour
        val sp = applicationContext.getSharedPreferences("mahava_reminders", Context.MODE_PRIVATE)
        val pending = mutableListOf<Triple<Int, String, String>>() // id, title, text

        // Always put the real tip/message in the notification (not a vague "you have a reminder").
        // privateNotifications only softens the title on the lock screen.
        fun titleOf(full: String): String =
            if (private) applicationContext.getString(R.string.app_name) else full

        val tools = app.toolsRepository
        val localTime = java.time.Instant.ofEpochMilli(app.clock.nowMillis()).atZone(app.clock.zoneId()).toLocalTime()
        val nowMinute = localTime.hour * 60 + localTime.minute
        // Fixed 08:00; the 15-minute tick only catches up if the phone was off at 8.
        // The daily tip is a subscriber feature: free accounts get no 08:00 notification.
        if (localTime.hour >= ReminderScheduler.RECOMMENDATION_HOUR && app.authRepository.isPremiumEffectiveNow()) {
            val item = tools.ensureRecommendation()
            val recommendation = com.google.gson.Gson().fromJson(item.payloadJson,com.mahava.app.health.DailyRecommendation::class.java)
            if (!recommendation.shown) {
                val message=if(private) "توصیهٔ امروز در ماه آماده است." else recommendation.body
                val intent=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("mahava://screen/recommendation"),applicationContext,MainActivity::class.java)
                val pi=PendingIntent.getActivity(applicationContext,1005,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val note=NotificationCompat.Builder(applicationContext,ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_bell).setContentTitle(if(private) "ماه" else "توصیه امروز")
                    .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pi).setAutoCancel(true).build()
                try {
                    NotificationManagerCompat.from(applicationContext).notify(1005,note)
                    tools.save(item.id,item.type,recommendation.copy(shown=true),item.epochDay)
                } catch (_: SecurityException) { }
            }
        }

        reminders["daily_log"]?.let { pref ->
            val already = sp.getLong("daily_notified_day", -1L) == today.toEpochDay()
            val logged = app.database.dailyLogDao().getByDay(today.toEpochDay()) != null
            if (!already && !logged && localTime.hour*60+localTime.minute >= pref.hour*60+pref.minute) {
                pending += Triple(
                    1001,
                    titleOf("ثبت حال امروز"),
                    applicationContext.getString(R.string.notification_daily_text)
                )
                sp.edit().putLong("daily_notified_day", today.toEpochDay()).apply()
            }
        }
        reminders["period"]?.let {
            val periods = app.database.periodDao().getAll()
            val result = app.repository.computeCycle(profile, periods)
            val est = result.prediction as? com.mahava.app.cycle.PredictionKind.Estimate
            val days = result.daysUntilCentralPeriod
            if (est != null && days != null && days in 0..2 && !result.periodOngoing && localTime.hour*60+localTime.minute>=it.hour*60+it.minute) {
                val key = est.nextPeriodStartCentral.toEpochDay()
                if (sp.getLong("period_notified_central", -1L) != key) {
                    pending += Triple(
                        1002,
                        titleOf("نزدیک پریود"),
                        applicationContext.getString(R.string.notification_period_text)
                    )
                    sp.edit().putLong("period_notified_central", key).apply()
                }
            }
        }
        reminders["water"]?.let { pref ->
            val already = sp.getLong("water_notified_day", -1L) == today.toEpochDay()
            if (!already && nowMinute >= ReminderScheduler.dueMinute(pref)) {
                pending += Triple(
                    1003,
                    titleOf("یادآوری آب"),
                    applicationContext.getString(R.string.notification_water_text)
                )
                sp.edit().putLong("water_notified_day", today.toEpochDay()).apply()
            }
        }
        reminders["sleep"]?.let { pref ->
            val already = sp.getLong("sleep_notified_day", -1L) == today.toEpochDay()
            // 22:30; the 15-minute tick delivers it between 22:30 and about 22:45.
            if (!already && nowMinute >= ReminderScheduler.dueMinute(pref)) {
                pending += Triple(
                    1004,
                    titleOf("وقت استراحت"),
                    applicationContext.getString(R.string.notification_sleep_text)
                )
                sp.edit().putLong("sleep_notified_day", today.toEpochDay()).apply()
            }
        }
        reminders["medication"]?.let { pref ->
            if (sp.getLong("medication_day",-1)!=today.toEpochDay() && localTime.hour*60+localTime.minute>=pref.hour*60+pref.minute) {
                pending += Triple(1006,titleOf("یادآوری داروی ثبت‌شده"),pref.medicationLabel ?: "یادآوری دارویی که خودت ثبت کرده‌ای؛ مطابق دستور پزشکت عمل کن.")
                sp.edit().putLong("medication_day",today.toEpochDay()).apply()
            }
        }
        if (pending.isEmpty()) return Result.success()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pending.forEach { (id, title, text) ->
            val notif = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(if(private) "یک یادآوری در ماه داری." else text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if(private) "یک یادآوری در ماه داری." else text))
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
