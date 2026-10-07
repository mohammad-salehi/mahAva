package com.mahava.app.pattern

import com.mahava.app.content.TodaySignalContent
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.util.PersianDigits

/**
 * "Based on your own pattern": what this user logged at the same point of her past cycles.
 * Computed on the device only. Needs at least [MIN_COMPLETE_CYCLES] complete cycles and the
 * item in at least [MIN_CYCLES_WITH] of them; otherwise we return null and the app shows the
 * general science text with an honest "not enough data yet" note.
 */
data class PersonalSignal(
    val kind: String,
    val key: String,
    val label: String,
    val cyclesChecked: Int,
    val cyclesWith: Int,
    /** Phase group where it showed up in most cycles, or null when timing was scattered. */
    val dominantPhase: String?,
    val dominantCycles: Int,
    val typicalDaysBefore: Int?,
    val typicalCycleDay: Int?,
    val textFa: String
)

data class PersonalCycleSummary(val medianCycle: Int, val medianBleed: Int?, val cycles: Int, val textFa: String)

object PersonalPatternEngine {
    const val MIN_COMPLETE_CYCLES = 2
    const val MIN_CYCLES_WITH = 2
    const val MAX_CYCLES = 6

    const val NOT_ENOUGH_FA =
        "هنوز دادهٔ کافی برای الگوی شخصی نداری. وقتی دست‌کم ۲ چرخهٔ کامل ثبت کنی، این‌جا می‌گوییم در چرخه‌های خودت چه دیده‌ایم. فعلاً توضیح علمی کلی را می‌بینی."

    data class Cycle(val start: Long, val next: Long, val bleedEnd: Long?)

    /** Complete cycles = two consecutive period starts. Most recent [MAX_CYCLES]. */
    fun cycles(periods: List<PeriodEventEntity>): List<Cycle> {
        val sorted = periods.sortedBy { it.startEpochDay }
        return sorted.zipWithNext { a, b -> Cycle(a.startEpochDay, b.startEpochDay, a.endEpochDay) }
            .filter { (it.next - it.start) in 15..90 }
            .takeLast(MAX_CYCLES)
    }

    fun completeCycleCount(periods: List<PeriodEventEntity>): Int = cycles(periods).size

    fun keysOf(log: DailyLogEntity, kind: String): Set<String> {
        fun csv(s: String?) = s?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet().orEmpty()
        return when (kind) {
            "food" -> csv(log.foodCravings)
            "body" -> csv(log.physicalSymptoms) + (if ((log.painScore ?: 0) > 0) setOf("pain") else emptySet())
            else -> csv(log.moods)
        }
    }

    /** Coarse phase of a past day, from that cycle's real start and the next real start. */
    fun phaseOf(day: Long, c: Cycle): String {
        val bleedEnd = (c.bleedEnd ?: (c.start + 4)).coerceIn(c.start, c.start + 9)
        val daysBefore = c.next - day
        val ovulation = c.next - 14
        return when {
            day <= bleedEnd -> "menstrual"
            daysBefore <= 5 -> "late_luteal"
            day in (ovulation - 5)..(ovulation + 1) -> "fertile"
            day < ovulation - 5 -> "follicular"
            else -> "early_luteal"
        }
    }

    fun forSignal(
        kind: String,
        key: String,
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>
    ): PersonalSignal? {
        val cs = cycles(periods)
        if (cs.size < MIN_COMPLETE_CYCLES) return null
        // cycle index -> days that had the item
        val hitsByCycle = cs.mapIndexedNotNull { idx, c ->
            val days = logs.filter { it.epochDay >= c.start && it.epochDay < c.next && key in keysOf(it, kind) }
                .map { it.epochDay }.sorted()
            if (days.isEmpty()) null else idx to days
        }
        val cyclesWith = hitsByCycle.size
        if (cyclesWith < MIN_CYCLES_WITH) return null

        val phaseCycles = mutableMapOf<String, MutableSet<Int>>()
        hitsByCycle.forEach { (idx, days) ->
            days.forEach { d -> phaseCycles.getOrPut(phaseOf(d, cs[idx])) { mutableSetOf() } += idx }
        }
        val best = phaseCycles.maxByOrNull { it.value.size }
        val dominant = best?.takeIf { it.value.size >= MIN_CYCLES_WITH }?.key
        val dominantCycles = best?.value?.size ?: 0

        var daysBefore: Int? = null
        var cycleDay: Int? = null
        if (dominant != null) {
            val firstInPhase = hitsByCycle.mapNotNull { (idx, days) ->
                days.firstOrNull { phaseOf(it, cs[idx]) == dominant }?.let { idx to it }
            }
            daysBefore = median(firstInPhase.map { (idx, d) -> (cs[idx].next - d).toInt() })
            cycleDay = median(firstInPhase.map { (idx, d) -> (d - cs[idx].start).toInt() + 1 })
        }
        val label = TodaySignalContent.label(kind, key)
        val n = cs.size
        val text = textFor(label, kind, n, cyclesWith, dominant, dominantCycles, daysBefore, cycleDay)
        return PersonalSignal(kind, key, label, n, cyclesWith, dominant, dominantCycles, daysBefore, cycleDay, text)
    }

    /** Items that usually show up in [phaseGroup] for this user, strongest first. */
    fun forPhase(
        phaseGroup: String,
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        limit: Int = 3
    ): List<PersonalSignal> {
        if (phaseGroup == "general" || cycles(periods).size < MIN_COMPLETE_CYCLES) return emptyList()
        val out = mutableListOf<PersonalSignal>()
        for (kind in listOf("body", "mood", "food")) {
            val keys = logs.flatMap { keysOf(it, kind) }.toSet()
            keys.forEach { k ->
                forSignal(kind, k, periods, logs)?.takeIf { it.dominantPhase == phaseGroup }?.let { out += it }
            }
        }
        return out.sortedWith(compareByDescending<PersonalSignal> { it.dominantCycles }.thenByDescending { it.cyclesWith })
            .take(limit)
    }

    fun cycleSummary(periods: List<PeriodEventEntity>): PersonalCycleSummary? {
        val cs = cycles(periods)
        if (cs.size < MIN_COMPLETE_CYCLES) return null
        val medCycle = median(cs.map { (it.next - it.start).toInt() }) ?: return null
        val bleeds = periods.mapNotNull { p -> p.endEpochDay?.let { (it - p.startEpochDay + 1).toInt() } }.filter { it in 1..15 }
        val medBleed = if (bleeds.size >= MIN_COMPLETE_CYCLES) median(bleeds) else null
        val base = "در ${fa(cs.size)} چرخهٔ اخیر، چرخه‌ات معمولاً حدود ${fa(medCycle)} روز طول کشیده"
        val text = if (medBleed != null) "$base و پریودت حدود ${fa(medBleed)} روز." else "$base."
        return PersonalCycleSummary(medCycle, medBleed, cs.size, text)
    }

    private fun textFor(
        label: String,
        kind: String,
        n: Int,
        cyclesWith: Int,
        dominant: String?,
        dominantCycles: Int,
        daysBefore: Int?,
        cycleDay: Int?
    ): String {
        val verb = if (kind == "food") "هوس «$label» را" else "«$label» را"
        if (dominant == null) {
            return "${inCycles(cyclesWith, n)} $verb ثبت کردی، ولی زمانش هر بار فرق داشت."
        }
        val lead = inCycles(dominantCycles, n)
        return when (dominant) {
            "late_luteal" -> {
                val d = daysBefore ?: 2
                val whenFa = if (d <= 1) "یک روز قبل از پریود" else "${fa(d)} روز قبل از پریود"
                "$lead معمولاً $whenFa $verb ثبت کردی."
            }
            "menstrual" -> {
                val d = cycleDay ?: 1
                "$lead $verb بیشتر روز ${fa(d)} پریود ثبت کردی."
            }
            else -> {
                val d = cycleDay ?: 1
                "$lead $verb بیشتر حوالی روز ${fa(d)} چرخه (${phaseNameFa(dominant)}) ثبت کردی."
            }
        }
    }

    private fun inCycles(with: Int, n: Int): String =
        if (with >= n) "در ${fa(n)} چرخهٔ اخیر،" else "در ${fa(with)} چرخه از ${fa(n)} چرخهٔ اخیر،"

    fun phaseNameFa(group: String): String = when (group) {
        "menstrual" -> "روزهای پریود"
        "follicular" -> "بعد از پریود"
        "fertile" -> "حوالی تخمک‌گذاری"
        "early_luteal" -> "بعد از تخمک‌گذاری"
        "late_luteal" -> "روزهای پیش از پریود"
        else -> "زمان نامعلوم"
    }

    private fun median(xs: List<Int>): Int? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else Math.round((s[s.size / 2 - 1] + s[s.size / 2]) / 2.0).toInt()
    }

    private fun fa(n: Int) = PersianDigits.toPersian(n.toString())
}
