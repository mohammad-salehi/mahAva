package com.mahava.app

import androidx.room.Room
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.sync.DataSync
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncFailure
import com.mahava.app.sync.SyncOutcome
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Background sync must never get stuck: refused items are kept but parked, ties go up, retries are short. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncRecoveryTest {
    private lateinit var app: MahavaApplication
    private lateinit var db: MahavaDatabase
    private lateinit var server: StrictDataServer
    private lateinit var sync: DataSyncRepository
    private val clock = FakeAppClock(LocalDate.of(2026, 10, 8))
    private val today = LocalDate.of(2026, 10, 8).toEpochDay()
    private val t0 = 1_759_000_000_000L

    @Before fun setup() = runBlocking {
        app = RuntimeEnvironment.getApplication() as MahavaApplication
        db = Room.inMemoryDatabaseBuilder(app, MahavaDatabase::class.java).allowMainThreadQueries().build()
        server = StrictDataServer()
        app.prefs.setAccessToken("a"); app.prefs.setRefreshToken("r")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        sync = DataSyncRepository(app, db, app.prefs, app.apiClient, app.authRepository, clock, server)
        sync.resetAll()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, createdAt = t0, updatedAt = t0))
        for (i in 0 until 5) db.dailyLogDao().upsert(DailyLogEntity(epochDay = today - i, moods = "calm", createdAt = t0, updatedAt = t0 + i))
    }

    @After fun tearDown() { db.close() }

    @Test fun workerRetriesOnlyShortTransientFailures() {
        val net = SyncOutcome.Failed("x", SyncFailure.NETWORK)
        assertTrue(DataSync.shouldRetry(net, 0))
        assertTrue(DataSync.shouldRetry(SyncOutcome.Failed("x", SyncFailure.SERVER, 502), 3))
        assertFalse("capped, no multi-hour backoff", DataSync.shouldRetry(net, DataSync.MAX_ATTEMPTS - 1))
        assertFalse(DataSync.shouldRetry(SyncOutcome.Failed("x", SyncFailure.REJECTED, 400, restored = true), 0))
        assertFalse(DataSync.shouldRetry(SyncOutcome.Failed("x", SyncFailure.AUTH, 401), 0))
        assertFalse(DataSync.shouldRetry(SyncOutcome.Failed("x", SyncFailure.UPDATE_REQUIRED, 426), 0))
        assertFalse(DataSync.shouldRetry(SyncOutcome.Ok, 0))
        assertFalse(DataSync.shouldRetry(SyncOutcome.Skipped, 0))
    }

    @Test fun refusedRecord_isKeptAndParked_thenGoesUpAfterAnEdit() = runBlocking {
        val bad = "log/${today - 2}"
        server.refuse = { r -> if (r.id == bad) "value" else null }
        val first = sync.sync()
        assertTrue(first is SyncOutcome.Failed && first.reason == SyncFailure.REJECTED)
        assertEquals(1, sync.pendingCount())
        assertEquals(1, sync.status.value.rejected)
        // Unchanged: not offered again on every round (which kept the old job in backoff).
        val before = server.pushedIds.count { it == bad }
        repeat(3) { sync.sync() }
        assertEquals(before, server.pushedIds.count { it == bad })
        assertNotNull("never deleted from the phone", db.dailyLogDao().getByDay(today - 2))
        // She edits it and the server takes it now: nothing pending.
        server.refuse = { null }
        val row = db.dailyLogDao().getByDay(today - 2)!!
        db.dailyLogDao().upsert(row.copy(moods = "happy", updatedAt = t0 + 100))
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(0, sync.pendingCount())
        assertEquals(0, sync.status.value.rejected)
        assertTrue(server.rows[bad]!!.data!!.contains("happy"))
    }

    @Test fun refusedRecord_isOfferedAgainLater_evenWithoutAnEdit() = runBlocking {
        val bad = "log/${today - 1}"
        server.refuse = { r -> if (r.id == bad) "value" else null }
        sync.sync()
        server.refuse = { null } // e.g. the server rule was fixed
        clock.setNowMillis(clock.nowMillis() + DataSyncRepository.REJECT_RETRY_MS + 1)
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(0, sync.pendingCount())
    }

    @Test fun sameTimeDifferentContent_phoneEditIsNotLost() = runBlocking {
        db.reminderDao().upsert(ReminderPrefEntity(id = "water", enabled = false, hour = 13, minute = 0, updatedAt = t0))
        assertEquals(SyncOutcome.Ok, sync.sync())
        // Changed on the phone without a newer time (old code paths / an added field).
        db.reminderDao().upsert(ReminderPrefEntity(id = "water", enabled = true, hour = 13, minute = 0, updatedAt = t0))
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(0, sync.pendingCount())
        assertTrue("phone kept her edit", db.reminderDao().getAll().first { it.id == "water" }.enabled)
        assertTrue("server has her edit", server.rows["reminder/water"]!!.data!!.contains("\"enabled\":true"))
    }
}
