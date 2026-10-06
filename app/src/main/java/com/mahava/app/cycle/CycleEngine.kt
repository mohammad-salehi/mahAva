package com.mahava.app.cycle

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic cycle engine (algorithmVersion = mahava-cycle-1.1.0).
 *
 * Ovulation / fertility basis (documented, not a clinical guarantee):
 * - NHS: ovulation usually happens 10–16 days before the next period; sperm can live up to ~7 days.
 * - OWH: most fertile from 3 days before ovulation through ovulation day; egg lives 12–24 h.
 * - Wilcox 1995/2000: conceptions came from a ~6-day window ending on ovulation day; the window
 *   moves from cycle to cycle even in regular cycles.
 * We therefore anchor ovulation BACKWARD from the predicted next period:
 *   ovCentral = C − 14, ovulation range = [C − 16 − extra, C − 12 + extra],
 *   extra = max(0, h − 3) where h is the half-width of the next-period range;
 *   fertility = [ovEarliest − 3, ovLatest] (clamped to the current cycle).
 * We NEVER claim a fixed day-14 ovulation, never emit pregnancy probability %, and never
 * materialize predictions as real period events.
 */
class CycleEngine {

    data class Input(
        val today: LocalDate,
        val periods: List<PeriodInterval>,
        val typicalCycleLength: Int?, // user-confirmed; null = unknown
        val typicalBleedLength: Int?,
        val regularCycles: Boolean?,
        val hormonalContraception: Boolean = false,
        val postpartumOrBreastfeeding: Boolean = false,
        val perimenopause: Boolean = false,
        val pregnancyMode: Boolean = false,
        val fertilityTrackingEnabled: Boolean = false,
        val maxCyclesForMedian: Int = 6
    )

    fun compute(input: Input): CycleEngineResult {
        val confirmed = input.periods
            .filter { it.confirmedByUser }
            .sortedBy { it.start }

        val lastStart = confirmed.lastOrNull()?.start
        val ongoing = confirmed.lastOrNull()?.let { it.isOngoing() && !it.start.isAfter(input.today) } == true

        val restriction = when {
            input.pregnancyMode -> PredictionRestriction.PREGNANCY_MODE
            input.hormonalContraception -> PredictionRestriction.HORMONAL_CONTRACEPTION
            input.postpartumOrBreastfeeding -> PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING
            input.perimenopause -> PredictionRestriction.PERIMENOPAUSE
            input.regularCycles == false -> PredictionRestriction.IRREGULAR
            else -> PredictionRestriction.NONE
        }

        val cycleDay = lastStart?.let {
            val d = (input.today.toEpochDay() - it.toEpochDay() + 1).toInt()
            if (d < 1) null else d
        }

        val lastP = confirmed.lastOrNull()
        val inLoggedBleed = lastP?.endInclusive?.let { e -> !input.today.isBefore(lastP.start) && !input.today.isAfter(e) } == true
        val bleedingDay = if ((ongoing || inLoggedBleed) && lastStart != null) {
            (input.today.toEpochDay() - lastStart.toEpochDay() + 1).toInt().takeIf { it >= 1 }
        } else null

        val samples = cycleSamples(confirmed)
        val predictionAllowed = restriction == PredictionRestriction.NONE ||
            restriction == PredictionRestriction.IRREGULAR // irregular -> unknown estimate below

        val pred: PredictionKind
        val effectiveRestriction: PredictionRestriction
        val basis: String
        val limits: String
        val reason: String

        when {
            input.pregnancyMode -> {
                pred = PredictionKind.Unknown
                effectiveRestriction = PredictionRestriction.PREGNANCY_MODE
                basis = "حالت بارداری روشن است."
                limits = "در این حالت، پیش‌بینی پریود و روزهای باروری انجام نمی‌شود."
                reason = "pregnancy_mode"
            }
            !predictionAllowed || restriction in setOf(
                PredictionRestriction.HORMONAL_CONTRACEPTION,
                PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING,
                PredictionRestriction.PERIMENOPAUSE
            ) -> {
                pred = PredictionKind.Unknown
                effectiveRestriction = restriction
                basis = restrictionBasisFa(restriction)
                limits = "می‌توانی مثل همیشه ثبت کنی و مطالب آموزشی را بخوانی؛ فقط تاریخ‌ها را حدس نمی‌زنیم."
                reason = restriction.name
            }
            restriction == PredictionRestriction.IRREGULAR -> {
                pred = PredictionKind.Unknown
                effectiveRestriction = PredictionRestriction.IRREGULAR
                basis = "چون چرخه‌ات را نامنظم ثبت کرده‌ای، تاریخ پریود بعدی را حدس نمی‌زنیم."
                limits = "در چرخهٔ نامنظم، تاریخ پریود و روزهای باروری قابل پیش‌بینی نیست."
                reason = "irregular"
            }
            samples.size >= 2 -> {
                val used = samples.takeLast(input.maxCyclesForMedian)
                val median = medianInt(used.map { it.lengthDays })
                val variability = max(2, (mad(used.map { it.lengthDays }) * 1.5).roundToInt().coerceAtLeast(2))
                // Extra uncertainty when few cycles
                val dataPad = when (used.size) {
                    2 -> 2
                    3 -> 1
                    else -> 0
                }
                val half = variability + dataPad
                val anchor = lastStart!!
                val central = anchor.plusDays(median.toLong())
                val earliest = central.minusDays(half.toLong())
                val latest = central.plusDays(half.toLong())
                val (ovEarliest, ovLatest) = SubWindowMath.ovulationRange(central, half, anchor)
                val fertEarliest = maxDate(ovEarliest.minusDays(3), anchor)
                val fertLatest = ovLatest
                pred = PredictionKind.Estimate(
                    medianCycleLength = median,
                    cyclesUsed = used.size,
                    nextPeriodStartEarliest = earliest,
                    nextPeriodStartLatest = latest,
                    nextPeriodStartCentral = central,
                    ovulationEarliest = minDate(ovEarliest, ovLatest),
                    ovulationLatest = maxDate(ovEarliest, ovLatest),
                    fertilityEarliest = minDate(fertEarliest, fertLatest),
                    fertilityLatest = maxDate(fertEarliest, fertLatest)
                )
                effectiveRestriction = PredictionRestriction.NONE
                basis = "بر اساس ${fa(used.size)} چرخه‌ای که ثبت کرده‌ای؛ چرخه‌هایت معمولاً حدود ${fa(median)} روز است."
                limits = "این فقط یک تخمین است و ممکن است چند روز زودتر یا دیرتر باشد."
                reason = "median_recent_cycles"
            }
            input.typicalCycleLength != null && lastStart != null -> {
                val L = input.typicalCycleLength
                val half = 3 // minimum uncertainty for single user-stated length
                val central = lastStart.plusDays(L.toLong())
                val earliest = central.minusDays(half.toLong())
                val latest = central.plusDays(half.toLong())
                val (ovEarliest, ovLatest) = SubWindowMath.ovulationRange(central, half, lastStart)
                val fertEarliest = maxDate(ovEarliest.minusDays(3), lastStart)
                val fertLatest = ovLatest
                pred = PredictionKind.Estimate(
                    medianCycleLength = L,
                    cyclesUsed = 0,
                    nextPeriodStartEarliest = earliest,
                    nextPeriodStartLatest = latest,
                    nextPeriodStartCentral = central,
                    ovulationEarliest = minDate(ovEarliest, ovLatest),
                    ovulationLatest = maxDate(ovEarliest, ovLatest),
                    fertilityEarliest = minDate(fertEarliest, fertLatest),
                    fertilityLatest = maxDate(fertEarliest, fertLatest)
                )
                effectiveRestriction = PredictionRestriction.NONE
                basis = "بر اساس طول چرخه‌ای که خودت وارد کرده‌ای (حدود ${fa(L)} روز)."
                limits = "هنوز چرخهٔ کاملی ثبت نشده؛ تاریخ ممکن است تا ${fa(half)} روز زودتر یا دیرتر باشد."
                reason = "user_stated_cycle_length"
            }
            else -> {
                pred = PredictionKind.Unknown
                effectiveRestriction = PredictionRestriction.INSUFFICIENT_DATA
                basis = "برای تخمین تاریخ‌ها، تاریخ آخرین پریود و طول چرخه یا دست‌کم ۲ چرخهٔ ثبت‌شده لازم است."
                limits = "تا آن موقع می‌توانی ثبت کنی و مطالب آموزشی را بخوانی."
                reason = "insufficient_data"
            }
        }

        val daysUntil: Int?
        val late: Boolean
        val daysLate: Int?
        when (val p = pred) {
            is PredictionKind.Estimate -> {
                val delta = (p.nextPeriodStartCentral.toEpochDay() - input.today.toEpochDay()).toInt()
                if (delta < 0) {
                    daysUntil = null
                    late = true
                    daysLate = -delta
                } else {
                    daysUntil = delta
                    late = false
                    daysLate = null
                }
            }
            else -> {
                daysUntil = null
                late = false
                daysLate = null
            }
        }

        val phase = inferPhase(
            cycleDay = cycleDay,
            today = input.today,
            ongoingPeriod = ongoing,
            typicalBleed = input.typicalBleedLength,
            lastPeriod = confirmed.lastOrNull(),
            prediction = pred
        )

        val showFertility = input.fertilityTrackingEnabled &&
            pred is PredictionKind.Estimate &&
            effectiveRestriction == PredictionRestriction.NONE

        return CycleEngineResult(
            algorithmVersion = CycleEngineResult.ALGORITHM_VERSION,
            today = input.today,
            cycleDay = cycleDay,
            phase = phase,
            lastPeriodStart = lastStart,
            bleedingDay = bleedingDay,
            periodOngoing = ongoing,
            daysUntilCentralPeriod = daysUntil,
            isLate = late,
            daysLate = daysLate,
            prediction = pred,
            restriction = effectiveRestriction,
            basisDescriptionFa = basis,
            limitsDescriptionFa = limits,
            reasonFa = reason,
            showFertility = showFertility
        )
    }

    fun cycleSamples(periods: List<PeriodInterval>): List<CycleLengthSample> {
        val starts = periods.map { it.start }.distinct().sorted()
        if (starts.size < 2) return emptyList()
        val out = mutableListOf<CycleLengthSample>()
        for (i in 0 until starts.lastIndex) {
            val a = starts[i]
            val b = starts[i + 1]
            val len = (b.toEpochDay() - a.toEpochDay()).toInt()
            if (len in 15..90) { // keep extreme but plausible; do not silently drop without reason in UI
                out += CycleLengthSample(a, b, len)
            }
        }
        return out
    }

    private fun inferPhase(
        cycleDay: Int?,
        today: LocalDate,
        ongoingPeriod: Boolean,
        typicalBleed: Int?,
        lastPeriod: PeriodInterval?,
        prediction: PredictionKind
    ): CyclePhase {
        if (cycleDay == null) return CyclePhase.UNKNOWN
        if (ongoingPeriod) return CyclePhase.MENSTRUATION
        if (lastPeriod?.endInclusive != null) {
            val end = lastPeriod.endInclusive
            if (!today.isAfter(end) && !today.isBefore(lastPeriod.start)) return CyclePhase.MENSTRUATION
        } else if (typicalBleed != null && cycleDay <= typicalBleed) {
            // only soft hint when period ended unknown — still prefer UNKNOWN for bleed if not ongoing
        }
        when (prediction) {
            is PredictionKind.Estimate -> {
                if (!today.isBefore(prediction.ovulationEarliest) && !today.isAfter(prediction.ovulationLatest)) {
                    return CyclePhase.OVULATION_WINDOW
                }
                val central = prediction.nextPeriodStartCentral
                val lutealStartApprox = prediction.ovulationLatest.plusDays(1)
                if (!today.isBefore(lutealStartApprox) && today.isBefore(central)) return CyclePhase.LUTEAL
                if (cycleDay >= 1 && today.isBefore(prediction.ovulationEarliest)) {
                    // after bleed
                    return if (ongoingPeriod) CyclePhase.MENSTRUATION else CyclePhase.FOLLICULAR
                }
            }
            else -> Unit
        }
        return CyclePhase.UNKNOWN
    }

    private fun fa(n: Int) = com.mahava.app.util.PersianDigits.toPersian(n)

    private fun restrictionBasisFa(r: PredictionRestriction): String = when (r) {
        PredictionRestriction.HORMONAL_CONTRACEPTION -> "چون روش هورمونی جلوگیری ثبت کرده‌ای، تاریخ پریود و روزهای باروری را حدس نمی‌زنیم."
        PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING -> "بعد از زایمان یا در دوران شیردهی، زمان برگشت پریود قابل پیش‌بینی نیست."
        PredictionRestriction.PERIMENOPAUSE -> "نزدیک یائسگی، چرخه‌ها قابل پیش‌بینی نیستند؛ تاریخ‌ها را حدس نمی‌زنیم."
        else -> "شرایطی که ثبت کرده‌ای برای پیش‌بینی تاریخ‌ها مناسب نیست."
    }

    private fun medianInt(values: List<Int>): Int {
        require(values.isNotEmpty())
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else ((s[m - 1] + s[m]) / 2.0).roundToInt()
    }

    private fun mad(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val med = medianInt(values).toDouble()
        val deviations = values.map { kotlin.math.abs(it - med) }.sorted()
        val m = deviations.size / 2
        return if (deviations.size % 2 == 1) deviations[m] else (deviations[m - 1] + deviations[m]) / 2.0
    }

    private fun minDate(a: LocalDate, b: LocalDate) = if (a.isBefore(b)) a else b
    private fun maxDate(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) a else b
}
