package com.mahava.app

import com.mahava.app.network.MahApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PartnerShareParseTest {
    private val api = MahApiClient()

    @Test
    fun parsesSnapshotEvenWhenUpdatedAtIsNumber() {
        val raw = """
            {"ok":true,"snapshot":{"phaseGroup":"menstrual","cycleDay":3,"periodOngoing":true,
             "today":{"epochDay":20000,"moods":["sad"],"symptoms":[],"cravings":[],"painScore":2,"log":null}},
             "logs":[{"epochDay":20000,"moods":"sad"}],
             "periods":[{"startEpochDay":19998}],
             "updatedAt":1728000000000,"version":2}
        """.trimIndent()
        val share = api.parsePartnerShare(raw)
        assertNotNull(share.snapshot)
        assertEquals("menstrual", share.snapshot!!.phaseGroup)
        assertEquals(3, share.snapshot!!.cycleDay)
        assertEquals("1728000000000", share.updatedAt)
        assertEquals(1, share.logs?.size)
    }

    @Test
    fun nullSnapshotDoesNotCrash() {
        val share = api.parsePartnerShare("""{"ok":true,"snapshot":null,"logs":[],"periods":[]}""")
        assertNull(share.snapshot)
    }
}
