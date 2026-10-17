package com.mahava.app.health

import com.mahava.app.cycle.CycleEngineResult
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.HealthAlertEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.PregnancyEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.health.models.AlertSeverity
import com.mahava.app.health.models.AlertStatus
import com.mahava.app.health.models.AppHealthMode
import com.mahava.app.health.models.CycleHomeSummary
import com.mahava.app.health.models.FullAnalyticsData
import com.mahava.app.health.models.HealthAlert
import com.mahava.app.health.models.HealthInsight
import com.mahava.app.health.models.PregnancyProgress
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

data class HealthUiSnapshot(
    val appMode: AppHealthMode,
    val pregnancy: PregnancyEntity?,
    val pregnancyProgress: PregnancyProgress?,
    val todayLog: DailyLogEntity?,
    val currentCycleSummary: CycleHomeSummary?,
    val homeChartData: List<com.mahava.app.health.models.HomeChartPoint>,
    val fullAnalyticsData: FullAnalyticsData,
    val relevantInsights: List<HealthInsight>,
    val activeAlerts: List<HealthAlert>,
    val analyticsRangeMonths: Int = 6
)

object HealthStore {

    fun snapshot(
        profile: UserProfileEntity?,
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        cycle: CycleEngineResult?,
        pregnancy: PregnancyEntity?,
        persistedAlerts: List<HealthAlertEntity>,
        today: LocalDate,
        nowMillis: Long,
        content: ScientificContentService,
        analyticsRangeMonths: Int = 6
    ): HealthUiSnapshot {
        val pregActive = pregnancy != null && pregnancy.status == "active"
        val mode = if (pregActive || profile?.pregnancyMode == true) AppHealthMode.PREGNANCY else AppHealthMode.CYCLE
        val progress = if (pregActive) PregnancyService.progressFromEntity(pregnancy!!, today) else null
        val todayLog = logs.find { it.epochDay == today.toEpochDay() }
        val chart = CycleAnalyticsService.homeSymptomChart(logs, today)
        val analytics = CycleAnalyticsService.analyze(
            periods, logs, today, analyticsRangeMonths, profile?.typicalCycleLength
        )
        val cycleSummary = if (mode == AppHealthMode.CYCLE) {
            CycleHomeSummary(
                cycleDay = cycle?.cycleDay,
                phaseLabelFa = phaseFa(cycle?.phase) + if (cycle?.phase != null && cycle.phase != CyclePhase.UNKNOWN) " (تخمین)" else "",
                phaseIsEstimate = true,
                nextPeriodLabelFa = nextPeriodFa(cycle),
                historySummaryFa = historyFa(analytics),
                chart = chart
            )
        } else null
        val insights = content.insightsForHome(cycle, progress?.contentWeek?.coerceAtMost(42))
        val computed = HealthRuleEngine.evaluate(profile, periods, logs, today, pregActive, nowMillis)
        val mergedAlerts = mergeAlerts(computed, persistedAlerts)
        return HealthUiSnapshot(
            appMode = mode,
            pregnancy = pregnancy,
            pregnancyProgress = progress,
            todayLog = todayLog,
            currentCycleSummary = cycleSummary,
            homeChartData = chart,
            fullAnalyticsData = analytics,
            relevantInsights = insights,
            activeAlerts = mergedAlerts.filter { it.status != AlertStatus.DISMISSED },
            analyticsRangeMonths = analyticsRangeMonths
        )
    }

    fun toEntity(a: HealthAlert): HealthAlertEntity = HealthAlertEntity(
        id = a.id,
        ruleId = a.ruleId,
        ruleVersion = a.ruleVersion,
        severity = when (a.severity) {
            AlertSeverity.INFO -> "info"
            AlertSeverity.ATTENTION -> "attention"
            AlertSeverity.URGENT_GUIDANCE -> "urgent_guidance"
        },
        titleFa = a.titleFa,
        plainLanguageExplanationFa = a.plainLanguageExplanationFa,
        recommendedActionFa = a.recommendedActionFa,
        observedDates = a.observedDates.joinToString(","),
        evidenceReferences = a.evidenceReferences.joinToString(","),
        deduplicationKey = a.deduplicationKey,
        status = when (a.status) {
            AlertStatus.ACTIVE -> "active"
            AlertStatus.READ -> "read"
            AlertStatus.DISMISSED -> "dismissed"
        },
        createdAt = a.createdAt,
        updatedAt = a.createdAt
    )

    fun fromEntity(e: HealthAlertEntity): HealthAlert = HealthAlert(
        id = e.id,
        ruleId = e.ruleId,
        ruleVersion = e.ruleVersion,
        severity = when (e.severity) {
            "urgent_guidance" -> AlertSeverity.URGENT_GUIDANCE
            "attention" -> AlertSeverity.ATTENTION
            else -> AlertSeverity.INFO
        },
        titleFa = e.titleFa,
        plainLanguageExplanationFa = e.plainLanguageExplanationFa,
        recommendedActionFa = e.recommendedActionFa,
        observedDates = e.observedDates.split(',').filter { it.isNotBlank() },
        evidenceReferences = e.evidenceReferences.split(',').filter { it.isNotBlank() },
        deduplicationKey = e.deduplicationKey,
        status = when (e.status) {
            "read" -> AlertStatus.READ
            "dismissed" -> AlertStatus.DISMISSED
            else -> AlertStatus.ACTIVE
        },
        createdAt = e.createdAt
    )

    private fun mergeAlerts(computed: List<HealthAlert>, persisted: List<HealthAlertEntity>): List<HealthAlert> {
        val byDedupe = persisted.associateBy { it.deduplicationKey }
        return computed.map { c ->
            val old = byDedupe[c.deduplicationKey]
            if (old != null) c.copy(
                id = old.id,
                status = when (old.status) {
                    "read" -> AlertStatus.READ
                    "dismissed" -> AlertStatus.DISMISSED
                    else -> AlertStatus.ACTIVE
                },
                createdAt = old.createdAt
            ) else c
        }
    }

    private fun phaseFa(p: CyclePhase?): String = when (p) {
        CyclePhase.MENSTRUATION -> "قاعدگی"
        CyclePhase.FOLLICULAR -> "بعد از پریود (فاز فولیکولار)"
        CyclePhase.OVULATION_WINDOW -> "بازهٔ تخمینی تخمک‌گذاری"
        CyclePhase.LUTEAL -> "نیمهٔ دوم چرخه (فاز لوتئال)"
        else -> "نامشخص"
    }

    private fun nextPeriodFa(cycle: CycleEngineResult?): String? {
        val days = cycle?.daysUntilCentralPeriod ?: return null
        if (cycle.prediction !is com.mahava.app.cycle.PredictionKind.Estimate) return null
        return when {
            cycle.isLate -> "پریود از تخمین عقب افتاده (قطعیت ندارد)"
            days == 0 -> "تخمین: حوالی امروز"
            days > 0 -> "تخمین: حدود ${PersianDigits.toPersian(days)} روز تا پریود بعدی"
            else -> null
        }
    }

    private fun historyFa(a: FullAnalyticsData): String {
        if (a.completedCycleCount == 0) return "هنوز چرخهٔ کامل‌شدهٔ کافی نداریم."
        val mean = a.meanCycle?.let { PersianDigits.toPersian(it.toInt()) } ?: "—"
        return "${PersianDigits.toPersian(a.completedCycleCount)} چرخهٔ کامل؛ میانگین تقریبی $mean روز."
    }
}
