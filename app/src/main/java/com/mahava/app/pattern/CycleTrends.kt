package com.mahava.app.pattern

import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.SubWindowMath
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import java.time.LocalDate

data class CycleLengthPoint(val index: Int, val lengthDays: Int, val startEpochDay: Long)
data class BleedLengthPoint(val index: Int, val lengthDays: Int)
data class PhaseSymptomFreq(
    val phaseLabelFa: String,
    val symptomKey: String,
    val symptomLabelFa: String,
    val count: Int,
    val daysInPhase: Int
)

data class SymptomDayPercents(
    val totalLoggedDays: Int,
    val painPercent: Int?,
    val lowMoodPercent: Int?,
    val cravingPercent: Int?,
    val lowEnergyPercent: Int?
)

data class CycleTrendsResult(
    val cycleLengths: List<CycleLengthPoint>,
    val bleedLengths: List<BleedLengthPoint>,
    val medianCycle: Int?,
    val medianBleed: Int?,
    val phaseSymptomFreqs: List<PhaseSymptomFreq>,
    val dayPercents: SymptomDayPercents,
    val emptyReasonFa: String?
)

object CycleTrendsAnalyzer {
    private val NEGATIVE_MOODS = setOf("sad", "anxious", "irritable", "anxiety")

    fun analyze(
        periods: List<PeriodEventEntity>,
        logs: List<DailyLogEntity>,
        typicalCycleLength: Int? = 28
    ): CycleTrendsResult {
        val sorted = periods.sortedBy { it.startEpochDay }
        val cycleLengths = sorted.zipWithNext { a, b ->
            val len = (b.startEpochDay - a.startEpochDay).toInt()
            CycleLengthPoint(0, len, a.startEpochDay)
        }.filter { it.lengthDays in 15..90 }
            .mapIndexed { i, p -> p.copy(index = i + 1) }

        val bleedLengths = sorted.mapNotNull { p ->
            val end = p.endEpochDay ?: return@mapNotNull null
            val len = (end - p.startEpochDay + 1).toInt()
            if (len in 1..15) BleedLengthPoint(0, len) else null
        }.mapIndexed { i, p -> p.copy(index = i + 1) }

        val medianCycle = median(cycleLengths.map { it.lengthDays })
        val medianBleed = median(bleedLengths.map { it.lengthDays })

        val phaseFreqs = mutableListOf<PhaseSymptomFreq>()
        if (sorted.size >= 2 && logs.isNotEmpty()) {
            val estLen = medianCycle ?: typicalCycleLength ?: 28
            val phaseDayCounts = mutableMapOf<String, Int>()
            val phaseSymCounts = mutableMapOf<Pair<String, String>, Int>()

            for (log in logs) {
                val day = LocalDate.ofEpochDay(log.epochDay)
                val phaseLabel = estimatePhaseLabel(day, sorted, estLen) ?: continue
                phaseDayCounts[phaseLabel] = (phaseDayCounts[phaseLabel] ?: 0) + 1
                val keys = mutableListOf<String>()
                log.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.let { keys += it }
                if (log.painScore != null && log.painScore > 0) keys += "pain"
                log.foodCravings?.split(',')?.filter { it.isNotBlank() }?.forEach { keys += "craving_$it" }
                keys.distinct().forEach { k ->
                    val pair = phaseLabel to k
                    phaseSymCounts[pair] = (phaseSymCounts[pair] ?: 0) + 1
                }
            }
            phaseSymCounts.entries
                .sortedByDescending { it.value }
                .take(12)
                .forEach { (pair, count) ->
                    val (phase, key) = pair
                    val days = phaseDayCounts[phase] ?: 1
                    if (count >= 2) {
                        phaseFreqs += PhaseSymptomFreq(
                            phaseLabelFa = phase,
                            symptomKey = key,
                            symptomLabelFa = labelFa(key),
                            count = count,
                            daysInPhase = days
                        )
                    }
                }
        }

        val dayPercents = computeDayPercents(logs)

        val empty = when {
            sorted.isEmpty() -> "هنوز پریودی ثبت نشده."
            cycleLengths.isEmpty() && bleedLengths.isEmpty() && logs.isEmpty() ->
                "برای روند، دست‌کم دو پریود پشت‌سرهم یا چند ثبت روزانه لازم است."
            else -> null
        }

        return CycleTrendsResult(
            cycleLengths = cycleLengths,
            bleedLengths = bleedLengths,
            medianCycle = medianCycle,
            medianBleed = medianBleed,
            phaseSymptomFreqs = phaseFreqs,
            dayPercents = dayPercents,
            emptyReasonFa = empty
        )
    }

    private fun computeDayPercents(logs: List<DailyLogEntity>): SymptomDayPercents {
        if (logs.isEmpty()) {
            return SymptomDayPercents(0, null, null, null, null)
        }
        val n = logs.size
        fun pct(count: Int) = ((count * 100.0) / n).toInt()
        val pain = logs.count { (it.painScore ?: 0) >= 4 || (it.physicalSymptoms?.contains("pain") == true) }
        val lowMood = logs.count { log ->
            log.moods?.split(',')?.any { it.trim() in NEGATIVE_MOODS } == true
        }
        val craving = logs.count { !it.foodCravings.isNullOrBlank() }
        val lowEnergy = logs.count { it.energy == "low" || it.fatigue == "high" }
        return SymptomDayPercents(
            totalLoggedDays = n,
            painPercent = pct(pain),
            lowMoodPercent = pct(lowMood),
            cravingPercent = pct(craving),
            lowEnergyPercent = pct(lowEnergy)
        )
    }

    private fun estimatePhaseLabel(day: LocalDate, periods: List<PeriodEventEntity>, estLen: Int): String? {
        val starts = periods.map { it.startEpochDay }.sorted()
        val dayEp = day.toEpochDay()
        val startEp = starts.lastOrNull { it <= dayEp } ?: return null
        val start = LocalDate.ofEpochDay(startEp)
        val nextStart = starts.firstOrNull { it > startEp }?.let { LocalDate.ofEpochDay(it) }
            ?: start.plusDays(estLen.toLong())
        val period = periods.find { it.startEpochDay == startEp }
        val bleedingDay = if (period != null) {
            val end = period.endEpochDay ?: if (period.stillOngoing) dayEp else null
            if (end != null && dayEp in startEp..end) (dayEp - startEp + 1).toInt() else null
        } else null
        val half = 3
        val (ovE, ovL) = SubWindowMath.ovulationRange(nextStart, half, start)
        val classified = SubWindowMath.classify(day, start, nextStart, ovE, ovL, bleedingDay)
        return when (classified.subWindow) {
            CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE -> "پریود"
            CycleSubWindow.FOLLICULAR_EARLY, CycleSubWindow.FOLLICULAR_LATE -> "بعد از پریود"
            CycleSubWindow.PERI_OVULATORY -> "حوالی تخمک‌گذاری"
            CycleSubWindow.LUTEAL_EARLY, CycleSubWindow.LUTEAL_MID -> "نیمهٔ دوم"
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> "پیش از پریود"
            CycleSubWindow.LATE_PERIOD -> "تأخیر"
            else -> null
        }
    }

    private fun labelFa(key: String): String = when {
        key.startsWith("craving_") -> "هوس " + com.mahava.app.content.FoodCravingKeys.labelFa(key.removePrefix("craving_"))
        key == "bloating" -> "نفخ"
        key == "headache" -> "سردرد"
        key == "breast_tenderness" -> "حساسیت سینه"
        key == "pain" -> "درد"
        key == "nausea" -> "تهوع"
        key == "acne" -> "جوش"
        else -> key
    }

    private fun median(vals: List<Int>): Int? {
        if (vals.isEmpty()) return null
        val s = vals.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2 else s[m]
    }
}
