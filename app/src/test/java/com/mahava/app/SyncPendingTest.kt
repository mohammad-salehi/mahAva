package com.mahava.app

import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mahava.app.data.db.*
import com.mahava.app.network.DataPullDto
import com.mahava.app.network.DataPushDto
import com.mahava.app.network.MahApiException
import com.mahava.app.network.SyncRecordDto
import com.mahava.app.sync.DataSyncRemote
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncRecord
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Same push rules as backend services/mah/data.service.js (keys, fields, equal-time compare). */
class StrictDataServer : DataSyncRemote {
    data class Row(val kind: String, val key: String, val data: String?, val updatedAt: Long, val deleted: Boolean, val serverTs: Long)
    private val gson = Gson()
    val rows = LinkedHashMap<String, Row>()
    val rejected = mutableListOf<String>()
    private var tick = 1000L
    private val keyRules = mapOf(
        "tool" to Regex("^[a-zA-Z0-9_-]{1,64}$"), "profile" to Regex("^main$"), "period" to Regex("^-?\\d{1,7}$"),
        "log" to Regex("^-?\\d{1,7}$"), "reminder" to Regex("^[a-z0-9_]{1,32}$"), "settings" to Regex("^[a-zA-Z0-9_.]{1,48}$"),
        "pregnancy" to Regex("^(main|active|ended_[0-9]{1,12}|[0-9]{1,12})$"), "alert" to Regex("^[a-zA-Z0-9_]{1,64}$"))
    private fun dto(r: Row) = SyncRecordDto(r.kind, r.key, r.data?.let { JsonParser.parseString(it).asJsonObject }, r.deleted, r.updatedAt, r.serverTs)
    private fun check(c: SyncRecord) {
        val bad = when {
            keyRules[c.kind]?.matches(c.key) != true -> "key"
            c.updatedAt < 1_420_070_400_000L -> "time"
            !c.deleted && c.data == null -> "data"
            !c.deleted && c.data!!.size() > 80 -> "fields"
            !c.deleted && c.data!!.entrySet().any { (_, v) -> !v.isJsonNull && (!v.isJsonPrimitive || (v.asJsonPrimitive.isString && v.asString.length > 4000)) } -> "value"
            else -> null
        }
        if (bad != null) { rejected += "${c.kind}/${c.key}:$bad"; throw MahApiException(400, bad) }
    }
    override suspend fun pull(since: Long): DataPullDto {
        val list = if (since <= 0) rows.values.filter { !it.deleted } else rows.values.filter { it.serverTs > since }
        return DataPullDto(now = tick, cursor = tick, more = false, full = since <= 0, records = list.sortedBy { it.serverTs }.map(::dto))
    }
    override suspend fun push(changes: List<SyncRecord>): DataPushDto {
        changes.forEach(::check)
        val conflicts = mutableListOf<SyncRecordDto>()
        for (c in changes) {
            val id = "${c.kind}/${c.key}"
            val e = rows[id]
            val data = if (c.deleted) null else gson.toJson(JsonParser.parseString(gson.toJson(c.data)))
            if (e != null && e.updatedAt > c.updatedAt) { conflicts += dto(e); continue }
            if (e != null && e.updatedAt == c.updatedAt) {
                if (e.data == data && e.deleted == c.deleted) continue
                conflicts += dto(e); continue
            }
            if (e == null && c.deleted) continue
            tick += 1
            rows[id] = Row(c.kind, c.key, data, c.updatedAt, c.deleted, tick)
        }
        return DataPushDto(now = tick, applied = 0, conflicts = conflicts)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncPendingTest {
    private lateinit var app: MahavaApplication
    private lateinit var db: MahavaDatabase
    private lateinit var server: StrictDataServer
    private lateinit var sync: DataSyncRepository
    private val clock = FakeAppClock(LocalDate.of(2026, 10, 8))
    private val today = LocalDate.of(2026, 10, 8).toEpochDay()
    private val t0 = 1_790_000_000_000L

    @Before fun setup() = runBlocking {
        app = RuntimeEnvironment.getApplication() as MahavaApplication
        db = Room.inMemoryDatabaseBuilder(app, MahavaDatabase::class.java).allowMainThreadQueries().build()
        server = StrictDataServer()
        app.prefs.setAccessToken("a"); app.prefs.setRefreshToken("r")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        sync = DataSyncRepository(app, db, app.prefs, app.apiClient, app.authRepository, clock, server)
        sync.resetAll()
    }
    @After fun tearDown() { db.close() }

    private suspend fun pendingIds(): String = sync.pendingIds().joinToString(",")

    @Test fun everyKindSettlesWithNothingPending() = runBlocking {
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, createdAt = t0, updatedAt = t0))
        db.periodDao().upsert(PeriodEventEntity(startEpochDay = today - 3, endEpochDay = null, stillOngoing = true, createdAt = t0, updatedAt = t0))
        db.dailyLogDao().upsert(DailyLogEntity(epochDay = today, moods = "calm", sleepHours = 7.5f, bbtCelsius = 36.7f, weightKg = 61.2f, createdAt = t0, updatedAt = t0))
        db.reminderDao().upsert(ReminderPrefEntity(id = "daily_log", enabled = true, hour = 21, minute = 0))
        db.reminderDao().upsert(ReminderPrefEntity(id = "water", enabled = true))
        db.pregnancyDao().upsert(PregnancyEntity(estimatedDueEpochDay = today + 200, createdAt = t0, updatedAt = t0))
        db.healthAlertDao().upsert(HealthAlertEntity(id = java.util.UUID.randomUUID().toString(), ruleId = "r1", ruleVersion = 1, severity = "info",
            titleFa = "هشدار", plainLanguageExplanationFa = "توضیح", recommendedActionFa = "کار", observedDates = "2026-10-01",
            evidenceReferences = "s1", deduplicationKey = "d1", createdAt = t0, updatedAt = t0))
        db.toolRecordDao().upsert(ToolRecord("doctor_${java.util.UUID.randomUUID()}", "doctor", today, "{\"text\":\"سؤال\"}", t0, t0))
        db.toolRecordDao().upsert(ToolRecord("insight_cycle_length", "insight", null, "{\"title\":\"الگو\",\"v\":1.5}", t0, t0))
        db.toolRecordDao().upsert(ToolRecord("recommendation_$today", "recommendation", today, "{\"a\":\"b\"}", t0, t0))
        repeat(3) { sync.sync() }
        assertEquals("rejected=${server.rejected}", "", pendingIds())
        // Logout + login on the same phone, then a few more rounds.
        sync.clearLocalCache()
        repeat(3) { sync.sync() }
        assertEquals("after relogin rejected=${server.rejected}", "", pendingIds())
        // Typical edits.
        db.healthAlertDao().getAll().first().let { db.healthAlertDao().upsert(it.copy(status = "read", updatedAt = t0 + 50)) }
        db.pregnancyDao().getAll().first().let { db.pregnancyDao().upsert(it.copy(status = "ended", endedAt = t0 + 60, updatedAt = t0 + 60)) }
        repeat(3) { sync.sync() }
        assertEquals("after edits rejected=${server.rejected}", "", pendingIds())
    }
}