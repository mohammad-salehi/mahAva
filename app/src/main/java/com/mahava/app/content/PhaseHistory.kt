package com.mahava.app.content

import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.SubWindowMath
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

data class PhaseHistorySummary(
    val linesFa: List<String>,
    val completedCycles: Int,
    val matchingDays: Int
)

/**
 * "در سابقه تو": maps the user's REAL past logs onto the same sub-window in past, completed cycles.
 * Past cycles use the real next start (not a prediction). Nothing is invented: no logs → we say so.
 */
object PhaseHistory {
    private val BLEED = setOf("light", "medium", "heavy")

    fun summarize(
        subWindow: CycleSubWindow,
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>
    ): PhaseHistorySummary {
        val byStart = periods.sortedBy { it.startEpochDay }.distinctBy { it.startEpochDay }
        data class Cycle(val start: LocalDate, val next: LocalDate, val end: LocalDate?)
        val cycles = byStart.zipWithNext().mapNotNull { (a, b) ->
            val len = b.startEpochDay - a.startEpochDay
            if (len !in 15..90) null
            else Cycle(LocalDate.ofEpochDay(a.startEpochDay), LocalDate.ofEpochDay(b.startEpochDay), a.endEpochDay?.let { LocalDate.ofEpochDay(it) })
        }
        if (cycles.isEmpty()) {
            return PhaseHistorySummary(
                listOf("هنوز چرخهٔ کاملی ثبت نشده. وقتی دست‌کم یک چرخه (از شروع یک پریود تا شروع پریود بعدی) ثبت شود، اینجا خلاصهٔ ثبت‌های خودت را در همین بخش از چرخه می‌بینی."),
                0, 0
            )
        }
        if (subWindow == CycleSubWindow.UNKNOWN || subWindow == CycleSubWindow.LATE_PERIOD) {
            return PhaseHistorySummary(
                listOf("برای این حالت، مقایسه با چرخه‌های گذشته معنی ندارد."), cycles.size, 0
            )
        }
        val matched = mutableListOf<Pair<Int, DailyLogEntity>>()
        cycles.forEachIndexed { idx, c ->
            val (ovE, ovL) = SubWindowMath.ovulationRange(c.next, 0, c.start)
            logs.filter { it.epochDay >= c.start.toEpochDay() && it.epochDay < c.next.toEpochDay() }.forEach { log ->
                val day = LocalDate.ofEpochDay(log.epochDay)
                val dayIndex = (log.epochDay - c.start.toEpochDay()).toInt() + 1
                val bleedingDay = when {
                    c.end != null && !day.isAfter(c.end) -> dayIndex
                    c.end == null && log.bleeding in BLEED && dayIndex <= 10 -> dayIndex
                    else -> null
                }
                val cls = SubWindowMath.classify(day, c.start, c.next, ovE, ovL, bleedingDay)
                if (cls.subWindow == subWindow) matched += idx to log
            }
        }
        if (matched.isEmpty()) {
            return PhaseHistorySummary(
                listOf("در ${fa(cycles.size)} چرخهٔ کاملی که ثبت کرده‌ای، برای همین بخش از چرخه ثبتی نداری."),
                cycles.size, 0
            )
        }
        val cyclesWith = matched.map { it.first }.distinct().size
        val counts = linkedMapOf<String, Int>()
        matched.forEach { (_, log) ->
            val keys = mutableSetOf<String>()
            log.physicalSymptoms?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.let { keys += it }
            if ((log.painScore ?: 0) > 0) keys += "pain"
            log.moods?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.let { keys += it.map { m -> "mood_$m" } }
            if (log.energy == "low") keys += "energy_low"
            keys.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        val lines = mutableListOf<String>()
        lines += "در ${fa(cyclesWith)} چرخه از ${fa(cycles.size)} چرخهٔ کامل گذشته، برای همین بخش از چرخه ${fa(matched.size)} روز ثبت داری."
        val top = counts.entries.sortedByDescending { it.value }.take(4)
        if (top.isNotEmpty()) {
            lines += "بیشتر این‌ها را ثبت کرده‌ای: " + top.joinToString("، ") { "${labelFa(it.key)} (${fa(it.value)} روز)" } + "."
        }
        val pains = matched.mapNotNull { it.second.painScore }
        if (pains.isNotEmpty()) {
            lines += "میانگین شدت درد ثبت‌شده: ${fa(Math.round(pains.average()).toInt())} از ۱۰."
        }
        val none = matched.count { it.second.noSymptoms }
        if (none > 0) lines += "در ${fa(none)} روز «هیچ علامتی ندارم» زده‌ای."
        lines += "این فقط خلاصهٔ ثبت‌های خودت است، نه پیش‌بینی برای امروز."
        return PhaseHistorySummary(lines, cycles.size, matched.size)
    }

    fun labelFa(key: String): String = when (key) {
        "pain" -> "درد"
        "bloating" -> "نفخ"
        "headache" -> "سردرد"
        "breast_tenderness" -> "حساسیت سینه"
        "nausea" -> "حالت تهوع"
        "acne" -> "جوش"
        "energy_low" -> "انرژی کم"
        "mood_happy" -> "حال خوب"
        "mood_calm" -> "آرامش"
        "mood_sad" -> "غم"
        "mood_anxious" -> "اضطراب"
        "mood_irritable" -> "زودرنجی"
        else -> key.removePrefix("mood_")
    }

    private fun fa(n: Int) = PersianDigits.toPersian(n)
}
