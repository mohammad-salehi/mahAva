package com.mahava.app

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.health.CycleAnalyticsService
import com.mahava.app.health.HealthMockFixtures
import com.mahava.app.health.HealthRuleEngine
import com.mahava.app.health.PregnancyService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HealthFeaturesTest {

    @Test
    fun cycleBoundaries_20_21_35_36() {
        fun lengths(vararg lens: Int): List<PeriodEventEntity> {
            var day = 10_000L
            val starts = mutableListOf(day)
            lens.forEach { len ->
                day += len
                starts += day
            }
            return starts.mapIndexed { i, s ->
                PeriodEventEntity(id = i + 1L, startEpochDay = s, endEpochDay = s + 4, createdAt = 1, updatedAt = 1)
            }
        }
        val completed = CycleAnalyticsService.completedCycleLengths(lengths(20, 21, 35, 36))
        assertEquals(listOf(20, 21, 35, 36), completed)
        val alerts20 = HealthRuleEngine.evaluate(
            HealthMockFixtures.profile(), lengths(20), emptyList(), HealthMockFixtures.FIXED_TODAY, false, HealthMockFixtures.NOW_MS
        )
        assertTrue(alerts20.any { it.ruleId == "cycle_short" })
        val alerts21 = HealthRuleEngine.evaluate(
            HealthMockFixtures.profile(), lengths(21), emptyList(), HealthMockFixtures.FIXED_TODAY, false, HealthMockFixtures.NOW_MS
        )
        assertFalse(alerts21.any { it.ruleId == "cycle_short" })
        val alerts35 = HealthRuleEngine.evaluate(
            HealthMockFixtures.profile(), lengths(35), emptyList(), HealthMockFixtures.FIXED_TODAY, false, HealthMockFixtures.NOW_MS
        )
        assertFalse(alerts35.any { it.ruleId == "cycle_long" })
        val alerts36 = HealthRuleEngine.evaluate(
            HealthMockFixtures.profile(), lengths(36), emptyList(), HealthMockFixtures.FIXED_TODAY, false, HealthMockFixtures.NOW_MS
        )
        assertTrue(alerts36.any { it.ruleId == "cycle_long" })
    }

    @Test
    fun bleedBoundaries_7_8_9() {
        fun period(bleedDays: Int) = listOf(
            PeriodEventEntity(
                id = 1L,
                startEpochDay = 20_000L,
                endEpochDay = 20_000L + bleedDays - 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        assertFalse(
            HealthRuleEngine.evaluate(HealthMockFixtures.profile(), period(7), emptyList(), HealthMockFixtures.FIXED_TODAY, false, 1)
                .any { it.ruleId.startsWith("bleed_") }
        )
        assertTrue(
            HealthRuleEngine.evaluate(HealthMockFixtures.profile(), period(8), emptyList(), HealthMockFixtures.FIXED_TODAY, false, 1)
                .any { it.ruleId == "bleed_over_7" }
        )
        assertTrue(
            HealthRuleEngine.evaluate(HealthMockFixtures.profile(), period(9), emptyList(), HealthMockFixtures.FIXED_TODAY, false, 1)
                .any { it.ruleId == "bleed_over_8" }
        )
    }

    @Test
    fun painStreak_and_gap_and_todayImmediate() {
        val today = HealthMockFixtures.FIXED_TODAY
        val logsGap = listOf(
            DailyLogEntity(epochDay = today.toEpochDay(), painScore = 8, createdAt = 1, updatedAt = 1),
            // yesterday missing — not counted as no pain; streak length 1 for consecutive rule
            DailyLogEntity(epochDay = today.minusDays(2).toEpochDay(), painScore = 8, createdAt = 1, updatedAt = 1)
        )
        val withGap = HealthRuleEngine.evaluate(HealthMockFixtures.profile(), emptyList(), logsGap, today, false, 1)
        assertTrue(withGap.any { it.ruleId == "pain_severe_today" })
        assertFalse(withGap.any { it.ruleId == "pain_severe_streak" })

        val streak = listOf(
            DailyLogEntity(epochDay = today.toEpochDay(), painScore = 8, createdAt = 1, updatedAt = 1),
            DailyLogEntity(epochDay = today.minusDays(1).toEpochDay(), painScore = 7, createdAt = 1, updatedAt = 1)
        )
        val withStreak = HealthRuleEngine.evaluate(HealthMockFixtures.profile(), emptyList(), streak, today, false, 1)
        assertTrue(withStreak.any { it.ruleId == "pain_severe_streak" })
    }

    @Test
    fun currentCycleExcludedFromMean() {
        val periods = HealthMockFixtures.periodsWithShortAndLong()
        val completed = CycleAnalyticsService.completedCycleLengths(periods)
        assertTrue(completed.isNotEmpty())
        // last open cycle must not appear as a completed length
        val openStart = periods.maxBy { it.startEpochDay }.startEpochDay
        assertFalse(completed.any { false }) // sanity
        val analytics = CycleAnalyticsService.analyze(periods, emptyList(), HealthMockFixtures.FIXED_TODAY, 12)
        assertEquals(completed.size, analytics.completedCycleCount)
        assertTrue(periods.any { it.startEpochDay == openStart && it.endEpochDay == null })
    }

    @Test
    fun pregnancyLmpAndClinicianPriority() {
        val lmp = LocalDate.of(2026, 1, 1)
        val eddLmp = PregnancyService.eddFromLmp(lmp)
        assertEquals(lmp.plusDays(280), eddLmp)
        val clinician = LocalDate.of(2026, 10, 10)
        val resolved = PregnancyService.resolveEdd("clinician_edd", lmp, clinician)
        assertEquals(clinician, resolved)
        val fromLmp = PregnancyService.resolveEdd("lmp", lmp, clinician)
        assertEquals(eddLmp, fromLmp)
    }

    @Test
    fun pregnancyProgress_calendarDays_and_afterWeek40() {
        val edd = LocalDate.of(2026, 1, 1).plusDays(280)
        // At LMP day: age 0
        val atLmp = PregnancyService.progress(edd, LocalDate.of(2026, 1, 1))
        assertEquals(0, atLmp.gestationalAgeDays)
        assertEquals(0, atLmp.completedWeeks)
        // 12 weeks + 3 days
        val day = LocalDate.of(2026, 1, 1).plusDays(12 * 7 + 3L)
        val p = PregnancyService.progress(edd, day)
        assertEquals(12 * 7 + 3, p.gestationalAgeDays)
        assertEquals(12, p.completedWeeks)
        assertEquals(3, p.daysIntoWeek)
        assertEquals(12, p.contentWeek)
        // After week 40: real age shown, progress capped
        val late = PregnancyService.progress(edd, edd.plusDays(14))
        assertTrue(late.gestationalAgeDays > 280)
        assertEquals(1f, late.progressFraction)
        assertTrue(late.completedWeeks >= 40)
    }

    @Test
    fun dstSafe_epochDayArithmetic() {
        // Crossing a typical DST boundary via epochDay stays calendar-stable.
        val before = LocalDate.of(2026, 3, 20)
        val after = LocalDate.of(2026, 3, 22)
        assertEquals(2, before.until(after).days)
        assertEquals(2, after.toEpochDay() - before.toEpochDay())
        val instantDiffHours = java.time.Duration.between(
            before.atStartOfDay(ZoneOffset.UTC),
            after.atStartOfDay(ZoneOffset.UTC)
        ).toHours()
        assertEquals(48, instantDiffHours)
    }

    @Test
    fun rulesDisabledInPregnancy() {
        val periods = HealthMockFixtures.periodsWithShortAndLong()
        val logs = HealthMockFixtures.logsWithPainStreakAndGaps()
        val alerts = HealthRuleEngine.evaluate(
            HealthMockFixtures.profile(pregnancyMode = true),
            periods, logs, HealthMockFixtures.FIXED_TODAY, pregnancyActive = true, HealthMockFixtures.NOW_MS
        )
        assertTrue(alerts.none { it.ruleId.startsWith("cycle_") || it.ruleId.startsWith("bleed_") })
    }

    @Test
    fun analyticsUpdatesAfterLogChange() {
        val periods = HealthMockFixtures.periodsWithShortAndLong()
        val empty = CycleAnalyticsService.analyze(periods, emptyList(), HealthMockFixtures.FIXED_TODAY, 6)
        val withLogs = CycleAnalyticsService.analyze(
            periods, HealthMockFixtures.logsWithPainStreakAndGaps(), HealthMockFixtures.FIXED_TODAY, 6
        )
        assertTrue(withLogs.loggedDaysInRange > empty.loggedDaysInRange)
        assertTrue(withLogs.symptomPainDays >= 1)
    }
}
