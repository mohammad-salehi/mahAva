package com.mahava.app.health.models

import java.time.LocalDate

/** Calendar date as YYYY-MM-DD (Gregorian storage). Display conversion to Jalali is UI-only. */
typealias DateOnly = String

object DateOnlyFormat {
    fun fromEpochDay(epochDay: Long): DateOnly = LocalDate.ofEpochDay(epochDay).toString()
    fun toEpochDay(dateOnly: DateOnly): Long = LocalDate.parse(dateOnly).toEpochDay()
    fun fromLocalDate(d: LocalDate): DateOnly = d.toString()
}

data class ApiError(
    val code: String,
    val messageFa: String,
    val retryable: Boolean = false
)

data class SyncOperation(
    val id: String,
    val kind: String,
    val key: String,
    val createdAt: Long,
    val status: SyncOpStatus
)

enum class SyncOpStatus { LOCAL_ONLY, PENDING, SYNCED }

enum class AppHealthMode { CYCLE, PREGNANCY }

data class UserHealthProfile(
    val typicalCycleLength: Int?,
    val typicalBleedLength: Int?,
    val hormonalContraception: Boolean,
    val postpartumOrBreastfeeding: Boolean,
    val perimenopause: Boolean,
    val ageBand: String?,
    val pregnancyMode: Boolean,
    val timezone: String
)

data class CycleRecord(
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val lengthDays: Int?,
    val bleedDays: Int?,
    val completed: Boolean
)

data class SymptomSeverity(val code: String, val severity: Int)

data class SymptomLogView(
    val logDate: DateOnly,
    val mood: String?,
    val physicalSymptoms: List<SymptomSeverity>,
    val pelvicPainScore: Int?,
    val sleepMinutes: Int?,
    val waterIntakeMl: Int?,
    val vaginalDischarge: String?,
    val bleedingIntensity: String?,
    val notes: String?,
    val pregnancyId: Long?
)

data class PregnancyData(
    val id: Long,
    val status: String,
    val lmpDate: DateOnly?,
    val estimatedDueDate: DateOnly,
    val datingMethod: String,
    val clinicianConfirmed: Boolean,
    val timezone: String,
    val endedAt: Long?,
    val endReason: String?
)

data class PregnancyProgress(
    val gestationalAgeDays: Int,
    val completedWeeks: Int,
    val daysIntoWeek: Int,
    val contentWeek: Int,
    val progressFraction: Float,
    val estimatedDueDate: DateOnly,
    val labelFa: String,
    val weekOrdinalLabelFa: String
)

data class PregnancyMilestone(
    val week: Int,
    val titleFa: String,
    val bodyFa: String
)

data class FetalDevelopmentEntry(
    val week: Int,
    val growthFa: String,
    val bodyChangesFa: String,
    val milestonesFa: List<String>,
    val sizeHintFa: String?,
    val sourceIds: List<String>,
    val motherAdviceFa: String = "",
    val fatherAdviceFa: String = "",
    val careFa: String = ""
)

enum class InsightReviewStatus { DRAFT, SOURCE_VERIFIED, CLINICALLY_REVIEWED }

data class ScientificSource(
    val id: String,
    val publisherFa: String,
    val titleFa: String,
    val url: String,
    val publishedOrUpdated: DateOnly?,
    val accessedAt: DateOnly,
    val kind: String,
    val language: String
)

data class HealthInsight(
    val id: String,
    val titleFa: String,
    val summaryFa: String,
    val sections: List<InsightSection>,
    val topic: String,
    val tags: List<String>,
    val audienceGroup: String,
    val applicabilityNoteFa: String,
    val sourceIds: List<String>,
    val publishedAt: DateOnly,
    val reviewedAt: DateOnly?,
    val reviewStatus: InsightReviewStatus,
    val contentVersion: String
)

data class InsightSection(
    val headingFa: String,
    val bodyFa: String
)

enum class AlertSeverity { INFO, ATTENTION, URGENT_GUIDANCE }
enum class AlertStatus { ACTIVE, READ, DISMISSED }

data class HealthAlert(
    val id: String,
    val ruleId: String,
    val ruleVersion: Int,
    val severity: AlertSeverity,
    val titleFa: String,
    val plainLanguageExplanationFa: String,
    val recommendedActionFa: String,
    val observedDates: List<DateOnly>,
    val evidenceReferences: List<String>,
    val deduplicationKey: String,
    val status: AlertStatus,
    val createdAt: Long
)

data class HomeChartPoint(
    val date: DateOnly,
    val painScore: Int?,
    val hasLog: Boolean
)

data class CycleHomeSummary(
    val cycleDay: Int?,
    val phaseLabelFa: String,
    val phaseIsEstimate: Boolean,
    val nextPeriodLabelFa: String?,
    val historySummaryFa: String,
    val chart: List<HomeChartPoint>
)

data class FullAnalyticsData(
    val rangeMonths: Int,
    val completedCycleCount: Int,
    val cycleLengths: List<Int>,
    val bleedLengths: List<Int>,
    val meanCycle: Double?,
    val minCycle: Int?,
    val maxCycle: Int?,
    val meanBleed: Double?,
    val symptomPainDays: Int,
    val loggedDaysInRange: Int,
    val sleepTrend: List<Pair<DateOnly, Int>>,
    val waterTrend: List<Pair<DateOnly, Int>>,
    val emptyReasonFa: String?
)
