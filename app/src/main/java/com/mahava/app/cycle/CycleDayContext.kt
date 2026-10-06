package com.mahava.app.cycle

import java.time.LocalDate
import kotlin.math.max

/**
 * Finer educational windows scaled to the user's estimated cycle.
 * Ovulation / luteal timing is anchored backward from the next expected period
 * (NHS: ovulation usually 10–16 days before the next period; Bull 2019: mean luteal 12.4 d,
 * follicular length drives most cycle-length variation). Never a fixed day 14.
 */
enum class CycleSubWindow(val id: String, val titleFa: String) {
    MENSTRUATION_EARLY("menstruation_early", "روزهای اول پریود"),
    MENSTRUATION_LATE("menstruation_late", "روزهای آخر پریود"),
    FOLLICULAR_EARLY("follicular_early", "بعد از پریود (تخمینی)"),
    FOLLICULAR_LATE("follicular_late", "نزدیک شدن به تخمک‌گذاری (تخمینی)"),
    PERI_OVULATORY("peri_ovulatory", "حوالی تخمک‌گذاری (تخمینی)"),
    LUTEAL_EARLY("luteal_early", "بعد از تخمک‌گذاری (تخمینی)"),
    LUTEAL_MID("luteal_mid", "میانهٔ نیمهٔ دوم چرخه (تخمینی)"),
    LUTEAL_LATE_PREMENSTRUAL("luteal_late_premenstrual", "روزهای پیش از پریود (تخمینی)"),
    LATE_PERIOD("late_period", "پریود دیر کرده"),
    UNKNOWN("unknown", "مرحلهٔ امروز معلوم نیست");

    companion object {
        fun fromId(id: String?): CycleSubWindow? = entries.find { it.id == id }
    }
}

data class CycleDayContext(
    val subWindow: CycleSubWindow,
    val cycleDay: Int?,
    val estimatedCycleLength: Int?,
    val daysUntilCentralPeriod: Int?,
    val daysRelativeToOvulationCentral: Int?,
    val uncertaintyNoteFa: String,
    /** True on the first/last day of an estimated window: the user may still be in the neighbouring one. */
    val nearBoundary: Boolean = false,
    /** True when sub-window comes from logged bleeding rather than from date estimates. */
    val fromLoggedBleeding: Boolean = false
)

/** Pure date math shared by the live resolver and the retrospective history mapping. */
object SubWindowMath {
    /** Ovulation range anchored to the next period [central]; widened when the period estimate is wider than ±3 days. */
    fun ovulationRange(central: LocalDate, halfWidth: Int, cycleStart: LocalDate? = null): Pair<LocalDate, LocalDate> {
        val extra = max(0, halfWidth - 3).toLong()
        var early = central.minusDays(16 + extra)
        var late = central.minusDays(12 - extra)
        if (cycleStart != null) {
            // Never place ovulation inside the first 2 days of the cycle (very short cycles).
            val floor = cycleStart.plusDays(2)
            if (early.isBefore(floor)) early = floor
            if (late.isBefore(early)) late = early
        }
        return early to late
    }

    data class Classified(val subWindow: CycleSubWindow, val nearBoundary: Boolean)

    /**
     * Classify [day] inside a cycle. [bleedingDay] is the 1-based day of logged bleeding (null when not bleeding).
     * [central] is the (predicted or actual) next period start.
     */
    fun classify(
        day: LocalDate,
        cycleStart: LocalDate,
        central: LocalDate,
        ovEarliest: LocalDate,
        ovLatest: LocalDate,
        bleedingDay: Int?
    ): Classified {
        if (bleedingDay != null) {
            return if (bleedingDay <= 2) Classified(CycleSubWindow.MENSTRUATION_EARLY, bleedingDay == 2)
            else Classified(CycleSubWindow.MENSTRUATION_LATE, bleedingDay == 3)
        }
        if (!day.isBefore(central)) return Classified(CycleSubWindow.LATE_PERIOD, false)
        if (day.isBefore(ovEarliest)) {
            val span = (ovEarliest.toEpochDay() - cycleStart.toEpochDay()).coerceAtLeast(2)
            val mid = cycleStart.plusDays(span / 2)
            return if (day.isBefore(mid)) Classified(CycleSubWindow.FOLLICULAR_EARLY, day == mid.minusDays(1))
            else Classified(CycleSubWindow.FOLLICULAR_LATE, day == mid || day == ovEarliest.minusDays(1))
        }
        if (!day.isAfter(ovLatest)) {
            return Classified(CycleSubWindow.PERI_OVULATORY, day == ovEarliest || day == ovLatest)
        }
        val dUntil = (central.toEpochDay() - day.toEpochDay()).toInt()
        return when {
            dUntil >= 10 -> Classified(CycleSubWindow.LUTEAL_EARLY, day == ovLatest.plusDays(1) || dUntil == 10)
            dUntil >= 6 -> Classified(CycleSubWindow.LUTEAL_MID, dUntil == 9 || dUntil == 6)
            else -> Classified(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, dUntil == 5)
        }
    }
}

object CycleDayContextResolver {
    private const val NOTE_ESTIMATE =
        "این مرحله از روی تاریخ‌ها تخمین زده شده و ممکن است چند روز جابه‌جا باشد. روز ۱۴ را برای همه فرض نمی‌کنیم؛ زمان تخمک‌گذاری از روی تاریخ پریود بعدی‌ات حساب شده."
    private const val NOTE_NEAR =
        "امروز نزدیک مرز دو مرحله است؛ ممکن است هنوز در مرحلهٔ قبلی یا وارد مرحلهٔ بعدی شده باشی."
    private const val NOTE_BLEED = "این بخش بر اساس خون‌ریزی‌ای است که خودت ثبت کرده‌ای."

    fun resolve(result: CycleEngineResult): CycleDayContext {
        val pred = result.prediction
        val length = (pred as? PredictionKind.Estimate)?.medianCycleLength
        val restricted = CycleContentPolicy.forceGeneral(result.restriction)

        // Logged bleeding is real data: show period content even when dates can't be estimated,
        // except when bleeding pattern is driven by hormones or pregnancy.
        val bleedDay = result.bleedingDay
        if (bleedDay != null && (!restricted || CycleContentPolicy.bleedingContentAllowed(result.restriction))) {
            val c = SubWindowMath.classify(result.today, result.lastPeriodStart ?: result.today, result.today.plusDays(1), result.today, result.today, bleedDay)
            return CycleDayContext(c.subWindow, result.cycleDay, length, result.daysUntilCentralPeriod, null,
                NOTE_BLEED, nearBoundary = false, fromLoggedBleeding = true)
        }
        if (restricted) {
            return CycleDayContext(CycleSubWindow.UNKNOWN, result.cycleDay, length, result.daysUntilCentralPeriod, null, "")
        }
        if (result.isLate) {
            return CycleDayContext(CycleSubWindow.LATE_PERIOD, result.cycleDay, length, null, null,
                "پریود از تاریخ تخمینی گذشته است. چند روز جابه‌جایی رایج است.")
        }
        if (pred !is PredictionKind.Estimate || result.lastPeriodStart == null) {
            return CycleDayContext(CycleSubWindow.UNKNOWN, result.cycleDay, length, result.daysUntilCentralPeriod, null, "")
        }
        val today = result.today
        val ovCentral = pred.ovulationEarliest.plusDays(
            (pred.ovulationLatest.toEpochDay() - pred.ovulationEarliest.toEpochDay()) / 2
        )
        val relOv = (today.toEpochDay() - ovCentral.toEpochDay()).toInt()
        val c = SubWindowMath.classify(
            today, result.lastPeriodStart, pred.nextPeriodStartCentral,
            pred.ovulationEarliest, pred.ovulationLatest, null
        )
        val note = if (c.nearBoundary) "$NOTE_NEAR $NOTE_ESTIMATE" else NOTE_ESTIMATE
        return CycleDayContext(c.subWindow, result.cycleDay, length, result.daysUntilCentralPeriod, relOv, note, c.nearBoundary)
    }
}

object CycleContentPolicy {
    fun forceGeneral(restriction: PredictionRestriction): Boolean =
        restriction in setOf(
            PredictionRestriction.INSUFFICIENT_DATA,
            PredictionRestriction.IRREGULAR,
            PredictionRestriction.HORMONAL_CONTRACEPTION,
            PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING,
            PredictionRestriction.PERIMENOPAUSE,
            PredictionRestriction.PREGNANCY_MODE
        )

    /** Period-day content may still be shown from logged bleeding for these restrictions. */
    fun bleedingContentAllowed(restriction: PredictionRestriction): Boolean =
        restriction in setOf(
            PredictionRestriction.NONE,
            PredictionRestriction.USER_DISABLED_FERTILITY,
            PredictionRestriction.INSUFFICIENT_DATA,
            PredictionRestriction.IRREGULAR,
            PredictionRestriction.PERIMENOPAUSE
        )
}
