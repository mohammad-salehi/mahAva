package com.mahava.app

import com.google.gson.Gson
import com.mahava.app.network.DataPullDto
import com.mahava.app.network.SyncJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real answers of backend services/mah/data.service.js, including tombstones (`data: null`). */
class SyncJsonTest {
    private val pull = """
        {"ok":true,"now":1760860000000,"cursor":1760859999999,"more":false,"full":false,"records":[
          {"kind":"log","key":"20380","data":{"epochDay":20380,"moods":"calm","sleepHours":7.5,"noSymptoms":false},"deleted":false,"updatedAt":1760850000000,"serverUpdatedAt":1760850000100},
          {"kind":"tool","key":"insight_pms_pain","data":null,"deleted":true,"updatedAt":1760851000000,"serverUpdatedAt":1760851000100},
          {"kind":"period","key":"20370","deleted":true,"updatedAt":1760852000000,"serverUpdatedAt":1760852000100}
        ]}
    """.trimIndent()

    @Test fun pullWithTombstones_parses() {
        val p = SyncJson.parsePull(pull)
        assertEquals(1760859999999L, p.cursor)
        assertFalse(p.more)
        val r = p.records!!
        assertEquals(3, r.size)
        assertEquals(20380L, r[0].data!!.get("epochDay").asLong)
        assertTrue(r[1].deleted)
        assertNull(r[1].data)
        assertNull(r[2].data)
        assertEquals(1760852000000L, r[2].updatedAt)
    }

    @Test fun plainGson_isWhatBrokeTheSync() {
        // Documents the bug the hand parser avoids (Gson cannot put JSON null into JsonObject?).
        val failed = runCatching { Gson().fromJson(pull, DataPullDto::class.java) }.isFailure
        assertTrue(failed)
    }

    @Test fun pushWithDeletedConflict_parses() {
        val p = SyncJson.parsePush("""{"ok":true,"now":5,"applied":2,"conflicts":[{"kind":"log","key":"20381","data":null,"deleted":true,"updatedAt":1760853000000,"serverUpdatedAt":1}],"partnerChanges":[]}""")
        assertEquals(2, p.applied)
        assertEquals(1, p.conflicts!!.size)
        assertTrue(p.conflicts!![0].deleted)
        assertNull(p.conflicts!![0].data)
        assertEquals(0, SyncJson.parsePush("""{"ok":true,"now":5,"applied":0,"conflicts":[]}""").conflicts!!.size)
    }
}
