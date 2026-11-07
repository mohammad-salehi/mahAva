package com.mahava.app

import com.mahava.app.ui.screens.partnerPeriodOngoing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerPeriodOngoingTest {
    private val today = 20_400L

    @Test fun startOnlyEntry_notFlagged_isNotOngoing() {
        assertFalse(partnerPeriodOngoing(today - 60, null, false, today))
        assertFalse(partnerPeriodOngoing(today - 1, null, false, today))
    }

    @Test fun flaggedOngoing_isOngoing() = assertTrue(partnerPeriodOngoing(today - 3, null, true, today))

    @Test fun oldClientWithoutFlag_onlyRecentCounts() {
        assertTrue(partnerPeriodOngoing(today - 4, null, null, today))
        assertFalse(partnerPeriodOngoing(today - 45, null, null, today))
    }

    @Test fun partnerSnapshot_capsForgottenOngoingFlag() {
        val periods = listOf(com.google.gson.JsonObject().apply {
            addProperty("startEpochDay", today - 40); addProperty("stillOngoing", true)
        })
        val snap = com.mahava.app.partner.PartnerCycleEnrich.deriveFromPeriods(periods, today)!!
        assertFalse("day 41 is not an ongoing period", snap.periodOngoing)
    }

    @Test fun endedPeriod_isNotOngoing() = assertFalse(partnerPeriodOngoing(today - 8, today - 3, false, today))
}
