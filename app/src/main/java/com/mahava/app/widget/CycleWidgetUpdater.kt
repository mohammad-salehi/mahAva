package com.mahava.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mahava.app.MainActivity
import com.mahava.app.MahavaApplication
import com.mahava.app.R
import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.PredictionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Updates the home-screen cycle widget from Room + CycleEngine.
 * Premium-only: without an active subscription shows a locked placeholder (no cycle data).
 */
object CycleWidgetUpdater {
    const val ACTION_REFRESH = "com.mahava.app.widget.ACTION_REFRESH"

    fun requestUpdate(context: Context) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val ids = mgr.getAppWidgetIds(ComponentName(appCtx, CycleWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val intent = Intent(appCtx, CycleWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        appCtx.sendBroadcast(intent)
    }

    suspend fun updateAll(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val app = context.applicationContext as? MahavaApplication
        val premium = isPremium(context)
        val loggedIn = try {
            app?.authRepository?.hasSessionTokens() == true
        } catch (_: Throwable) { false }
        val snapshot = if (premium) loadSnapshot(context) else WidgetSnapshot.empty()
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                buildViews(context, appWidgetManager, id, snapshot, premium, loggedIn)
            )
        }
    }

    private suspend fun isPremium(context: Context): Boolean {
        val app = context.applicationContext as? MahavaApplication ?: return false
        return try {
            app.authRepository.isPremiumEffectiveNow()
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun loadSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? MahavaApplication
            ?: return@withContext WidgetSnapshot.empty()
        val profile = app.database.profileDao().get()
        val periods = app.database.periodDao().getAll()
        val result = app.repository.computeCycle(profile, periods)
        val dayCtx = CycleDayContextResolver.resolve(result)
        val length = (result.prediction as? PredictionKind.Estimate)?.medianCycleLength
            ?: profile?.typicalCycleLength
        WidgetSnapshot(
            hasData = result.cycleDay != null || result.lastPeriodStart != null,
            cycleDay = result.cycleDay,
            cycleLength = length,
            phase = result.phase,
            phaseShort = shortPhaseFa(result.phase, dayCtx.subWindow),
            daysUntil = result.daysUntilCentralPeriod,
            isLate = result.isLate,
            daysLate = result.daysLate,
            periodOngoing = result.periodOngoing
        )
    }

    private fun shortPhaseFa(phase: CyclePhase, sub: CycleSubWindow): String = when (sub) {
        CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE -> "پریود"
        CycleSubWindow.FOLLICULAR_EARLY -> "فولیکولار"
        CycleSubWindow.FOLLICULAR_LATE -> "نزدیک تخمک‌گذاری"
        CycleSubWindow.PERI_OVULATORY -> "تخمک‌گذاری"
        CycleSubWindow.LUTEAL_EARLY -> "لوتئال"
        CycleSubWindow.LUTEAL_MID -> "نیمهٔ دوم"
        CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> "پیش از پریود"
        CycleSubWindow.LATE_PERIOD -> "تأخیر پریود"
        CycleSubWindow.UNKNOWN -> when (phase) {
            CyclePhase.MENSTRUATION -> "پریود"
            CyclePhase.FOLLICULAR -> "فولیکولار"
            CyclePhase.OVULATION_WINDOW -> "تخمک‌گذاری"
            CyclePhase.LUTEAL -> "لوتئال"
            CyclePhase.UNKNOWN -> "—"
        }
    }

    private fun buildViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snap: WidgetSnapshot,
        premium: Boolean,
        loggedIn: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cycle)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            if (premium) {
                putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            } else if (loggedIn) {
                putExtra(MainActivity.EXTRA_OPEN_ACCOUNT, true)
            } else {
                putExtra(MainActivity.EXTRA_OPEN_LOGIN, true)
            }
        }
        val pi = PendingIntent.getActivity(
            context,
            if (premium) 7101 else 7102,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        views.setOnClickPendingIntent(R.id.widget_ring, pi)

        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
        val sizePx = CycleRingBitmapRenderer.sizePxForWidget(context, minW, minH)

        val bitmap = if (!premium) {
            CycleRingBitmapRenderer.renderLocked(context, sizePx)
        } else {
            val phaseTitle = when {
                !snap.hasData -> "اولین پریود را ثبت کن"
                else -> snap.phaseShort
            }
            val model = CycleRingBitmapRenderer.RingModel(
                cycleDay = if (snap.hasData) snap.cycleDay else null,
                cycleLength = snap.cycleLength,
                phase = snap.phase,
                phaseTitleFa = phaseTitle,
                daysUntilPeriod = if (snap.hasData) snap.daysUntil else null,
                isLate = snap.isLate,
                periodOngoing = snap.periodOngoing
            )
            CycleRingBitmapRenderer.render(context, model, sizePx)
        }
        views.setImageViewBitmap(R.id.widget_ring, bitmap)
        return views
    }

    private data class WidgetSnapshot(
        val hasData: Boolean,
        val cycleDay: Int?,
        val cycleLength: Int?,
        val phase: CyclePhase?,
        val phaseShort: String,
        val daysUntil: Int?,
        val isLate: Boolean,
        val daysLate: Int?,
        val periodOngoing: Boolean
    ) {
        companion object {
            fun empty() = WidgetSnapshot(
                hasData = false,
                cycleDay = null,
                cycleLength = null,
                phase = null,
                phaseShort = "—",
                daysUntil = null,
                isLate = false,
                daysLate = null,
                periodOngoing = false
            )
        }
    }
}
