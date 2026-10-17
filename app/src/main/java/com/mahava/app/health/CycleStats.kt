package com.mahava.app.health

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity

/**
 * Plain statistics over her own records for charts, tables and the doctor report.
 * Only real period starts are used (never predictions); days without a log are "unknown",
 * never zero.
 */
object CycleStats {
    data class Cycle(val start: Long, val next: Long?, val bleedDays: Int?) {
        val length: Int? get() = next?.let { (it - start).toInt() }
    }

    /** Phase keys used for grouping, in cycle order. */
    val PHASES = listOf("menstrual", "follicular", "fertile", "luteal", "premenstrual")
    fun phaseTitleFa(key: String) = when (key) {
        "menstrual" -> "پریود"
        "follicular" -> "بعد از پریود"
        "fertile" -> "روزهای باروری"
        "luteal" -> "بعد از تخمک‌گذاری"
        "premenstrual" -> "پیش از پریود"
        else -> "نامعلوم"
    }

    fun cycles(periods: List<PeriodEventEntity>, today: Long): List<Cycle> {
        val sorted = periods.filter { it.startEpochDay <= today }.sortedBy { it.startEpochDay }
            .distinctBy { it.startEpochDay }
        return sorted.mapIndexed { i, p ->
            val next = sorted.getOrNull(i + 1)?.startEpochDay
            val bleed = p.endEpochDay?.let { (it - p.startEpochDay + 1).toInt() }?.takeIf { it in 1..15 }
            Cycle(p.startEpochDay, next?.takeIf { it - p.startEpochDay in 15..90 }, bleed)
        }
    }

    fun completeLengths(periods: List<PeriodEventEntity>, today: Long): List<Int> =
        cycles(periods, today).mapNotNull { it.length }

    fun typicalLength(periods: List<PeriodEventEntity>, today: Long, fallback: Int = 28): Int {
        val l = completeLengths(periods, today).takeLast(6).sorted()
        return if (l.isEmpty()) fallback else l[l.size / 2]
    }

    /** Phase of a past or current day from the real period that contains it. */
    fun phaseOf(day: Long, periods: List<PeriodEventEntity>, today: Long): String? {
        val cs = cycles(periods, today)
        val c = cs.lastOrNull { it.start <= day } ?: return null
        val len = c.length ?: typicalLength(periods, today)
        val d = (day - c.start).toInt() + 1
        if (c.next == null && d > 60) return null
        val bleed = c.bleedDays ?: 5
        val ov = len - 14
        return when {
            d <= bleed -> "menstrual"
            d > len - 5 -> "premenstrual"
            d in (ov - 5)..(ov + 1) -> "fertile"
            d < ov - 5 -> "follicular"
            else -> "luteal"
        }
    }

    fun sleepHours(log: DailyLogEntity): Float? =
        log.sleepMinutes?.let { it / 60f } ?: log.sleepHours

    /** Energy on a 1–5 scale; the older low/medium/high answer maps to 2/3/4. */
    fun energy(log: DailyLogEntity): Float? = log.energyScore?.takeIf { it in 1..5 }?.toFloat()
        ?: when (log.energy) { "low" -> 2f; "medium" -> 3f; "high" -> 4f; else -> null }

    private val GOOD = setOf("happy", "calm", "focused")
    private val LOW = setOf("sad", "anxious", "irritable", "sensitive", "bored", "unfocused")

    /** Mood on a 1–5 scale from the overall feeling, else from the logged moods. */
    fun mood(log: DailyLogEntity): Float? = when (log.overallFeeling) {
        "great" -> 5f; "good" -> 4f; "ordinary" -> 3f; "bad" -> 2f; "very_bad" -> 1f
        else -> {
            val m = log.moods.orEmpty().split(',').filter { it.isNotBlank() }
            when {
                m.isEmpty() -> null
                m.any { it in LOW } && m.any { it in GOOD } -> 3f
                m.any { it in LOW } -> 2f
                else -> 4f
            }
        }
    }

    fun pain(log: DailyLogEntity): Int? = log.painScore?.takeIf { it in 0..10 }

    /** Average of a metric per phase; only phases with at least [minDays] logged days. */
    fun byPhase(
        logs: List<DailyLogEntity>, periods: List<PeriodEventEntity>, today: Long,
        minDays: Int = 2, metric: (DailyLogEntity) -> Float?
    ): List<Triple<String, Float, Int>> {
        val groups = logs.mapNotNull { l -> metric(l)?.let { v -> phaseOf(l.epochDay, periods, today)?.let { it to v } } }
            .groupBy({ it.first }, { it.second })
        return PHASES.mapNotNull { p -> groups[p]?.takeIf { it.size >= minDays }?.let { Triple(p, it.average().toFloat(), it.size) } }
    }

    fun symptomCounts(logs: List<DailyLogEntity>): List<Pair<String, Int>> =
        logs.flatMap { it.physicalSymptoms.orEmpty().split(',').filter { s -> s.isNotBlank() }.distinct() }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    fun moodCounts(logs: List<DailyLogEntity>): List<Pair<String, Int>> =
        logs.flatMap { it.moods.orEmpty().split(',').filter { s -> s.isNotBlank() }.distinct() }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** One value per day in [from, to]; null when that day has no value. */
    fun daily(logs: List<DailyLogEntity>, from: Long, to: Long, metric: (DailyLogEntity) -> Float?): List<Float?> {
        val by = logs.associateBy { it.epochDay }
        return (from..to).map { d -> by[d]?.let(metric) }
    }
}

object LogLabels {
    fun symptomFa(k: String): String = when (k) {
        "pain" -> "درد"; "abdomen_pain" -> "درد شکم"; "back_pain" -> "درد کمر"; "breast_pain" -> "درد سینه"
        "breast_tenderness" -> "حساسیت سینه"; "headache" -> "سردرد"; "bloating" -> "نفخ"; "acne" -> "جوش"
        "nausea" -> "تهوع"; "cramps" -> "گرفتگی"; "heaviness" -> "سنگینی"; "fatigue" -> "خستگی"
        "severe_headache" -> "سردرد شدید"; "vision_changes" -> "تغییر دید"; "reduced_movement" -> "کاهش حرکت جنین"
        "fluid_leak" -> "خروج مایع"; "fainting" -> "غش"; "other" -> "سایر"
        else -> k
    }
    fun moodFa(k: String): String = when (k) {
        "happy" -> "خوشحال"; "calm" -> "آرام"; "anxious" -> "نگران"; "sensitive" -> "حساس"; "irritable" -> "زودرنج"
        "sad" -> "غمگین"; "bored" -> "بی‌حوصله"; "focused" -> "تمرکز خوب"; "unfocused" -> "تمرکز کم"
        else -> k
    }
    fun painLocationFa(k: String): String = when (k) {
        "lower_abdomen" -> "زیر شکم"; "back" -> "کمر"; "pelvis" -> "لگن"; "head" -> "سر"; "breast" -> "سینه"
        "legs" -> "پاها"; "other" -> "جای دیگر"; else -> k
    }
    val FEELINGS = listOf("great" to "عالی", "good" to "خوب", "ordinary" to "معمولی", "bad" to "بد", "very_bad" to "خیلی بد")
    val MOODS = listOf("calm", "happy", "anxious", "sensitive", "irritable", "sad", "bored", "focused", "unfocused").map { it to moodFa(it) }
    val PAIN_LOCATIONS = listOf("lower_abdomen", "back", "pelvis", "head", "breast", "legs", "other").map { it to painLocationFa(it) }
    val PAIN_TYPES = listOf("cramping" to "گرفتگی", "dull" to "مبهم و ممتد", "sharp" to "تیز", "burning" to "سوزشی", "pressure" to "فشاری")
    val PAIN_DURATIONS = listOf("30" to "کمتر از ۱ ساعت", "120" to "۱ تا ۳ ساعت", "300" to "۳ تا ۶ ساعت", "720" to "بیشتر روز")
    val PAIN_ACTIONS = listOf("rest" to "استراحت", "heat" to "کیسهٔ گرم", "painkiller" to "مسکن", "walk" to "پیاده‌روی", "nothing" to "کاری نکردم")
    val PAIN_RESULTS = listOf("better" to "بهتر شد", "same" to "فرقی نکرد", "worse" to "بدتر شد")
    val DISCHARGE = listOf("none" to "ندارم", "dry" to "خشک", "sticky" to "چسبناک", "creamy" to "کرمی", "watery" to "آبکی", "eggwhite" to "شفاف و کش‌دار", "different" to "متفاوت از معمول")
    val DISCHARGE_CONCERNS = listOf("odor" to "بو", "itching" to "خارش", "burning" to "سوزش", "pain" to "درد")
    fun labelOf(list: List<Pair<String, String>>, k: String?): String {
        val key = if (k == "stretchy") "eggwhite" else k
        return list.firstOrNull { it.first == key }?.second ?: k.orEmpty()
    }
}
