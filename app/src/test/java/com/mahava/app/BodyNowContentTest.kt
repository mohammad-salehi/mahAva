package com.mahava.app

import androidx.room.Room
import com.mahava.app.content.BodyNowContent
import com.mahava.app.content.CravingContent
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncOutcome
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

class BodyNowContentTest {
    private val banned = listOf("ممکن است تجربه کنید", "توصیه می‌شود", "در این مرحله از چرخه قاعدگی", "افزایش سطح پروژسترون",
        "نوسانات خلقی", "علائم جسمانی", "فعالیت بدنی با شدت متوسط", "هورمونت بالاست", "بی‌حوصله‌ای", "عزیز دلم", "خوشگلم", "می‌باشد")
    private val labels = listOf(BodyNowContent.LABEL_HORMONES, BodyNowContent.LABEL_FEEL, BodyNowContent.LABEL_EAT, BodyNowContent.LABEL_DO, BodyNowContent.LABEL_LIMIT)

    private fun checkFull(name: String, b: BodyNowContent.BodyNow) {
        val got = b.rows.map { it.label }
        labels.forEach { assertTrue("$name misses $it", it in got) }
        b.rows.forEach { r ->
            assertTrue("$name ${r.key} empty", r.text.isNotBlank())
            assertTrue("$name ${r.key} too long", r.text.length <= 190)
            banned.forEach { assertFalse("$name: ${r.text}", r.text.contains(it)) }
        }
        assertTrue("$name has no sources", b.sourceIds.isNotEmpty())
        b.sourceIds.forEach { assertTrue("$name unknown source $it", it in BodyNowContent.SOURCES) }
    }

    @Test fun everySubWindowHasEveryRow() {
        CycleSubWindow.entries.forEach { sw ->
            assertTrue(sw.name, sw in BodyNowContent.cycleStates)
            val group = if (sw == CycleSubWindow.LATE_PERIOD || sw == CycleSubWindow.UNKNOWN) "general" else CravingContent.phaseGroupOf(sw, null)
            val b = BodyNowContent.forCycle(group, null, sw)
            assertEquals(sw.name, BodyNowContent.cycleStates.getValue(sw).hormones, b.rows.first().text)
            assertEquals("بر اساس زمان تقریبی چرخه‌ت", b.basisFa)
            checkFull(sw.name, b)
        }
    }

    @Test fun neighbouringSubWindowsDiffer() {
        val all = BodyNowContent.cycleStates.values.toList()
        assertEquals(all.size, all.map { it.hormones }.distinct().size)
        assertEquals(all.size, all.map { it.eat }.distinct().size)
        assertEquals(all.size, all.map { it.limit }.distinct().size)
    }

    @Test fun oldPhaseGroupsStillWork() {
        listOf("menstrual", "follicular", "fertile", "early_luteal", "late_luteal", "general", "unknown").forEach { g ->
            checkFull(g, BodyNowContent.forCycle(g, null))
        }
        // Contraception / irregular cycles come in as "general": no phase guess even if the calendar says ovulation.
        assertEquals(CycleSubWindow.UNKNOWN, BodyNowContent.pickSubWindow("general", CycleSubWindow.PERI_OVULATORY))
        assertEquals(CycleSubWindow.LATE_PERIOD, BodyNowContent.pickSubWindow("general", CycleSubWindow.LATE_PERIOD))
        assertEquals(CycleSubWindow.LUTEAL_MID, BodyNowContent.pickSubWindow("early_luteal", CycleSubWindow.LUTEAL_MID))
    }

    @Test fun loggedSymptomReplacesTheGenericGuess() {
        val log = DailyLogEntity(epochDay = 1, physicalSymptoms = "headache", overallFeeling = "bad", createdAt = 1, updatedAt = 1)
        val b = BodyNowContent.forCycle("late_luteal", log, CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL)
        val feel = b.rows.first { it.key == "feel" }.text
        assertTrue(feel.contains("سردرد ثبت کردی"))
        assertTrue(feel.contains("حال نداری"))
        assertFalse(feel.contains("حساسیت سینه داشته باشی"))
        assertEquals("بر اساس ثبت امروزت و زمان تقریبی چرخه‌ت", b.basisFa)
    }

    @Test fun everyPregnancyWeekHasEveryRowAndNoCycleTalk() {
        (1..42).forEach { w ->
            val b = BodyNowContent.forPregnancy(w)
            checkFull("week $w", b)
            assertTrue(b.rows.any { it.key == "warn" })
            assertEquals("بر اساس هفتهٔ بارداریت", b.basisFa)
            b.rows.forEach { assertFalse("week $w: ${it.text}", it.text.contains("چرخه") || it.text.contains("پریود")) }
        }
        assertEquals(9, BodyNowContent.pregnancyStates.map { it.second.hormones }.distinct().size)
        assertTrue(BodyNowContent.forPregnancy(null).points.isNotEmpty())
    }

    @Test fun pregnancyFoodSafetyIsCovered() {
        val all = BodyNowContent.pregnancyStates.joinToString(" ") { it.second.limit + it.second.eat }
        listOf("الکل", "۲۰۰ میلی‌گرم", "غیرپاستوریزه", "پرجیوه", "نیم‌پز").forEach { assertTrue(it, all.contains(it)) }
    }

    @Test fun moodSeriesKeepsUnloggedDaysEmpty() {
        val logs = listOf(
            DailyLogEntity(epochDay = 100, overallFeeling = "good", createdAt = 1, updatedAt = 1),
            DailyLogEntity(epochDay = 102, overallFeeling = "very_bad", createdAt = 1, updatedAt = 1),
            DailyLogEntity(epochDay = 103, note = "فقط یادداشت", createdAt = 1, updatedAt = 1)
        )
        val s = BodyNowContent.moodSeries(logs, 103, days = 5)
        assertEquals(listOf(99L, 100L, 101L, 102L, 103L), s.map { it.first })
        assertEquals(listOf(null, "good", null, "bad", null), s.map { it.second })
        assertEquals(3, BodyNowContent.MOODS.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TodayMoodSyncTest {
    @Test fun todayMoodSyncsRestoresAfterReloginAndCanChange() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as MahavaApplication
        val db = Room.inMemoryDatabaseBuilder(app, MahavaDatabase::class.java).allowMainThreadQueries().build()
        val server = FakeDataServer()
        app.prefs.setAccessToken("a"); app.prefs.setRefreshToken("r")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        val clock = FakeAppClock(LocalDate.of(2026, 10, 8))
        val sync = DataSyncRepository(app, db, app.prefs, app.apiClient, app.authRepository, clock, server)
        sync.resetAll()
        val day = LocalDate.of(2026, 10, 8).toEpochDay()
        val t0 = 1_790_000_000_000L
        db.dailyLogDao().upsert(DailyLogEntity(epochDay = day, overallFeeling = "good", createdAt = t0, updatedAt = t0))
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals("good", server.live("log")[day.toString()]!!.get("overallFeeling").asString)
        // Change today's pick.
        db.dailyLogDao().upsert(db.dailyLogDao().getByDay(day)!!.copy(overallFeeling = "bad", updatedAt = t0 + 10))
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals("bad", server.live("log")[day.toString()]!!.get("overallFeeling").asString)
        // Logout, log in again: the pick comes back from the server.
        sync.clearLocalCache()
        assertNull(db.dailyLogDao().getByDay(day))
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals("bad", BodyNowContent.moodOf(db.dailyLogDao().getByDay(day)))
        assertEquals(0, sync.pendingCount())
        db.close()
    }
}