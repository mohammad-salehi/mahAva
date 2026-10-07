package com.mahava.app

import com.mahava.app.content.CravingContent
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySignalContentTest {
    @Test fun lateLutealCravingExplainsAssociationWithoutDeficiencyDiagnosis() {
        val explanation = TodaySignalContent.explain("food", "chocolate", "late_luteal")
        assertTrue(explanation.contains("نزدیک پریود"))
        assertFalse(explanation.contains("کمبود منیزیم"))
    }

    @Test fun unknownCycleStageUsesGeneralExplanation() {
        assertEquals("general", CravingContent.phaseGroupOf(CycleSubWindow.UNKNOWN, null))
        val explanation = TodaySignalContent.explain("mood", "sad", "general")
        assertTrue(explanation.contains("از روی یک روز نمی‌شود"))
    }

    @Test fun periodPainExplainsContractionButOtherDaysDoNot() {
        assertTrue(TodaySignalContent.explain("body", "pain", "menstrual").contains("انقباض"))
        assertFalse(TodaySignalContent.explain("body", "pain", "follicular").contains("پروستاگلاندین"))
    }

    @Test fun hormonalContraceptionForcesGeneralPhaseGroup() {
        val group = TodaySignalContent.effectivePhaseGroup(
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL,
            CyclePhase.LUTEAL,
            hormonalContraception = true,
            regularCycles = true
        )
        assertEquals("general", group)
    }

    @Test fun irregularCyclesForceGeneralPhaseGroup() {
        val group = TodaySignalContent.effectivePhaseGroup(
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL,
            CyclePhase.LUTEAL,
            hormonalContraception = false,
            regularCycles = false
        )
        assertEquals("general", group)
    }

    @Test fun lateLutealPainSourcesIncludeAcog() {
        val full = TodaySignalContent.explainFull("body", "bloating", "late_luteal")
        assertTrue(full.sources.any { it.contains("ACOG") })
        assertTrue(full.textFa.contains("PMS") || full.textFa.contains("پیش از قاعدگی"))
    }
}
