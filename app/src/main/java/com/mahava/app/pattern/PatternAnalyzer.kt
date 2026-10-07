package com.mahava.app.pattern

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

/** Symptom clustered in a coarse phase bucket across cycles. */
data class PhaseClusterInsight(
    val id: String,
    val phaseLabelFa: String,
    val symptomLabelFa: String,
    val textFa: String,
    val cyclesBasis: Int,
    val entriesBasis: Int
)

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

    /**
     * Across last cycles, which symptoms appear most in which coarse phase bucket
     * (پریود / بعد از پریود / نیمهٔ دوم / پیش از پریود).
     */
    fun analyzePhaseClusters(
        symptoms: List<SymptomLogPoint>,
        periodStarts: List<PeriodStartPoint>,
        typicalCycleLength: Int = 28
    ): List<PhaseClusterInsight> {
        if (periodStarts.size < MIN_CYCLES) return emptyList()
        val starts = periodStarts.map { it.epochDay }.sorted()
        val cyclesChecked = starts.size - 1
        if (cyclesChecked < MIN_CYCLES) return emptyList()

        data class Hit(val cycleIdx: Int, val phase: String, val key: String)
        val hits = mutableListOf<Hit>()

        for (i in 0 until starts.size) {
            val start = starts[i]
            val next = if (i + 1 < starts.size) starts[i + 1] else start + typicalCycleLength
            val len = (next - start).toInt().coerceIn(15, 90)
            val menstrualEnd = start + 4
            val follicularEnd = start + (len * 0.45).toLong()
            val lutealStart = start + (len * 0.55).toLong()
            val preStart = next - 5

            for (p in symptoms) {
                if (p.epochDay < start || p.epochDay >= next) continue
                val phase = when {
                    p.epochDay <= menstrualEnd -> "پریود"
                    p.epochDay < follicularEnd -> "بعد از پریود"
                    p.epochDay < lutealStart -> "حوالی تخمک‌گذاری"
                    p.epochDay >= preStart -> "پیش از پریود"
                    else -> "نیمهٔ دوم"
                }
                hits += Hit(i, phase, p.symptomKey)
            }
        }

        val out = mutableListOf<PhaseClusterInsight>()
        val grouped = hits.groupBy { it.phase to it.key }
        for ((pair, list) in grouped) {
            val (phase, key) = pair
            if (list.size < MIN_ENTRIES_PER_SYMPTOM) continue
            val cyclesWith = list.map { it.cycleIdx }.toSet().size
            if (cyclesWith < MIN_CYCLES) continue
            val label = symptomLabelFa(key)
            out += PhaseClusterInsight(
                id = "cluster_${phase}_$key",
                phaseLabelFa = phase,
                symptomLabelFa = label,
                textFa = "«$label» را در ${faNum(cyclesWith)} چرخه بیشتر در بازهٔ «$phase» ثبت کرده‌ای (از روی ${faNum(list.size)} ثبت).",
                cyclesBasis = cyclesWith,
                entriesBasis = list.size
            )
        }
        return out.sortedByDescending { it.cyclesBasis * 100 + it.entriesBasis }
    }

    fun symptomLabelFa(key: String): String = when (key) {
        "bloating" -> "نفخ"
        "headache" -> "سردرد"
        "breast_tenderness" -> "حساسیت سینه"
        "pain" -> "درد"
        "fatigue" -> "خستگی"
        "anxiety", "anxious" -> "نگرانی"
        "irritable" -> "زودرنجی"
        "nausea" -> "تهوع"
        "acne" -> "جوش"
        "happy" -> "خوشحالی"
        "calm" -> "آرامش"
        "sad" -> "غم"
        else -> if (key.startsWith("craving_")) {
            "هوس " + com.mahava.app.content.FoodCravingKeys.labelFa(key.removePrefix("craving_"))
        } else key
    }

    private fun faNum(n: Int): String {
        val p = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        return n.toString().map { c -> if (c.isDigit()) p[c - '0'] else c }.joinToString("")
    }
}
