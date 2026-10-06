package com.mahava.app

import com.google.gson.Gson
import com.mahava.app.content.ContentBank
import com.mahava.app.content.PhaseContentSelector
import com.mahava.app.content.PhaseHistory
import com.mahava.app.cycle.CycleDayContext
import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.cycle.CycleEngine
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.PeriodInterval
import com.mahava.app.cycle.PredictionKind
import com.mahava.app.cycle.PredictionRestriction
import com.mahava.app.cycle.SubWindowMath
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Loads the bundled JSON straight from the source tree (pure JVM, no Robolectric needed). */
private fun bank(): ContentBank {
    val candidates = listOf("src/main/assets/content/content_bank.json", "app/src/main/assets/content/content_bank.json")
    val f = candidates.map { File(it) }.first { it.exists() }
    return Gson().fromJson(f.readText(), ContentBank::class.java)
}

private fun ctx(sw: CycleSubWindow, bleeding: Boolean = false) =
    CycleDayContext(sw, 10, 28, 10, 0, "note", fromLoggedBleeding = bleeding)

class SubWindowComputationTest {
    private val engine = CycleEngine()
    private val today = LocalDate.of(2026, 10, 6)

    private fun resolveAt(cycleDay: Int, length: Int = 28, end: Int = 5): CycleSubWindow {
        val start = today.minusDays((cycleDay - 1).toLong())
        val r = engine.compute(
            CycleEngine.Input(today, listOf(PeriodInterval(start, start.plusDays((end - 1).toLong()))), length, 5, true)
        )
        return CycleDayContextResolver.resolve(r).subWindow
    }

    @Test fun walk28DayCycle() {
        assertEquals(CycleSubWindow.MENSTRUATION_EARLY, resolveAt(1))
        assertEquals(CycleSubWindow.MENSTRUATION_EARLY, resolveAt(2))
        assertEquals(CycleSubWindow.MENSTRUATION_LATE, resolveAt(3))
        assertEquals(CycleSubWindow.MENSTRUATION_LATE, resolveAt(5))
        assertEquals(CycleSubWindow.FOLLICULAR_EARLY, resolveAt(6))
        assertEquals(CycleSubWindow.FOLLICULAR_LATE, resolveAt(10))
        // 28-day cycle → ovulation range = days 13..17 (C−16 .. C−12)
        assertEquals(CycleSubWindow.FOLLICULAR_LATE, resolveAt(12))
        assertEquals(CycleSubWindow.PERI_OVULATORY, resolveAt(13))
        assertEquals(CycleSubWindow.PERI_OVULATORY, resolveAt(17))
        assertEquals(CycleSubWindow.LUTEAL_EARLY, resolveAt(18))   // 11 days to period
        assertEquals(CycleSubWindow.LUTEAL_EARLY, resolveAt(19))   // 10
        assertEquals(CycleSubWindow.LUTEAL_MID, resolveAt(20))     // 9
        assertEquals(CycleSubWindow.LUTEAL_MID, resolveAt(23))     // 6
        assertEquals(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, resolveAt(24)) // 5
        assertEquals(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, resolveAt(28)) // 1
        assertEquals(CycleSubWindow.LATE_PERIOD, resolveAt(30))
    }

    @Test fun longCycleShiftsOvulationLaterNotDay14() {
        // 35-day cycle: ovulation range = days 20..24, so day 14–16 are still follicular.
        assertEquals(CycleSubWindow.FOLLICULAR_LATE, resolveAt(16, length = 35))
        assertEquals(CycleSubWindow.PERI_OVULATORY, resolveAt(21, length = 35))
        assertEquals(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, resolveAt(32, length = 35))
    }

    @Test fun shortCycleShiftsOvulationEarlier() {
        // 24-day cycle: ovulation range = days 9..13.
        assertEquals(CycleSubWindow.PERI_OVULATORY, resolveAt(9, length = 24))
        assertEquals(CycleSubWindow.LUTEAL_EARLY, resolveAt(14, length = 24))
    }

    @Test fun ovulationRangeWidensWithUncertainty() {
        val c = LocalDate.of(2026, 11, 1)
        val (e3, l3) = SubWindowMath.ovulationRange(c, 3)
        assertEquals(c.minusDays(16), e3); assertEquals(c.minusDays(12), l3)
        val (e5, l5) = SubWindowMath.ovulationRange(c, 5)
        assertEquals(c.minusDays(18), e5); assertEquals(c.minusDays(10), l5)
    }

    @Test fun fertilityWindowAboutEightDaysForStatedLength() {
        val start = today.minusDays(5)
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, start.plusDays(4))), 28, 5, true, fertilityTrackingEnabled = true))
        val p = r.prediction as PredictionKind.Estimate
        val days = p.fertilityLatest.toEpochDay() - p.fertilityEarliest.toEpochDay() + 1
        assertEquals(8L, days)
        assertEquals(p.ovulationLatest, p.fertilityLatest)
        assertTrue(r.showFertility)
    }

    @Test fun nearBoundaryFlagged() {
        val start = today.minusDays(12) // day 13 = first day of ovulation range
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, start.plusDays(4))), 28, 5, true))
        val c = CycleDayContextResolver.resolve(r)
        assertEquals(CycleSubWindow.PERI_OVULATORY, c.subWindow)
        assertTrue(c.nearBoundary)
        assertTrue(c.uncertaintyNoteFa.contains("مرز"))
    }

    @Test fun endNotRecordedIsNotOngoing() {
        val start = today.minusDays(9)
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, null, ongoingFlag = false)), 28, 5, true))
        assertFalse(r.periodOngoing)
        assertNotEquals(CycleSubWindow.MENSTRUATION_LATE, CycleDayContextResolver.resolve(r).subWindow)
    }

    @Test fun irregularWithOngoingBleedingShowsPeriodContent() {
        val start = today.minusDays(1)
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, null)), null, null, false))
        assertEquals(PredictionRestriction.IRREGULAR, r.restriction)
        val c = CycleDayContextResolver.resolve(r)
        assertEquals(CycleSubWindow.MENSTRUATION_EARLY, c.subWindow)
        assertTrue(c.fromLoggedBleeding)
    }

    @Test fun hormonalWithBleedingStaysGeneral() {
        val start = today.minusDays(1)
        val r = engine.compute(CycleEngine.Input(today, listOf(PeriodInterval(start, null)), 28, 5, true, hormonalContraception = true))
        assertEquals(CycleSubWindow.UNKNOWN, CycleDayContextResolver.resolve(r).subWindow)
    }

    @Test fun insufficientDataUnknown() {
        val r = engine.compute(CycleEngine.Input(today, emptyList(), null, null, null))
        assertEquals(CycleSubWindow.UNKNOWN, CycleDayContextResolver.resolve(r).subWindow)
    }
}

class ContentSelectionTest {
    private val b = bank()

    @Test fun everySubWindowHasRichPhaseItem() {
        CycleSubWindow.entries.forEach { sw ->
            val item = b.items.find { it.id == "sw_${sw.id}" }
            assertNotNull("missing sw_${sw.id}", item)
            val ph = item!!.phase
            assertNotNull("no phase block for ${sw.id}", ph)
            assertEquals(sw.id, ph!!.subWindow)
            assertFalse(ph.todayFa.isBlank())
            assertFalse(ph.seekCareFa.isNullOrEmpty())
            assertFalse(ph.careFa.isNullOrEmpty())
            assertFalse("claims for ${sw.id}", ph.claims.isNullOrEmpty())
            if (sw != CycleSubWindow.UNKNOWN && sw != CycleSubWindow.LATE_PERIOD) {
                assertEquals("4 hormones for ${sw.id}", 4, ph.hormones?.size)
                assertFalse(ph.uterusFa.isNullOrBlank())
            }
        }
    }

    @Test fun claimsAndLinksCiteRealSources() {
        b.items.filter { it.phase != null }.forEach { item ->
            val ids = item.sources.mapNotNull { it.id }.toSet()
            item.phase!!.claims!!.forEach { c -> assertTrue("${item.id}: ${c.textFa}", c.sourceIds!!.isNotEmpty() && ids.containsAll(c.sourceIds!!)) }
            item.phase!!.lifeLinks?.forEach { l ->
                assertTrue(l.evidence in setOf("good", "moderate", "limited", "mixed"))
                assertTrue(ids.containsAll(l.sourceIds!!))
            }
            item.sources.forEach { s -> assertEquals("2026-10-06", s.accessed) }
        }
    }

    @Test fun selectsExactSubWindow() {
        CycleSubWindow.entries.filter { it != CycleSubWindow.UNKNOWN }.forEach { sw ->
            val sel = PhaseContentSelector.selectPhase(b, ctx(sw), PredictionRestriction.NONE)
            assertEquals("sw_${sw.id}", sel.item.id)
            assertFalse(sel.usedGeneralFallback)
            assertNull(sel.restrictionNoteFa)
        }
    }

    @Test fun restrictedContextsUseContextItems() {
        val expected = mapOf(
            PredictionRestriction.IRREGULAR to "ctx_irregular",
            PredictionRestriction.HORMONAL_CONTRACEPTION to "ctx_hormonal_contraception",
            PredictionRestriction.POSTPARTUM_OR_BREASTFEEDING to "ctx_postpartum",
            PredictionRestriction.PERIMENOPAUSE to "ctx_perimenopause",
            PredictionRestriction.INSUFFICIENT_DATA to "ctx_insufficient_data",
            PredictionRestriction.PREGNANCY_MODE to "ctx_pregnancy"
        )
        expected.forEach { (r, id) ->
            val sel = PhaseContentSelector.selectPhase(b, ctx(CycleSubWindow.LUTEAL_MID), r)
            assertEquals(id, sel.item.id)
            assertTrue(sel.usedGeneralFallback)
            assertNotNull("restriction note must say why", sel.restrictionNoteFa)
        }
    }

    @Test fun loggedBleedingOverridesIrregularRestriction() {
        val sel = PhaseContentSelector.selectPhase(b, ctx(CycleSubWindow.MENSTRUATION_EARLY, bleeding = true), PredictionRestriction.IRREGULAR)
        assertEquals("sw_menstruation_early", sel.item.id)
        assertNotNull(sel.restrictionNoteFa)
    }

    @Test fun careDefaultsFollowSubWindow() {
        assertEquals("care_sw_luteal_late", PhaseContentSelector.selectCare(b, ctx(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL), PredictionRestriction.NONE, null).item.id)
        assertEquals("care_sw_late", PhaseContentSelector.selectCare(b, ctx(CycleSubWindow.LATE_PERIOD), PredictionRestriction.NONE, null).item.id)
        assertEquals("care_sw_unknown", PhaseContentSelector.selectCare(b, ctx(CycleSubWindow.LUTEAL_MID), PredictionRestriction.HORMONAL_CONTRACEPTION, null).item.id)
        CycleSubWindow.entries.forEach { sw ->
            val c = PhaseContentSelector.selectCare(b, ctx(sw), PredictionRestriction.NONE, null)
            assertTrue(c.item.id.startsWith("care_sw_"))
            assertNull(c.reasonFa)
        }
    }

    private fun log(bleeding: String? = null, pain: Int? = null, moods: String? = null, symptoms: String? = null, energy: String? = null, none: Boolean = false) =
        DailyLogEntity(epochDay = 1, bleeding = bleeding, painScore = pain, moods = moods, physicalSymptoms = symptoms, energy = energy, noSymptoms = none, createdAt = 0, updatedAt = 0)

    @Test fun careIsLogAwareWithPriority() {
        val c = ctx(CycleSubWindow.FOLLICULAR_EARLY)
        fun pick(l: DailyLogEntity) = PhaseContentSelector.selectCare(b, c, PredictionRestriction.NONE, l)
        assertEquals("care_log_heavy", pick(log(bleeding = "heavy", pain = 8, moods = "sad")).item.id)
        assertEquals("care_log_pain", pick(log(pain = 6, moods = "sad")).item.id)
        assertEquals("care_log_mood", pick(log(moods = "anxious", symptoms = "bloating")).item.id)
        assertEquals("care_log_bloating", pick(log(symptoms = "bloating,headache")).item.id)
        assertEquals("care_log_headache", pick(log(symptoms = "breast_tenderness")).item.id)
        assertEquals("care_log_sleep_energy", pick(log(energy = "low")).item.id)
        assertEquals("care_sw_follicular_early", pick(log(none = true)).item.id)
        assertEquals("care_sw_follicular_early", pick(log(pain = 2, moods = "happy")).item.id)
        assertNotNull(pick(log(pain = 6)).reasonFa)
    }

    @Test fun reviewStatusExactAndNoForbiddenWords() {
        assertEquals("مبتنی بر منابع؛ بازبینی پزشکی مستقل انجام نشده", b.medicalReviewStatus)
        val forbidden = listOf("bundled", "بازه عدم قطعیت", "حتماً", "قطعاً باردار", "انرژی‌ات بیشتر است", "میل جنسی‌ات")
        b.items.forEach { item ->
            assertEquals("مبتنی بر منابع؛ بازبینی پزشکی مستقل انجام نشده", item.medicalReviewStatus)
            val text = Gson().toJson(item)
            forbidden.forEach { w -> assertFalse("${item.id} contains $w", text.contains(w)) }
        }
    }
}

class PhaseHistoryTest {
    private val t = LocalDate.of(2026, 10, 6)
    private fun p(back: Long, len: Long = 5) = PeriodEventEntity(startEpochDay = t.minusDays(back).toEpochDay(), endEpochDay = t.minusDays(back - len + 1).toEpochDay(), createdAt = 0, updatedAt = 0)

    @Test fun noCompletedCycleSaysSo() {
        val s = PhaseHistory.summarize(CycleSubWindow.LUTEAL_MID, listOf(p(10)), emptyList())
        assertEquals(0, s.completedCycles)
        assertTrue(s.linesFa.first().contains("هنوز چرخهٔ کاملی"))
    }

    @Test fun mapsRealLogsToSameSubWindowOfPastCycles() {
        val periods = listOf(p(70), p(42), p(14))
        // 4 days before the period that started 42 days ago → premenstrual of cycle 1
        val logs = listOf(
            DailyLogEntity(epochDay = t.minusDays(46).toEpochDay(), physicalSymptoms = "bloating", painScore = 4, createdAt = 0, updatedAt = 0),
            DailyLogEntity(epochDay = t.minusDays(17).toEpochDay(), physicalSymptoms = "bloating,headache", createdAt = 0, updatedAt = 0)
        )
        val s = PhaseHistory.summarize(CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, periods, logs)
        assertEquals(2, s.completedCycles)
        assertEquals(2, s.matchingDays)
        assertTrue(s.linesFa.any { it.contains("نفخ") })
        val other = PhaseHistory.summarize(CycleSubWindow.FOLLICULAR_EARLY, periods, logs)
        assertEquals(0, other.matchingDays)
        assertTrue(other.linesFa.first().contains("ثبتی نداری"))
    }
}
