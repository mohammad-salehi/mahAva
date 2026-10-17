package com.mahava.app.health

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ToolRecord
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Report ranges and chart buckets. Every chart and report starts, by default, at her FIRST
 * entry (earliest period, check-in or tool record she made) and runs to today. Long spans are
 * grouped by week or by (Jalali) month so a chart stays readable.
 */
object TimeRange {
    /** Range choices: 0 = from the first entry (default), N = last N months, -1 = custom. */
    const val ALL = 0
    const val CUSTOM = -1

    /** Records the app makes by itself; they are not "her entries". */
    private val GENERATED = setOf("insight", "recommendation")

    fun firstEntryDay(
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        records: List<ToolRecord> = emptyList(),
        today: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val days = mutableListOf<Long>()
        periods.minOfOrNull { it.startEpochDay }?.let { days += it }
        logs.minOfOrNull { it.epochDay }?.let { days += it }
        records.asSequence().filter { it.deletedAt == null && it.type !in GENERATED }
            .map { r -> r.epochDay ?: Instant.ofEpochMilli(r.createdAt).atZone(zone).toLocalDate().toEpochDay() }
            .minOrNull()?.let { days += it }
        return days.filter { it <= today }.minOrNull()
    }

    /** Start day for a range choice; "from the first entry" falls back to today with no data. */
    fun startFor(choice: Int, today: LocalDate, first: Long?, customFrom: Long): Long = when {
        choice == ALL -> first ?: today.toEpochDay()
        choice == CUSTOM -> customFrom
        else -> today.minusMonths(choice.toLong()).toEpochDay()
    }

    enum class Unit { DAY, WEEK, MONTH }

    data class Bucket(val start: Long, val end: Long, val label: String)

    data class Plan(val unit: Unit, val monthsPerBucket: Int, val buckets: List<Bucket>) {
        val size get() = buckets.size
        /** "روزانه" / "هفتگی" / "ماهانه" / "هر ۳ ماه". */
        val unitFa: String get() = when (unit) {
            Unit.DAY -> "روزانه"
            Unit.WEEK -> "هفتگی"
            Unit.MONTH -> if (monthsPerBucket == 1) "ماهانه" else "هر ${PersianDigits.toPersian(monthsPerBucket)} ماه"
        }
        val labels get() = buckets.map { it.label }
    }

    /**
     * Buckets covering [from, to]. Up to a month: days; up to ~6 months: weeks; longer: Jalali
     * months. With [maxPoints] (small home charts) several months are merged into one bucket.
     */
    fun plan(from: Long, to: Long, maxPoints: Int = Int.MAX_VALUE): Plan {
        val a = minOf(from, to); val b = maxOf(from, to)
        val span = (b - a + 1).toInt()
        if (span <= 31 && span <= maxPoints) {
            return Plan(Unit.DAY, 0, (a..b).map { d -> Bucket(d, d, PersianDigits.toPersian(JalaliDate.from(LocalDate.ofEpochDay(d)).day)) })
        }
        val weeks = (span + 6) / 7
        if (span <= 186 && weeks <= maxPoints) {
            // Weeks end on "to", so the newest bucket is always a full week ending today.
            val list = (0 until weeks).map { w ->
                val end = b - (weeks - 1 - w) * 7L
                val start = maxOf(a, end - 6)
                Bucket(start, end, JalaliDate.from(LocalDate.ofEpochDay(start)).formatShortFa())
            }
            return Plan(Unit.WEEK, 0, list)
        }
        return monthPlan(a, b, maxPoints)
    }

    /** Jalali-month buckets for [from, to]; with [maxPoints] several months share one bucket. */
    fun monthPlan(from: Long, to: Long, maxPoints: Int = Int.MAX_VALUE): Plan {
        val a = minOf(from, to); val b = maxOf(from, to)
        val first = JalaliDate.from(LocalDate.ofEpochDay(a))
        val last = JalaliDate.from(LocalDate.ofEpochDay(b))
        val months = (last.year - first.year) * 12 + (last.month - first.month) + 1
        val per = if (maxPoints == Int.MAX_VALUE) 1 else ((months + maxPoints - 1) / maxPoints).coerceAtLeast(1)
        val list = mutableListOf<Bucket>()
        var y = first.year; var m = first.month
        var lastYearShown = -1
        while (true) {
            val startDay = JalaliDate(y, m, 1).toLocalDate().toEpochDay()
            if (startDay > b) break
            var ny = y; var nm = m + per
            while (nm > 12) { nm -= 12; ny++ }
            val nextStart = JalaliDate(ny, nm, 1).toLocalDate().toEpochDay()
            val label = JalaliDate.monthNameFa(m) + if (y != lastYearShown) " " + PersianDigits.toPersian(y % 100).padStart(2, '۰') else ""
            lastYearShown = y
            list += Bucket(maxOf(a, startDay), minOf(b, nextStart - 1), label)
            y = ny; m = nm
        }
        return Plan(Unit.MONTH, per, list)
    }

    /** Average of a metric per bucket; null when nothing was recorded in that bucket. */
    fun average(logs: List<DailyLogEntity>, plan: Plan, metric: (DailyLogEntity) -> Float?): List<Float?> {
        val sorted = logs.sortedBy { it.epochDay }
        return plan.buckets.map { bk ->
            sorted.asSequence().filter { it.epochDay in bk.start..bk.end }.mapNotNull(metric).toList()
                .takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }
    }

    /** Number of days per bucket matching [predicate]; null when the bucket has no log at all. */
    fun countDays(logs: List<DailyLogEntity>, plan: Plan, predicate: (DailyLogEntity) -> Boolean): List<Float?> =
        plan.buckets.map { bk ->
            val inB = logs.filter { it.epochDay in bk.start..bk.end }
            if (inB.isEmpty()) null else inB.count(predicate).toFloat()
        }

    /** Shrinks a long list (e.g. every cycle length) to at most [max] points by averaging neighbours. */
    fun compress(values: List<Float>, max: Int): List<Float> {
        if (values.size <= max || max <= 0) return values
        val chunk = (values.size + max - 1) / max
        // Keep the newest values intact at the end; group from the oldest side.
        return values.reversed().chunked(chunk).map { it.average().toFloat() }.reversed()
    }
}
