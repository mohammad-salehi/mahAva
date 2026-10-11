package com.mahava.app.health

import com.mahava.app.data.db.PregnancyEntity
import com.mahava.app.health.models.DateOnlyFormat
import com.mahava.app.health.models.PregnancyData
import com.mahava.app.health.models.PregnancyProgress
import com.mahava.app.util.PersianDigits
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pregnancy dating: calendar-day arithmetic only (no millis/24h).
 * Clinician EDD wins over LMP-derived EDD when datingMethod is clinician_edd.
 */
object PregnancyService {
    const val FULL_TERM_DAYS = 280

    fun eddFromLmp(lmp: LocalDate): LocalDate = lmp.plusDays(FULL_TERM_DAYS.toLong())

    fun toData(e: PregnancyEntity): PregnancyData = PregnancyData(
        id = e.id,
        status = e.status,
        lmpDate = e.lmpEpochDay?.let { DateOnlyFormat.fromEpochDay(it) },
        estimatedDueDate = DateOnlyFormat.fromEpochDay(e.estimatedDueEpochDay),
        datingMethod = e.datingMethod,
        clinicianConfirmed = e.clinicianConfirmed,
        timezone = e.timezone,
        endedAt = e.endedAt,
        endReason = e.endReason
    )

    fun resolveEdd(
        datingMethod: String,
        lmp: LocalDate?,
        clinicianEdd: LocalDate?
    ): LocalDate {
        return when (datingMethod) {
            "clinician_edd" -> requireNotNull(clinicianEdd) { "clinician_edd_required" }
            else -> {
                val start = requireNotNull(lmp) { "lmp_required" }
                eddFromLmp(start)
            }
        }
    }

    fun buildEntity(
        datingMethod: String,
        lmp: LocalDate?,
        clinicianEdd: LocalDate?,
        clinicianConfirmed: Boolean,
        timezone: String = ZoneId.systemDefault().id,
        existingId: Long = 0L,
        nowMillis: Long
    ): PregnancyEntity {
        val edd = resolveEdd(datingMethod, lmp, clinicianEdd)
        return PregnancyEntity(
            id = existingId,
            status = "active",
            lmpEpochDay = lmp?.toEpochDay(),
            estimatedDueEpochDay = edd.toEpochDay(),
            datingMethod = datingMethod,
            clinicianConfirmed = clinicianConfirmed || datingMethod == "clinician_edd",
            timezone = timezone,
            createdAt = nowMillis,
            updatedAt = nowMillis
        )
    }

    /**
     * Gestational age in whole calendar days from LMP-equivalent:
     * age = FULL_TERM_DAYS - (edd - today) in calendar days.
     */
    fun progress(edd: LocalDate, today: LocalDate): PregnancyProgress {
        // ChronoUnit.DAYS is calendar-day safe; Period.days is only the leftover day field.
        val daysUntilEdd = ChronoUnit.DAYS.between(today, edd).toInt()
        val gestationalAgeDays = (FULL_TERM_DAYS - daysUntilEdd).coerceAtLeast(0)
        val completedWeeks = gestationalAgeDays / 7
        val daysIntoWeek = gestationalAgeDays % 7
        // NHS week pages use completed gestational weeks; ordinal copy remains completed+1.
        val contentWeek = completedWeeks.coerceAtLeast(1)
        val progressFraction = (gestationalAgeDays.toFloat() / FULL_TERM_DAYS.toFloat()).coerceIn(0f, 1f)
        val labelFa = buildString {
            append(PersianDigits.toPersian(completedWeeks))
            append(" هفته")
            if (daysIntoWeek > 0) {
                append(" و ")
                append(PersianDigits.toPersian(daysIntoWeek))
                append(" روز")
            }
        }
        val weekOrdinalFa = "در هفتهٔ ${PersianDigits.toPersian(completedWeeks + 1)}"
        return PregnancyProgress(
            gestationalAgeDays = gestationalAgeDays,
            completedWeeks = completedWeeks,
            daysIntoWeek = daysIntoWeek,
            contentWeek = contentWeek,
            progressFraction = progressFraction,
            estimatedDueDate = DateOnlyFormat.fromLocalDate(edd),
            labelFa = labelFa,
            weekOrdinalLabelFa = weekOrdinalFa
        )
    }

    fun progressFromEntity(e: PregnancyEntity, today: LocalDate): PregnancyProgress =
        progress(LocalDate.ofEpochDay(e.estimatedDueEpochDay), today)
}
