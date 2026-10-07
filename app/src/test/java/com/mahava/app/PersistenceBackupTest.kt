package com.mahava.app

import androidx.room.Room
import com.mahava.app.data.backup.BackupManager
import com.mahava.app.data.backup.RestoreResult
import com.mahava.app.data.crypto.CryptoManager
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.data.repo.MahavaRepository
import com.mahava.app.util.FakeAppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersistenceBackupTest {
    private lateinit var db: MahavaDatabase
    private lateinit var repo: MahavaRepository
    private val clock = FakeAppClock(LocalDate.of(2026, 10, 6))

    @Before fun setup() {
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, MahavaDatabase::class.java).allowMainThreadQueries().build()
        repo = MahavaRepository(db, clock)
    }

    @After fun tearDown() { db.close() }

    @Test fun noDuplicateDailyLogs() = runBlocking {
        val day = clock.today().toEpochDay()
        val now = clock.nowMillis()
        repo.upsertDailyLog(DailyLogEntity(epochDay = day, bleeding = "light", note = "a", createdAt = now, updatedAt = now))
        repo.upsertDailyLog(DailyLogEntity(epochDay = day, bleeding = "medium", note = "b", createdAt = now, updatedAt = now))
        val all = db.dailyLogDao().getAll()
        assertEquals(1, all.size)
        assertEquals("medium", all[0].bleeding)
        assertEquals("b", all[0].note)
    }

    @Test fun persistenceAcrossReopen() = runBlocking {
        val now = clock.nowMillis()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, goal = "track_period", createdAt = now, updatedAt = now))
        db.periodDao().upsert(PeriodEventEntity(startEpochDay = clock.today().minusDays(10).toEpochDay(), createdAt = now, updatedAt = now))
        assertEquals(true, db.profileDao().get()?.onboardingDone)
        assertEquals(1, db.periodDao().getAll().size)
    }

    @Test fun backupRoundTrip() = runBlocking {
        val now = clock.nowMillis()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, goal = "body_awareness", createdAt = now, updatedAt = now))
        db.periodDao().upsert(PeriodEventEntity(startEpochDay = 100, endEpochDay = 104, createdAt = now, updatedAt = now))
        val bm = BackupManager(db, CryptoManager())
        val blob = bm.exportEncrypted("secret12".toCharArray(), now)
        db.profileDao().clear(); db.periodDao().clear()
        assertNull(db.profileDao().get())
        val result = bm.restoreEncrypted("secret12".toCharArray(), blob)
        assertEquals(RestoreResult.Success, result)
        assertEquals("body_awareness", db.profileDao().get()?.goal)
        assertEquals(1, db.periodDao().getAll().size)
    }

    @Test fun wrongPasswordLeavesData() = runBlocking {
        val now = clock.nowMillis()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, goal = "keep", createdAt = now, updatedAt = now))
        val bm = BackupManager(db, CryptoManager())
        val blob = bm.exportEncrypted("rightpass".toCharArray(), now)
        val before = db.profileDao().get()?.goal
        val result = bm.restoreEncrypted("wrongpass".toCharArray(), blob)
        assertTrue(result is RestoreResult.Failed)
        assertEquals(before, db.profileDao().get()?.goal)
    }

    @Test fun corruptFileLeavesData() = runBlocking {
        val now = clock.nowMillis()
        db.profileDao().upsert(UserProfileEntity(onboardingDone = true, goal = "safe", createdAt = now, updatedAt = now))
        val bm = BackupManager(db, CryptoManager())
        val result = bm.restoreEncrypted("x".toCharArray(), byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(result is RestoreResult.Failed)
        assertEquals("safe", db.profileDao().get()?.goal)
    }

    @Test fun futurePeriodRejected() = runBlocking {
        try {
            repo.upsertPeriod(PeriodEventEntity(startEpochDay = clock.today().plusDays(2).toEpochDay(), createdAt = 1, updatedAt = 1))
            fail("expected")
        } catch (_: IllegalArgumentException) {}
    }
}

class CryptoBackupUnitTest {
    @Test fun aesGcmRoundTripStandalone() {
        // PBKDF2 path does not need AndroidKeyStore
        val crypto = CryptoManager()
        val plain = "سلام ماه".toByteArray(Charsets.UTF_8)
        val blob = crypto.encryptBackup("password99".toCharArray(), plain)
        val out = crypto.decryptBackup("password99".toCharArray(), blob)
        assertArrayEquals(plain, out)
    }
}
