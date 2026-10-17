package com.mahava.app

import androidx.room.Room
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.network.MahApiException
import com.mahava.app.sync.DataSyncRepository
import com.mahava.app.sync.SyncFailure
import com.mahava.app.sync.SyncMessages
import com.mahava.app.sync.SyncOutcome
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The restore after login must report the real reason and never flood a slow link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataSyncFailureTest {
    private lateinit var app: MahavaApplication
    private lateinit var db: MahavaDatabase
    private lateinit var server: FakeDataServer
    private lateinit var sync: DataSyncRepository
    private val clock = FakeAppClock(LocalDate.of(2026, 10, 8))
    private val t0 = 1_790_000_000_000L
    private val today = LocalDate.of(2026, 10, 8).toEpochDay()

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
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, createdAt = t0, updatedAt = t0))
        for (i in 0 until 300) {
            db.dailyLogDao().upsert(DailyLogEntity(epochDay = today - i, moods = "calm", note = "n$i", createdAt = t0, updatedAt = t0 + i))
        }
    }

    @After fun tearDown() { db.close() }

    @Test fun oneRejectedRecord_isIsolatedWithFewRequests_andRestoreCountsAsDone() = runBlocking {
        val bad = (today - 17).toString()
        server.failPush = { batch -> if (batch.any { it.kind == "log" && it.key == bad }) MahApiException(400, "متن خیلی طولانی است") else null }
        val r = sync.sync()
        assertTrue(r is SyncOutcome.Failed)
        r as SyncOutcome.Failed
        assertEquals(SyncFailure.REJECTED, r.reason)
        assertTrue("data was pulled and applied", r.restored)
        assertEquals(300, server.live("log").size + 1)
        assertFalse(server.live("log").containsKey(bad))
        // Halving instead of one request per record (the old code sent ~400 here).
        assertTrue("push calls: ${server.pushCalls}", server.pushCalls <= 25)
        assertEquals(1, sync.pendingCount())
    }

    @Test fun rateLimited_stopsAtOnce_withoutRecordByRecordRetries() = runBlocking {
        server.failPush = { MahApiException(429, "درخواست همگام‌سازی زیاد است؛ کمی بعد تلاش کن") }
        val r = sync.sync() as SyncOutcome.Failed
        assertEquals(SyncFailure.RATE_LIMIT, r.reason)
        assertEquals(1, server.pushCalls)
        assertFalse(SyncMessages.autoRetry(r))
        assertTrue(SyncMessages.restoreFailedFa(r).contains("درخواست زیادی"))
    }

    @Test fun serverError_isReportedAsServer_notAsInternet() = runBlocking {
        server.failPull = { MahApiException(500, "Internal error") }
        val r = sync.sync() as SyncOutcome.Failed
        assertEquals(SyncFailure.SERVER, r.reason)
        assertEquals(500, r.httpCode)
        assertFalse(r.restored)
        val text = SyncMessages.restoreFailedFa(r)
        assertTrue(text, text.contains("سرور خطا داد (کد ۵۰۰)"))
        assertFalse(text, text.contains("اینترنت"))
    }

    @Test fun slowLink_isTimeout_andNoInternet_isNetwork() = runBlocking {
        server.failPull = { java.net.SocketTimeoutException("timeout") }
        val slow = sync.sync() as SyncOutcome.Failed
        assertEquals(SyncFailure.TIMEOUT, slow.reason)
        assertTrue(SyncMessages.reasonFa(slow).contains("دیر جواب"))
        server.failPull = { java.net.UnknownHostException("darapfm.ir") }
        val off = sync.sync() as SyncOutcome.Failed
        assertEquals(SyncFailure.NETWORK, off.reason)
        assertTrue(SyncMessages.reasonFa(off).contains("اینترنت"))
        // Once the server answers again, everything goes up and the round is Ok.
        server.failPull = null
        assertEquals(SyncOutcome.Ok, sync.sync())
        assertEquals(300, server.live("log").size)
    }

    @Test fun cdnPageInsteadOfJson_isServerSide() {
        val (reason, _) = SyncFailure.of(com.google.gson.JsonSyntaxException("Expected BEGIN_OBJECT"))
        assertEquals(SyncFailure.SERVER, reason)
        assertEquals(SyncFailure.AUTH, SyncFailure.of(MahApiException(401, "x")).first)
        assertEquals(SyncFailure.UPDATE_REQUIRED, SyncFailure.of(MahApiException(426, "x")).first)
    }
}
