package com.mahava.app

import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.health.CycleStats
import com.mahava.app.reminders.ReminderScheduler
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CycleStatsTest {
    private fun p(start: Long, len: Int = 5) = PeriodEventEntity(startEpochDay = start, endEpochDay = start + len - 1, createdAt = 0, updatedAt = 0)
    private fun log(day: Long, block: DailyLogEntity.() -> DailyLogEntity = { this }) = DailyLogEntity(epochDay = day, createdAt = 0, updatedAt = 0).block()

    @Test fun cycleLengthsUseOnlyRealStarts() {
        val periods = listOf(p(100), p(128), p(158, 6), p(187))
        assertEquals(listOf(28, 30, 29), CycleStats.completeLengths(periods, 200))
        assertEquals(29, CycleStats.typicalLength(periods, 200))
        assertEquals(6, CycleStats.cycles(periods, 200)[2].bleedDays)
        // The current cycle has no length yet.
        assertNull(CycleStats.cycles(periods, 200).last().length)
    }

    @Test fun phaseOfDay() {
        val periods = listOf(p(100), p(128))
        assertEquals("menstrual", CycleStats.phaseOf(101, periods, 200))
        assertEquals("follicular", CycleStats.phaseOf(105, periods, 200))
        assertEquals("fertile", CycleStats.phaseOf(113, periods, 200))
        assertEquals("luteal", CycleStats.phaseOf(118, periods, 200))
        assertEquals("premenstrual", CycleStats.phaseOf(125, periods, 200))
        assertNull(CycleStats.phaseOf(90, periods, 200))
    }

    @Test fun sleepPrefersMinutesAndFallsBackToHours() {
        assertEquals(7.5f, CycleStats.sleepHours(log(1) { copy(sleepMinutes = 450) })!!, 0.001f)
        assertEquals(6f, CycleStats.sleepHours(log(1) { copy(sleepHours = 6f) })!!, 0.001f)
        assertNull(CycleStats.sleepHours(log(1)))
    }

    @Test fun energyAndMoodScales() {
        assertEquals(2f, CycleStats.energy(log(1) { copy(energy = "low") }))
        assertEquals(5f, CycleStats.energy(log(1) { copy(energy = "low", energyScore = 5) }))
        assertEquals(5f, CycleStats.mood(log(1) { copy(overallFeeling = "great") }))
        assertEquals(2f, CycleStats.mood(log(1) { copy(moods = "sad,anxious") }))
        assertNull(CycleStats.mood(log(1)))
    }

    @Test fun byPhaseSkipsThinGroupsAndUnloggedDays() {
        val periods = listOf(p(100), p(128))
        val logs = listOf(log(100) { copy(energyScore = 2) }, log(101) { copy(energyScore = 2) }, log(105) { copy(energyScore = 4) }, log(106))
        val r = CycleStats.byPhase(logs, periods, 200) { CycleStats.energy(it) }
        assertEquals(1, r.size)
        assertEquals("menstrual", r[0].first)
        assertEquals(2f, r[0].second, 0.001f)
        assertEquals(listOf(2f, 2f, null), CycleStats.daily(logs, 100, 102) { CycleStats.energy(it) })
    }

    @Test fun recommendationAlwaysAtEightLocal() {
        val z = ZoneId.of("Asia/Tehran")
        assertEquals(60 * 60_000L, ReminderScheduler.delayUntilNextMorning(ZonedDateTime.of(2026, 10, 17, 7, 0, 0, 0, z)))
        assertEquals(23 * 60 * 60_000L, ReminderScheduler.delayUntilNextMorning(ZonedDateTime.of(2026, 10, 17, 9, 0, 0, 0, z)))
        assertEquals(24 * 60 * 60_000L, ReminderScheduler.delayUntilNextMorning(ZonedDateTime.of(2026, 10, 17, 8, 0, 0, 0, z)))
    }
}
