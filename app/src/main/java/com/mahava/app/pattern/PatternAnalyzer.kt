package com.mahava.app.pattern

import java.time.LocalDate

/**
 * Personal pattern analysis over REAL user logs only.
 * Non-causal wording. Requires defined minimum data.
 */
data class PatternInsight(
    val id: String,
    val textFa: String,
    val cyclesBasis: Int,
    val entriesBasis: Int
)

data class SymptomLogPoint(
    val epochDay: Long,
    val symptomKey: String,
    val intensity: Int? = null
)

data class PeriodStartPoint(val epochDay: Long)

object PatternAnalyzer {
    const val MIN_CYCLES = 2
    const val MIN_ENTRIES_PER_SYMPTOM = 2

    fun analyze(
        symptoms: List<SymptomLogPoint>,
        periodStarts: List<PeriodStartPoint>
    ): List<PatternInsight> {
        if (periodStarts.size < MIN_CYCLES) return emptyList()
        val starts = periodStarts.map { it.epochDay }.sorted()
        val insights = mutableListOf<PatternInsight>()

        val bySymptom = symptoms.groupBy { it.symptomKey }
        for ((key, points) in bySymptom) {
            if (points.size < MIN_ENTRIES_PER_SYMPTOM) continue
            // Count how many cycles had this symptom in the 5 days before period start
            var cyclesWith = 0
            for (i in 1 until starts.size) {
                val start = starts[i]
                val window = (start - 5)..(start - 1)
                if (points.any { it.epochDay in window }) cyclesWith++
            }
            val cyclesChecked = starts.size - 1
            if (cyclesWith >= MIN_CYCLES) {
                val label = symptomLabelFa(key)
                insights += PatternInsight(
                    id = "preperiod_$key",
                    textFa = "در ${faNum(cyclesWith)} چرخه از ${faNum(cyclesChecked)} چرخه ثبت‌شده، پیش از پریود «$label» گزارش کرده‌ای.",
                    cyclesBasis = cyclesWith,
                    entriesBasis = points.size
                )
            }
        }
        return insights
    }

    private fun symptomLabelFa(key: String): String = when (key) {
        "bloating" -> "نفخ"
        "headache" -> "سردرد"
        "breast_tenderness" -> "حساسیت سینه"
        "pain" -> "درد"
        "fatigue" -> "خستگی"
        "anxiety" -> "اضطراب"
        "irritable" -> "تحریک‌پذیری"
        else -> key
    }

    private fun faNum(n: Int): String {
        val p = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        return n.toString().map { c -> if (c.isDigit()) p[c - '0'] else c }.joinToString("")
    }
}
