package com.mahava.app

import com.mahava.app.checker.CheckerAnswer
import com.mahava.app.checker.CheckerBand
import com.mahava.app.checker.CheckerTopic
import com.mahava.app.checker.SymptomChecker
import com.mahava.app.content.CravingContent
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.insight.DailyInsightEngine
import com.mahava.app.cycle.CycleDayContext
import com.mahava.app.pattern.CycleTrendsAnalyzer
import com.mahava.app.pattern.PatternAnalyzer
import com.mahava.app.pattern.PeriodStartPoint
import com.mahava.app.pattern.SymptomLogPoint
import org.junit.Assert.*
import org.junit.Test

class CravingContentTest {
    @Test fun menstrualChocolateHasEducationalWording() {
        val m = CravingContent.meaning("chocolate", "menstrual")
        assertNotNull(m)
        assertTrue(m!!.meaningFa.contains("ممکنه") || m.meaningFa.contains("گاهی"))
        assertFalse(m.meaningFa.contains("حتماً بیماری"))
        assertTrue(CravingContent.disclaimerFa().contains("تشخیص"))
    }

    @Test fun allKeysHaveGeneralFallback() {
        FoodCravingKeys.ALL.forEach { (key, _) ->
            assertNotNull(CravingContent.meaning(key, "general"))
        }
    }

    @Test fun phaseGroupMapping() {
        assertEquals("menstrual", CravingContent.phaseGroupOf(CycleSubWindow.MENSTRUATION_EARLY, CyclePhase.MENSTRUATION))
        assertEquals("late_luteal", CravingContent.phaseGroupOf(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, CyclePhase.LUTEAL))
        assertEquals("fertile", CravingContent.phaseGroupOf(CycleSubWindow.PERI_OVULATORY, CyclePhase.OVULATION_WINDOW))
    }
}

class PhaseClusterTest {
    @Test fun clustersNeedMinCycles() {
        val starts = listOf(PeriodStartPoint(0), PeriodStartPoint(30))
        val symptoms = listOf(SymptomLogPoint(28, "bloating"))
        assertTrue(PatternAnalyzer.analyzePhaseClusters(symptoms, starts).isEmpty())
    }

    @Test fun clustersFindPremenstrual() {
        val starts = listOf(PeriodStartPoint(0), PeriodStartPoint(30), PeriodStartPoint(60), PeriodStartPoint(90))
        val symptoms = listOf(
            SymptomLogPoint(26, "bloating"),
            SymptomLogPoint(56, "bloating"),
            SymptomLogPoint(86, "bloating")
        )
        val clusters = PatternAnalyzer.analyzePhaseClusters(symptoms, starts, 30)
        assertTrue(clusters.any { it.symptomLabelFa.contains("نفخ") })
        clusters.forEach {
            assertFalse(it.textFa.contains("حتماً"))
            assertTrue(it.textFa.contains("ثبت"))
        }
    }
}

class SymptomCheckerTest {
    @Test fun neverDiagnoses() {
        val qs = SymptomChecker.questions(CheckerTopic.PCOS)
        val yesAll = qs.map { CheckerAnswer(it.id, true) }
        val r = SymptomChecker.score(CheckerTopic.PCOS, yesAll, 1000)
        assertEquals(CheckerBand.HIGHER, r.band)
        assertTrue(r.summaryFa.contains("تشخیص") || r.disclaimerFa.contains("تشخیص"))
        assertTrue(r.disclaimerFa.contains("پزشک"))
        assertFalse(r.summaryFa.contains("تو PCOS داری"))
        assertFalse(r.summaryFa.contains("مبتلا هستی"))
    }

    @Test fun lowBandWhenAllNo() {
        val qs = SymptomChecker.questions(CheckerTopic.ENDO)
        val noAll = qs.map { CheckerAnswer(it.id, false) }
        val r = SymptomChecker.score(CheckerTopic.ENDO, noAll, 1)
        assertEquals(CheckerBand.LOW, r.band)
        assertEquals(0, r.score)
    }
}

class DailyInsightTest {
    @Test fun buildsTeaserAndBody() {
        val ctx = CycleDayContext(
            subWindow = CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL,
            cycleDay = 25,
            estimatedCycleLength = 28,
            daysUntilCentralPeriod = 3,
            daysRelativeToOvulationCentral = 10,
            uncertaintyNoteFa = "تخمینی است."
        )
        val log = DailyLogEntity(
            epochDay = 100,
            moods = "irritable",
            energy = "low",
            foodCravings = "chocolate",
            painScore = 4,
            createdAt = 0,
            updatedAt = 0
        )
        val insight = DailyInsightEngine.build(ctx, null, log, emptyList())
        assertTrue(insight.teaserFa.isNotBlank())
        assertTrue(insight.bodyFa.contains("زودرنج") || insight.bodyFa.contains("حال"))
        assertNotNull(insight.cravingLineFa)
        assertTrue(insight.tipsFa.isNotEmpty())
    }
}

class CycleTrendsTest {
    @Test fun computesLengths() {
        val periods = listOf(
            PeriodEventEntity(id = 1, startEpochDay = 0, endEpochDay = 4, stillOngoing = false, createdAt = 0, updatedAt = 0),
            PeriodEventEntity(id = 2, startEpochDay = 28, endEpochDay = 32, stillOngoing = false, createdAt = 0, updatedAt = 0),
            PeriodEventEntity(id = 3, startEpochDay = 56, endEpochDay = 60, stillOngoing = false, createdAt = 0, updatedAt = 0)
        )
        val r = CycleTrendsAnalyzer.analyze(periods, emptyList())
        assertEquals(2, r.cycleLengths.size)
        assertEquals(28, r.medianCycle)
        assertEquals(5, r.medianBleed)
    }
}
