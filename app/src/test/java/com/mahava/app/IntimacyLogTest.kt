package com.mahava.app

import com.google.gson.JsonParser
import com.mahava.app.content.IntimacyLog
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.content.PartnerLogFormat
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.AppViewModelFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** «رابطه»: saved in the existing log fields, synced, in the doctor report, never shown to the husband. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ToolsTestApplication::class)
class IntimacyLogTest {
    private lateinit var app: ToolsTestApplication
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication() as ToolsTestApplication
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }
    @After fun close() {
        app.onTerminate()
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase()
        app.database.close()
    }

    @Test fun optionsRoundTripThroughTheLogFields() {
        val base = DailyLogEntity(epochDay = 1, createdAt = 1, updatedAt = 1)
        for ((key, _) in IntimacyLog.OPTIONS) assertEquals(key, IntimacyLog.of(IntimacyLog.apply(base, key)))
        val p = IntimacyLog.apply(base, "protected")
        assertTrue(p.intimacyLogged); assertEquals("yes", p.intimacyProtected)
        assertFalse(IntimacyLog.apply(p, "none").intimacyLogged)
        assertNull(IntimacyLog.of(IntimacyLog.apply(p, null)))
        assertEquals("رابطه بدون محافظت", IntimacyLog.reportFa(IntimacyLog.apply(base, "unprotected")))
        assertNull("«نداشتم» is not listed in the report", IntimacyLog.reportFa(IntimacyLog.apply(base, "none")))
        // An older log that only says "logged" still shows in the report.
        assertEquals("رابطه", IntimacyLog.reportFa(base.copy(intimacyLogged = true)))
    }

    @Test fun todaySave_keepsTheRestOfTheLog_andGoesIntoTheSyncPayload() = runBlocking {
        app.prefs.setAccessToken("a"); app.prefs.setRefreshToken("r")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        app.apiClient.setBaseUrl("http://127.0.0.1:9")
        val day = app.clock.today().toEpochDay()
        app.repository.upsertDailyLog(DailyLogEntity(epochDay = day, overallFeeling = "good", note = "n", createdAt = 1, updatedAt = 1))
        val vm = AppViewModelFactory(app).create(AppViewModel::class.java)
        vm.saveTodayIntimacy("unprotected")
        val log = app.database.dailyLogDao().getByDay(day)!!
        assertEquals("unprotected", IntimacyLog.of(log))
        assertEquals("good", log.overallFeeling); assertEquals("n", log.note)
        val rec = app.dataSync.collectLocal()["log/$day"]!!
        assertEquals(true, rec.data!!.get("intimacyLogged").asBoolean)
        assertEquals("no", rec.data!!.get("intimacyProtected").asString)
        vm.saveTodayIntimacy(null)
        assertNull(IntimacyLog.of(app.database.dailyLogDao().getByDay(day)))
    }

    @Test fun husbandNeverSeesIt() {
        val log = JsonParser.parseString("""{"epochDay":20000,"intimacyLogged":true,"intimacyProtected":"no","desire":"high","intimacyDiscomfort":true,"moods":"calm"}""").asJsonObject
        val lines = PartnerLogFormat.lines(log)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.none { it.label.contains("رابطه") || it.value.contains("محافظت") })
        val advice = com.mahava.app.content.PartnerAdvice.changeLineFa(listOf("intimacy"), null)
        assertFalse(advice.contains("رابطه"))
    }
}
