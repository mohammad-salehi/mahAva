package com.mahava.app

import androidx.room.Room
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncFailure
import com.mahava.app.sync.SyncOutcome
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/** The logout warning must count only what is really missing on the server, and logout never loses it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncLogoutTest {
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
        sync.clearLocalCache()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, createdAt = t0, updatedAt = t0))
        for (i in 0 until 40) db.dailyLogDao().upsert(DailyLogEntity(epochDay = today - i, moods = "calm", createdAt = t0, updatedAt = t0 + i))
    }

    @After fun tearDown() { db.close() }

    @Test fun firstRoundCutShort_countsOnlyWhatIsReallyMissing() = runBlocking {
        assertEquals(SyncOutcome.Ok, sync.sync())
        sync.resetAll() // a fresh first round (new login / lost state) with everything already on the server
        val row = db.dailyLogDao().getByDay(today)!!
        db.dailyLogDao().upsert(row.copy(moods = "happy", updatedAt = t0 + 500))
        server.pushError = java.io.IOException("link dropped")
        val r = sync.sync()
        assertTrue(r is SyncOutcome.Failed && r.reason == SyncFailure.NETWORK)
        assertEquals("only her one new edit, not all 41 items", 1, sync.pendingCount())
        server.pushError = null
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(0, sync.pendingCount())
        assertTrue(server.rows["log/$today"]!!.data!!.contains("happy"))
    }

    @Test fun logoutWithPending_keepsData_sameAccountUploadsIt() = runBlocking {
        assertEquals(SyncOutcome.Ok, sync.sync())
        val row = db.dailyLogDao().getByDay(today - 3)!!
        db.dailyLogDao().upsert(row.copy(moods = "sad", updatedAt = t0 + 900))
        assertEquals(1, sync.pendingForLogout())
        sync.holdLocalForAccount("user-1")
        app.prefs.clearAuth()
        assertEquals(0, sync.pendingCount())
        assertNotNull("kept on the phone", db.dailyLogDao().getByDay(today - 3))
        // Same account signs in again.
        app.prefs.setAccessToken("a2"); app.prefs.setRefreshToken("r2")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(0, sync.pendingCount())
        assertNull(sync.heldForAccount())
        assertTrue(server.rows["log/${today - 3}"]!!.data!!.contains("sad"))
    }

    @Test fun keptData_isClearedWhenAnotherAccountSignsIn() = runBlocking {
        assertEquals(SyncOutcome.Ok, sync.sync())
        sync.holdLocalForAccount("user-1")
        app.prefs.clearAuth()
        app.prefs.setAccessToken("b"); app.prefs.setRefreshToken("rb")
        app.prefs.setAccount("09130000000", "user-2"); app.prefs.setAccountRole("female")
        val other = StrictDataServer()
        val sync2 = DataSyncRepository(app, db, app.prefs, app.apiClient, app.authRepository, clock, other)
        sync2.sync()
        assertNull("user-1's logs never reach user-2", db.dailyLogDao().getByDay(today))
        assertTrue(other.rows.keys.none { it.startsWith("log/") })
    }
}
