package com.mahava.app

import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahava.app.content.PartnerLogFormat
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.network.DataPullDto
import com.mahava.app.network.DataPushDto
import com.mahava.app.network.SyncRecordDto
import com.mahava.app.sync.DataSyncRemote
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncOutcome
import com.mahava.app.sync.SyncRecord
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * In-memory stand-in for /api/mah/data with the same rules as the backend: last-write-wins on
 * the client's updatedAt, tombstones, full pull skips deleted, incremental pull by server cursor.
 * Data goes through JSON text both ways, like the real network.
 */
class FakeDataServer : DataSyncRemote {
    data class Row(val kind: String, val key: String, val data: String?, val updatedAt: Long, val deleted: Boolean, val serverTs: Long)
    private val gson = Gson()
    val rows = LinkedHashMap<String, Row>()
    private var tick = 1000L
    private val MIN_TS = 1_420_070_400_000L // 2015-01-01, as on the server

    private fun dto(r: Row) = SyncRecordDto(r.kind, r.key, r.data?.let { JsonParser.parseString(it).asJsonObject }, r.deleted, r.updatedAt, r.serverTs)

    override suspend fun pull(since: Long): DataPullDto {
        val list = if (since <= 0) rows.values.filter { !it.deleted } else rows.values.filter { it.serverTs > since }
        return DataPullDto(now = tick, cursor = tick, more = false, full = since <= 0, records = list.sortedBy { it.serverTs }.map(::dto))
    }

    override suspend fun push(changes: List<SyncRecord>): DataPushDto {
        // Same validation as the backend: a missing/zero time fails the whole batch (HTTP 400).
        changes.forEach { require(it.updatedAt >= MIN_TS) { "زمان نامعتبر: ${it.kind}/${it.key}" } }
        val conflicts = mutableListOf<SyncRecordDto>()
        var applied = 0
        for (c in changes) {
            val id = "${c.kind}/${c.key}"
            val existing = rows[id]
            if (existing != null && existing.updatedAt > c.updatedAt) { conflicts += dto(existing); continue }
            if (existing == null && c.deleted) { applied++; continue }
            tick += 1
            rows[id] = Row(c.kind, c.key, if (c.deleted) null else gson.toJson(c.data), c.updatedAt, c.deleted, tick)
            applied++
        }
        return DataPushDto(now = tick, applied = applied, conflicts = conflicts)
    }

    /** A change made on another phone. */
    fun inject(kind: String, key: String, data: JsonObject?, updatedAt: Long, deleted: Boolean = false) {
        tick += 1
        rows["$kind/$key"] = Row(kind, key, data?.let { gson.toJson(it) }, updatedAt, deleted, tick)
    }

    fun live(kind: String): Map<String, JsonObject> =
        rows.values.filter { it.kind == kind && !it.deleted }.associate { it.key to JsonParser.parseString(it.data).asJsonObject }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataSyncRoundTripTest {
    private lateinit var app: MahavaApplication
    private lateinit var db: MahavaDatabase
    private lateinit var server: FakeDataServer
    private lateinit var sync: DataSyncRepository
    private val clock = FakeAppClock(LocalDate.of(2026, 10, 8))
    private val gson = Gson()

    @Before fun setup() = runBlocking {
        app = RuntimeEnvironment.getApplication() as MahavaApplication
        db = Room.inMemoryDatabaseBuilder(app, MahavaDatabase::class.java).allowMainThreadQueries().build()
        server = FakeDataServer()
        app.prefs.setAccessToken("test-access")
        app.prefs.setRefreshToken("test-refresh")
        app.prefs.setAccount("09120000000", "user-1")
        app.prefs.setAccountRole("female")
        sync = DataSyncRepository(app, db, app.prefs, app.apiClient, app.authRepository, clock, server)
        sync.resetAll()
    }

    @After fun tearDown() { db.close() }

    private val today = LocalDate.of(2026, 10, 8).toEpochDay()
    private val t0 = 1_790_000_000_000L

    private fun fullLog(day: Long, updated: Long) = DailyLogEntity(
        epochDay = day, bleeding = "medium", noSymptoms = false, painScore = 6, painLocations = "lower_abdomen,back",
        painActivityImpact = "partial", physicalSymptoms = "pain,bloating", moods = "irritable", energy = "low", fatigue = "high",
        sleepQuality = "poor", sleepHours = 5.5f, stress = "high", focusDifficulty = true, discharge = "creamy",
        dischargeConcerns = "none", intimacyLogged = true, intimacyProtected = "yes", desire = "low", intimacyDiscomfort = false,
        bbtCelsius = 36.7f, ovulationTest = "neg", pregnancyTest = "negative", pregnancyTestEpochDay = day,
        medicationNote = "ibuprofen 400", weightKg = 61.2f, clots = "sometimes", foodCravings = "chocolate,salty",
        note = "یادداشت فارسی ✓", createdAt = t0, updatedAt = updated
    )

    private suspend fun seed() {
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, typicalCycleLength = 30, typicalBleedLength = 6,
            regularCycles = true, hiddenBodyCategories = "acne", lockEnabled = true, createdAt = t0, updatedAt = t0 + 1))
        db.periodDao().upsert(PeriodEventEntity(startEpochDay = today - 2, endEpochDay = null, stillOngoing = true, note = "شروع", createdAt = t0, updatedAt = t0 + 2))
        db.periodDao().upsert(PeriodEventEntity(startEpochDay = today - 32, endEpochDay = today - 27, createdAt = t0, updatedAt = t0 + 3))
        db.dailyLogDao().upsert(fullLog(today, t0 + 4))
        db.dailyLogDao().upsert(DailyLogEntity(epochDay = today - 1, bleeding = "heavy", moods = "sad", createdAt = t0, updatedAt = t0 + 5))
        db.dailyLogDao().upsert(DailyLogEntity(epochDay = today - 40, noSymptoms = true, createdAt = t0, updatedAt = t0 + 6))
        db.reminderDao().upsert(ReminderPrefEntity(id = "daily_log", enabled = true, hour = 21, minute = 30))
        db.reminderDao().upsert(ReminderPrefEntity(id = "medication", enabled = false, medicationLabel = "آهن"))
    }

    private fun DailyLogEntity.comparable() = copy(id = 0)
    private fun PeriodEventEntity.comparable() = copy(id = 0)

    @Test fun everyRecordType_roundTrips_deletes_and_restoresAfterReLogin() = runBlocking {
        seed()
        val logsBefore = db.dailyLogDao().getAll().map { it.comparable() }.sortedBy { it.epochDay }
        val periodsBefore = db.periodDao().getAll().map { it.comparable() }.sortedBy { it.startEpochDay }

        // 1) First sync uploads everything (no partner-consent setting any more).
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(setOf("main"), server.live("profile").keys)
        assertEquals(2, server.live("period").size)
        assertEquals(3, server.live("log").size)
        assertEquals(setOf("daily_log", "medication"), server.live("reminder").keys)
        assertTrue(server.live("settings").isEmpty())
        val serverLog = server.live("log")[today.toString()]!!
        assertEquals("یادداشت فارسی ✓", serverLog.get("note").asString)
        assertEquals(true, serverLog.get("intimacyLogged").asBoolean)
        assertEquals(36.7, serverLog.get("bbtCelsius").asDouble, 0.001)
        assertFalse("device-only fields stay on the phone", server.live("profile")["main"]!!.has("lockEnabled"))
        assertEquals(0, sync.pendingCount())

        // 2) Edits and deletes made on this phone reach the server.
        db.dailyLogDao().upsert(db.dailyLogDao().getByDay(today)!!.copy(note = "ویرایش شد", updatedAt = t0 + 100))
        db.dailyLogDao().deleteByDay(today - 1)
        db.periodDao().getAll().first { it.startEpochDay == today - 32 }.let { db.periodDao().delete(it.id) }
        db.reminderDao().upsert(ReminderPrefEntity(id = "daily_log", enabled = false, hour = 8, minute = 15))
        assertEquals(4, sync.pendingCount())
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals("ویرایش شد", server.live("log")[today.toString()]!!.get("note").asString)
        assertNull(server.live("log")[(today - 1).toString()])
        assertTrue(server.rows["log/${today - 1}"]!!.deleted)
        assertTrue(server.rows["period/${today - 32}"]!!.deleted)
        assertEquals(false, server.live("reminder")["daily_log"]!!.get("enabled").asBoolean)

        // 3) A change from another phone comes down; an older write from there loses.
        val other = gson.toJsonTree(DailyLogEntity(epochDay = today - 3, moods = "calm", createdAt = t0, updatedAt = t0 + 200)).asJsonObject
            .apply { remove("id"); remove("updatedAt") }
        server.inject("log", (today - 3).toString(), other, t0 + 200)
        val stale = gson.toJsonTree(fullLog(today, t0)).asJsonObject.apply { remove("id"); remove("updatedAt"); addProperty("note", "old") }
        db.dailyLogDao().upsert(db.dailyLogDao().getByDay(today)!!.copy(note = "تازه‌ترین", updatedAt = t0 + 300))
        server.inject("log", today.toString(), stale, t0 + 50)
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals("calm", db.dailyLogDao().getByDay(today - 3)?.moods)
        assertEquals("تازه‌ترین", db.dailyLogDao().getByDay(today)?.note)
        assertEquals("تازه‌ترین", server.live("log")[today.toString()]!!.get("note").asString)

        // 4) Logout clears the phone; logging in again restores everything from the server.
        val expectedLogs = db.dailyLogDao().getAll().map { it.comparable() }.sortedBy { it.epochDay }
        val expectedPeriods = db.periodDao().getAll().map { it.comparable() }.sortedBy { it.startEpochDay }
        val expectedProfile = db.profileDao().get()!!
        val expectedReminders = db.reminderDao().getAll().sortedBy { it.id }
        sync.clearLocalCache()
        assertTrue(db.dailyLogDao().getAll().isEmpty())
        assertNull(db.profileDao().get())
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(expectedLogs, db.dailyLogDao().getAll().map { it.comparable() }.sortedBy { it.epochDay })
        assertEquals(expectedPeriods, db.periodDao().getAll().map { it.comparable() }.sortedBy { it.startEpochDay })
        assertEquals(expectedReminders, db.reminderDao().getAll().sortedBy { it.id })
        val restored = db.profileDao().get()!!
        assertEquals(expectedProfile.typicalCycleLength, restored.typicalCycleLength)
        assertEquals(expectedProfile.typicalBleedLength, restored.typicalBleedLength)
        assertEquals(expectedProfile.hiddenBodyCategories, restored.hiddenBodyCategories)
        assertEquals(true, restored.onboardingDone)
        assertNotNull("non-null defaults survive a restore", restored.contentVersionShown)
        assertEquals("track_period", restored.goal)
        assertEquals(0, sync.pendingCount())

        // The full log (every field) came back exactly as she saved it.
        assertEquals(logsBefore.first { it.epochDay == today }.copy(note = "تازه‌ترین", updatedAt = t0 + 300),
            db.dailyLogDao().getByDay(today)!!.comparable())
        assertEquals(periodsBefore.first { it.startEpochDay == today - 2 }, db.periodDao().getAll().first().comparable())

        // 5) His view shows every field of the log she saved (what the share endpoint returns).
        val shared = JsonParser.parseString(server.rows["log/$today"]!!.data).asJsonObject.apply { addProperty("epochDay", today) }
        val labels = PartnerLogFormat.lines(shared).associate { it.label to it.value }
        assertEquals("تازه‌ترین", labels["یادداشت"])
        assertEquals("ثبت کرده", labels["رابطه"])
        assertEquals("متوسط", labels["خون‌ریزی"])
        assertEquals("گاهی", labels["لخته"])
        assertTrue(labels.getValue("هوس").contains("شکلات"))
        assertTrue(labels.getValue("شدت درد").contains("۶"))
        assertEquals("ibuprofen 400", labels["دارو"])
        assertTrue(labels.containsKey("وزن") && labels.containsKey("دمای پایهٔ بدن") && labels.containsKey("میل جنسی"))
    }

    @Test fun serverCopyMissingFields_keepsEntityDefaults() = runBlocking {
        // An older app version may have sent fewer fields: defaults must stay valid (no nulls in non-null fields).
        server.inject("log", today.toString(), JsonObject().apply { addProperty("moods", "happy") }, t0)
        server.inject("period", (today - 5).toString(), JsonObject().apply { addProperty("startEpochDay", today - 5) }, t0)
        server.inject("profile", "main", JsonObject().apply { addProperty("typicalCycleLength", 27) }, t0)
        assertEquals(SyncOutcome.Ok, sync.sync())
        val log = db.dailyLogDao().getByDay(today)!!
        assertEquals("happy", log.moods)
        assertFalse(log.intimacyLogged)
        val period = db.periodDao().getAll().single()
        assertEquals(today - 5, period.startEpochDay)
        assertFalse(period.stillOngoing)
        val p = db.profileDao().get()!!
        assertEquals(27, p.typicalCycleLength)
        assertEquals("track_period", p.goal)
        assertEquals("jalali", p.calendarType)
        assertNotNull(p.hiddenBodyCategories)
    }
}
