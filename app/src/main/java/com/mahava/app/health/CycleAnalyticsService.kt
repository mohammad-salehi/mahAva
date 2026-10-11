package com.mahava.app.health

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.health.models.DateOnlyFormat
import com.mahava.app.health.models.FullAnalyticsData
import com.mahava.app.health.models.HomeChartPoint
import com.mahava.app.pattern.CycleTrendsAnalyzer
import java.time.LocalDate

object CycleAnalyticsService {

    /**
     * Completed cycles only: length from period start to next period start.
     * Spotting alone never starts a cycle (periods come from PeriodEventEntity).
     * Current open cycle is excluded from means.
     */
    fun completedCycleLengths(periods: List<PeriodEventEntity>): List<Int> {
        val sorted = periods.sortedBy { it.startEpochDay }
        return sorted.zipWithNext { a, b ->
            (b.startEpochDay - a.startEpochDay).toInt()
        }.filter { it in 15..90 }
    }

    fun bleedLengths(periods: List<PeriodEventEntity>): List<Int> {
        return periods.mapNotNull { p ->
            val end = p.endEpochDay ?: return@mapNotNull null
            val len = (end - p.startEpochDay + 1).toInt()
            len.takeIf { it in 1..20 }
        }
    }

    fun homeSymptomChart(logs: List<DailyLogEntity>, today: LocalDate, days: Int = 14): List<HomeChartPoint> {
        val byDay = logs.associateBy { it.epochDay }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val log = byDay[d.toEpochDay()]
            HomeChartPoint(
                date = DateOnlyFormat.fromLocalDate(d),
                painScore = log?.painScore,
                hasLog = log != null
            )
        }
    }

    fun analyze(
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        today: LocalDate,
        rangeMonths: Int,
        typicalCycleLength: Int? = 28
    ): FullAnalyticsData {
        val from = today.minusMonths(rangeMonths.toLong()).toEpochDay()
        val periodsInRange = periods.filter { it.startEpochDay >= from }
        val logsInRange = logs.filter { it.epochDay >= from }
        val cycles = completedCycleLengths(periodsInRange)
        // Exclude the trailing open cycle: zipWithNext already only completed pairs.
        val bleeds = bleedLengths(periodsInRange.filter { it.endEpochDay != null })
        val meanCycle = cycles.takeIf { it.isNotEmpty() }?.average()
        val meanBleed = bleeds.takeIf { it.isNotEmpty() }?.average()
        val painDays = logsInRange.count { (it.painScore ?: -1) >= 4 }
        val sleepTrend = logsInRange.mapNotNull { log ->
            val mins = log.sleepMinutes ?: log.sleepHours?.let { (it * 60).toInt() } ?: return@mapNotNull null
            DateOnlyFormat.fromEpochDay(log.epochDay) to mins
        }
        val waterTrend = logsInRange.mapNotNull { log ->
            val w = log.waterIntakeMl ?: return@mapNotNull null
            DateOnlyFormat.fromEpochDay(log.epochDay) to w
        }
        val empty = when {
            cycles.isEmpty() && bleeds.isEmpty() && logsInRange.isEmpty() ->
                "هنوز دادهٔ کافی نداریم. با ثبت پریود و حال روزانه، نمودارها اینجا ساخته می‌شوند."
            cycles.isEmpty() ->
                "برای میانگین طول چرخه حداقل دو شروع پریود ثبت‌شده لازم است. الان ${periodsInRange.size} شروع در این بازه داری."
            else -> null
        }
        // Keep PhaseSymptomFreq available via existing analyzer for UI that needs it.
        CycleTrendsAnalyzer.analyze(periodsInRange, logsInRange, typicalCycleLength)
        return FullAnalyticsData(
            rangeMonths = rangeMonths,
            completedCycleCount = cycles.size,
            cycleLengths = cycles,
            bleedLengths = bleeds,
            meanCycle = meanCycle,
            minCycle = cycles.minOrNull(),
            maxCycle = cycles.maxOrNull(),
            meanBleed = meanBleed,
            symptomPainDays = painDays,
            loggedDaysInRange = logsInRange.size,
            sleepTrend = sleepTrend,
            waterTrend = waterTrend,
            emptyReasonFa = empty
        )
    }
}
