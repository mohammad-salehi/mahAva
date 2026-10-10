package com.mahava.app

import com.google.gson.JsonObject
import com.mahava.app.network.PartnerSnapshotDto
import com.mahava.app.partner.PartnerCycleEnrich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerCycleEnrichTest {
    @Test
    fun derivesCycleDayFromPeriodsAlone() {
        val today = 20_000L
        val periods = listOf(JsonObject().apply {
            addProperty("startEpochDay", today - 2) // day 3 of cycle, bleeding
        })
        val snap = PartnerCycleEnrich.deriveFromPeriods(periods, today)
        assertNotNull(snap)
        assertEquals(3, snap!!.cycleDay)
        assertTrue(snap.periodOngoing)
        assertEquals("menstrual", snap.phaseGroup)
        assertFalse(snap.generalOnly)
    }

    @Test
    fun enrichesThinSnapshot() {
        val today = 20_000L
        val periods = listOf(JsonObject().apply {
            addProperty("startEpochDay", today - 10)
            addProperty("endEpochDay", today - 6)
        })
        val thin = PartnerSnapshotDto(phaseGroup = "general", generalOnly = false)
        val out = PartnerCycleEnrich.enrich(thin, periods, today)
        assertNotNull(out)
        assertEquals(11, out!!.cycleDay)
        assertTrue(out.phaseGroup != "general" || out.cycleDay != null)
        assertEquals(11, out.cycleDay)
    }
}
