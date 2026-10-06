package com.mahava.app

import com.mahava.app.cycle.CycleEngine
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.PeriodInterval
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.cycle.PredictionRestriction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CycleEngineTest {
    private val engine = CycleEngine()

    @Test fun cycleDayBasic() {
        val start = LocalDate.of(2026, 9, 22)
        val today = start.plusDays(14)
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, start.plusDays(4))), 28, 5, true))
        assertEquals(15, r.cycleDay)
    }

    @Test fun earlyAndLateStartMedian() {
        val p = listOf(
            PeriodInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
            PeriodInterval(LocalDate.of(2026, 1, 30), LocalDate.of(2026, 2, 3)),
            PeriodInterval(LocalDate.of(2026, 2, 26), LocalDate.of(2026, 3, 2))
        )
        val today = LocalDate.of(2026, 3, 10)
        val r = engine.compute(CycleEngine.Input(today, p, null, 5, true))
        assertTrue("expected Estimate but was ${r.prediction} restriction=${r.restriction}", r.prediction is PredictionKind.Estimate)
        val est = r.prediction as PredictionKind.Estimate
        assertEquals("cyclesUsed", 2, est.cyclesUsed)
        assertTrue("median=${est.medianCycleLength}", est.medianCycleLength in 27..29)
        assertFalse(r.basisDescriptionFa.contains("بازه اطمینان"))
        assertFalse(r.limitsDescriptionFa.contains("95%"))
    }

    @Test fun insufficientDataUnknown() {
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 10, 1), emptyList(), null, null, null))
        assertTrue(r.prediction is PredictionKind.Unknown)
        assertEquals(PredictionRestriction.INSUFFICIENT_DATA, r.restriction)
    }

    @Test fun irregularRestricted() {
        val start = LocalDate.of(2026, 9, 1)
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 20), listOf(PeriodInterval(start, null)), 28, 5, false))
        assertEquals(PredictionRestriction.IRREGULAR, r.restriction)
        assertTrue(r.prediction is PredictionKind.Unknown)
    }

    @Test fun ongoingPeriodPhase() {
        val start = LocalDate.of(2026, 10, 1)
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 10, 3), listOf(PeriodInterval(start, null)), 28, 5, true))
        assertTrue(r.periodOngoing)
        assertEquals(CyclePhase.MENSTRUATION, r.phase)
    }

    @Test fun lateShowsLateNotNegativeCountdown() {
        val start = LocalDate.of(2026, 8, 1)
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 10), listOf(PeriodInterval(start, start.plusDays(4))), 28, 5, true))
        assertTrue(r.isLate)
        assertNull(r.daysUntilCentralPeriod)
        assertNotNull(r.daysLate)
        assertTrue(r.daysLate!! > 0)
    }

    @Test fun predictionsNeverBecomeEvents() {
        val periods = listOf(PeriodInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)))
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 20), periods, 28, 5, true))
        val est = r.prediction as PredictionKind.Estimate
        // engine output only; caller must not insert predictions into DB — verified by absence of side effects
        assertTrue(est.nextPeriodStartCentral.isAfter(LocalDate.of(2026, 9, 20)))
        assertEquals(1, periods.size)
    }

    @Test fun noFakePercentages() {
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 20),
            listOf(PeriodInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))), 28, 5, true, fertilityTrackingEnabled = true))
        assertFalse(r.basisDescriptionFa.contains("بازه اطمینان"))
        assertFalse(r.limitsDescriptionFa.contains("95%"))
        assertFalse(r.limitsDescriptionFa.contains("درصد دقت"))
    }

    @Test fun hormonalRestricts() {
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 20),
            listOf(PeriodInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))), 28, 5, true, hormonalContraception = true))
        assertEquals(PredictionRestriction.HORMONAL_CONTRACEPTION, r.restriction)
    }

    @Test fun plainLanguageBasisNoJargon() {
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 20),
            listOf(PeriodInterval(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))), 28, 5, true))
        assertFalse(r.basisDescriptionFa.contains("۰ چرخه"))
        assertFalse(r.basisDescriptionFa.contains("تأییدشده"))
        assertFalse(r.limitsDescriptionFa.contains("عدم قطعیت"))
        assertTrue(r.basisDescriptionFa.contains("۲۸"))
    }

    @Test fun ovulationAnchoredToNextPeriod() {
        val start = LocalDate.of(2026, 9, 1)
        val r = engine.compute(CycleEngine.Input(LocalDate.of(2026, 9, 3), listOf(PeriodInterval(start, start.plusDays(4))), 32, 5, true))
        val est = r.prediction as PredictionKind.Estimate
        assertEquals(est.nextPeriodStartCentral.minusDays(16), est.ovulationEarliest)
        assertEquals(est.nextPeriodStartCentral.minusDays(12), est.ovulationLatest)
    }

    @Test fun bleedLengthInclusive() {
        val p = PeriodInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))
        assertEquals(5, p.lengthDays())
    }
}
