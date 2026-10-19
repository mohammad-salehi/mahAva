package com.mahava.app

import com.mahava.app.cycle.CycleEngine
import com.mahava.app.cycle.PeriodInterval
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.reminders.ReminderScheduler
import com.mahava.app.ui.screens.ToolCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OvulationReminderTest {
    private val engine = CycleEngine()
    private val start = LocalDate.of(2026, 10, 1)
    private fun regular(today: LocalDate) = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, start.plusDays(4))), 28, 5, true))

    @Test fun ovulationDayIsMiddleOfTheEstimatedRange() {
        val r = regular(start.plusDays(5))
        val est = r.prediction as PredictionKind.Estimate
        val ov = ReminderScheduler.estimatedOvulationDay(r, pregnant = false)!!
        assertFalse(ov.isBefore(est.ovulationEarliest)); assertFalse(ov.isAfter(est.ovulationLatest))
        assertNull(ReminderScheduler.estimatedOvulationDay(r, pregnant = true))
    }

    @Test fun noOvulationReminderForIrregularOrUnknownCycles() {
        val irregular = engine.compute(CycleEngine.Input(start.plusDays(5), listOf(PeriodInterval(start, null)), 28, 5, false))
        assertNull(ReminderScheduler.estimatedOvulationDay(irregular, false))
        val none = engine.compute(CycleEngine.Input(start.plusDays(5), emptyList(), null, null, null))
        assertNull(ReminderScheduler.estimatedOvulationDay(none, false))
    }

    @Test fun firesOncePerCycleFromTheDayBefore() {
        val ov = LocalDate.of(2026, 10, 15)
        val due = 9 * 60
        assertFalse(ReminderScheduler.ovulationDue(ov, ov.minusDays(2), 12 * 60, due, -1))
        assertFalse(ReminderScheduler.ovulationDue(ov, ov.minusDays(1), 8 * 60, due, -1)) // before 09:00
        assertTrue(ReminderScheduler.ovulationDue(ov, ov.minusDays(1), 9 * 60, due, -1))
        assertTrue(ReminderScheduler.ovulationDue(ov, ov, 10 * 60, due, -1)) // catch-up if the phone was off
        assertFalse(ReminderScheduler.ovulationDue(ov, ov, 10 * 60, due, ov.toEpochDay())) // already sent this cycle
        assertFalse(ReminderScheduler.ovulationDue(ov, ov.plusDays(1), 10 * 60, due, -1))
        assertTrue(ReminderScheduler.ovulationDue(ov.plusDays(28), ov.plusDays(27), 10 * 60, due, ov.toEpochDay())) // next cycle
        assertFalse(ReminderScheduler.ovulationDue(null, ov, 10 * 60, due, -1))
    }

    @Test fun periodReminderZeroToTwoDaysBefore() {
        assertTrue(ReminderScheduler.periodDue(2, false, 600, 540))
        assertTrue(ReminderScheduler.periodDue(0, false, 600, 540))
        assertFalse(ReminderScheduler.periodDue(3, false, 600, 540))
        assertFalse(ReminderScheduler.periodDue(1, true, 600, 540))
        assertFalse(ReminderScheduler.periodDue(1, false, 500, 540))
        assertFalse(ReminderScheduler.periodDue(null, false, 600, 540))
    }

    @Test fun waterIsFixedAtOnePm() {
        assertEquals(13 to 0, ReminderScheduler.fixedTime("water"))
        assertNull(ReminderScheduler.fixedTime("ovulation"))
    }
}

class ToolsCatalogTest {
    @Test fun removedToolsAreGoneAndCycleToolKept() {
        val ids = ToolCatalog.all.map { it.id }
        listOf("insights", "recommendation", "doctor", "comparison", "pain").forEach { assertFalse(it, it in ids) }
        assertTrue("cycle" in ids)
        assertTrue("reminders" in ids)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ToolCatalog.REMOVED.containsAll(listOf("insights", "recommendation", "doctor")))
    }
}
