package com.mahava.app

import com.mahava.app.content.PhaseForecastEngine
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import org.junit.Assert.*
import org.junit.Test

class PhaseForecastTest {
    @Test
    fun lateLutealHasScienceReasonsAndTentativeWording() {
        val f = PhaseForecastEngine.build(
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL,
            CyclePhase.LUTEAL
        )
        assertTrue(f.moodLineFa.contains("ممکنه"))
        assertTrue(f.cravingLineFa.contains("ممکنه"))
        val scienceBlob = f.scienceMoodFa + f.scienceCravingFa + f.hormoneSnapshotFa
        assertTrue(scienceBlob.contains("پروژسترون") || scienceBlob.contains("استروژن"))
        assertTrue(scienceBlob.contains("سروتونین") || scienceBlob.contains("PMS"))
        assertTrue(f.hormoneSnapshotFa.contains("فردا"))
        assertTrue(f.disclaimerFa.contains("تشخیص"))
        assertFalse(f.scienceMoodFa.contains("حتماً بیماری"))
        assertEquals(2, f.summaryLinesFa.size)
    }

    @Test
    fun follicularMentionsEstrogen() {
        val f = PhaseForecastEngine.build(CycleSubWindow.FOLLICULAR_EARLY, CyclePhase.FOLLICULAR)
        val blob = f.scienceMoodFa + f.scienceCravingFa
        assertTrue(blob.contains("استروژن"))
        assertTrue(blob.contains("estrogen") || blob.contains("estradiol"))
    }

    @Test
    fun menstrualMentionsProstaglandin() {
        val f = PhaseForecastEngine.build(CycleSubWindow.MENSTRUATION_EARLY, CyclePhase.MENSTRUATION)
        assertTrue(f.scienceMoodFa.contains("پروستاگلاندین"))
        assertTrue(f.scienceCravingFa.contains("آهن") || f.scienceCravingFa.contains("منیزیم"))
    }
}
