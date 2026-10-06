package com.mahava.app.cycle

import java.time.LocalDate

enum class CyclePhase {
    MENSTRUATION, FOLLICULAR, OVULATION_WINDOW, LUTEAL, UNKNOWN
}

enum class PredictionRestriction {
    NONE,
    INSUFFICIENT_DATA,
    IRREGULAR,
    HORMONAL_CONTRACEPTION,
    POSTPARTUM_OR_BREASTFEEDING,
    PERIMENOPAUSE,
    PREGNANCY_MODE,
    USER_DISABLED_FERTILITY
}

data class PeriodInterval(
    val start: LocalDate,
    val endInclusive: LocalDate?, // null = ongoing OR end not recorded (see ongoingFlag)
    val confirmedByUser: Boolean = true,
    /** Explicit ongoing flag. Null keeps the legacy meaning "no end = ongoing". */
    val ongoingFlag: Boolean? = null
) {
    fun isOngoing(): Boolean = endInclusive == null && (ongoingFlag ?: true)
    fun lengthDays(): Int? {
        val end = endInclusive ?: return null
        return (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    }
}

data class CycleLengthSample(
    val start: LocalDate,
    val nextStart: LocalDate,
    val lengthDays: Int
)

sealed class PredictionKind {
    data object Unknown : PredictionKind()
    data class Estimate(
        val medianCycleLength: Int,
        val cyclesUsed: Int,
        val nextPeriodStartEarliest: LocalDate,
        val nextPeriodStartLatest: LocalDate,
        val nextPeriodStartCentral: LocalDate,
        val ovulationEarliest: LocalDate,
        val ovulationLatest: LocalDate,
        val fertilityEarliest: LocalDate,
        val fertilityLatest: LocalDate
    ) : PredictionKind()
}

data class CycleEngineResult(
    val algorithmVersion: String,
    val today: LocalDate,
    val cycleDay: Int?,
    val phase: CyclePhase,
    val lastPeriodStart: LocalDate?,
    val bleedingDay: Int?,
    val periodOngoing: Boolean,
    val daysUntilCentralPeriod: Int?,
    val isLate: Boolean,
    val daysLate: Int?,
    val prediction: PredictionKind,
    val restriction: PredictionRestriction,
    val basisDescriptionFa: String,
    val limitsDescriptionFa: String,
    val reasonFa: String,
    val showFertility: Boolean
) {
    companion object {
        const val ALGORITHM_VERSION = "mahava-cycle-1.1.0"
    }
}
