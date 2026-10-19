package com.mahava.app

import androidx.room.Room
import com.mahava.app.content.BodyNowContent
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
        "نوسانات خلقی و علائم جسمانی", "فعالیت بدنی با شدت متوسط", "هورمونت بالاست", "بی‌حوصله‌ای", "عزیز دلم", "خوشگلم")

    @Test fun everyPhaseGivesTwoOrThreeShortDistinctLines() {
        listOf("menstrual", "follicular", "fertile", "early_luteal", "late_luteal", "general", "unknown").forEach { g ->
            val b = BodyNowContent.forCycle(g, null)
            assertTrue(g, b.points.size in 2..3)
            assertEquals(b.points.size, b.points.distinct().size)
            assertEquals("بر اساس زمان تقریبی چرخه‌ت", b.basisFa)
            b.points.forEach { line -> assertTrue(line, line.length <= 90); banned.forEach { assertFalse(line, line.contains(it)) } }
        }
    }

    @Test fun loggedSymptomReplacesTheGenericGuess() {
        val log = DailyLogEntity(epochDay = 1, physicalSymptoms = "headache", overallFeeling = "bad", createdAt = 1, updatedAt = 1)
        val b = BodyNowContent.forCycle("late_luteal", log)
        assertTrue(b.points.any { it.contains("سردرد ثبت کردی") })
        assertFalse(b.points.any { it.contains("نفخ یا حساسیت سینه") })
        assertTrue(b.points.any { it.contains("حال نداری") })
        assertEquals("بر اساس ثبت امروزت و زمان تقریبی چرخه‌ت", b.basisFa)
    }

    @Test fun pregnancyShowsWeekNotesNotCycle() {
        listOf(null, 8, 20, 34).forEach { w ->
            val b = BodyNowContent.forPregnancy(w)
            assertTrue(b.points.isNotEmpty() && b.points.size <= 3)
            assertFalse(b.points.any { it.contains("چرخه") || it.contains("پریود") })
        }
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