package com.mahava.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mahava.app.MainActivity
import com.mahava.app.MahavaApplication
import com.mahava.app.BuildConfig
import com.mahava.app.R
import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.PredictionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Updates the home-screen cycle widget from Room + CycleEngine.
 * Premium-only: without an active subscription shows a locked placeholder (no cycle data).
 */
object CycleWidgetUpdater {
    val ACTION_REFRESH = "${BuildConfig.APPLICATION_ID}.widget.ACTION_REFRESH"

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
        val partnerView = loggedIn && try {
            app?.prefs?.accountRole?.first() == "male"
        } catch (_: Throwable) { false }
        val snapshot = when {
            !premium -> WidgetSnapshot.empty()
            partnerView -> loadPartnerSnapshot(context)
            else -> loadSnapshot(context)
        }
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                buildViews(context, appWidgetManager, id, snapshot, premium, loggedIn, partnerView)
            )
        }
    }

    /**
     * Male (partner) account: the ring shows HER status from the last synced share snapshot.
     * Not paired -> "not connected" placeholder (no data).
     */
    private suspend fun loadPartnerSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? MahavaApplication
            ?: return@withContext WidgetSnapshot.empty()
        val repo = app.partnerRepository
        val active = try { repo.isActivelyPaired() } catch (_: Throwable) { false }
        if (!active) return@withContext WidgetSnapshot.empty().copy(phaseShort = "وصل نیست")
        val share = try { repo.share.first() } catch (_: Throwable) { null }
        val s = share?.snapshot ?: return@withContext WidgetSnapshot.empty().copy(phaseShort = "در انتظار")
        val todayEpoch = app.clock.today().toEpochDay()
        val elapsed = s.today?.epochDay?.let { (todayEpoch - it).toInt().coerceAtLeast(0) } ?: 0
        val len = s.cycleLength
        val day = s.cycleDay?.let { d -> val v = d + elapsed; if (len != null && v > len + 30) null else v }
        val until = s.nextPeriodEpochDay?.let { (it - todayEpoch).toInt() } ?: s.daysUntilPeriod
        val phase = when (s.phaseGroup) {
            "menstrual" -> CyclePhase.MENSTRUATION
            "follicular" -> CyclePhase.FOLLICULAR
            "fertile" -> CyclePhase.OVULATION_WINDOW
            "early_luteal", "late_luteal" -> CyclePhase.LUTEAL
            else -> CyclePhase.UNKNOWN
        }
        // Her status in ONE simple word inside the ring.
        val short = com.mahava.app.content.PartnerAdvice.oneWordFa(s)
        WidgetSnapshot(
            hasData = day != null || s.nextPeriodEpochDay != null,
            cycleDay = day,
            cycleLength = len,
            phase = phase,
            phaseShort = short,
            daysUntil = until,
            isLate = s.isLate || (until != null && until < 0),
            daysLate = s.daysLate,
            periodOngoing = s.periodOngoing,
            oneWord = true
        )
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
        loggedIn: Boolean,
        partnerView: Boolean = false
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cycle)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            if (partnerView) {
                putExtra(MainActivity.EXTRA_OPEN_PARTNER, true)
            } else if (premium) {
                putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            } else if (loggedIn) {
                putExtra(MainActivity.EXTRA_OPEN_ACCOUNT, true)
            } else {
                putExtra(MainActivity.EXTRA_OPEN_LOGIN, true)
            }
        }
        val pi = PendingIntent.getActivity(
            context,
            if (partnerView) 7103 else if (premium) 7101 else 7102,
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
                !snap.hasData && partnerView -> snap.phaseShort
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
                periodOngoing = snap.periodOngoing,
                summaryWord = if (partnerView && snap.oneWord) snap.phaseShort else null
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
        val periodOngoing: Boolean,
        /** Husband's widget: phaseShort is her one-word status, drawn big inside the ring. */
        val oneWord: Boolean = false
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
