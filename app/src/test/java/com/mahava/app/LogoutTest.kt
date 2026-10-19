package com.mahava.app

import com.mahava.app.data.db.DailyLogEntity
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

/** Logout must always sign out on the phone, even offline and with changes not yet on the server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ToolsTestApplication::class)
class LogoutTest {
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
    private suspend fun signIn() {
        app.prefs.setAccessToken("a"); app.prefs.setRefreshToken("r")
        app.prefs.setAccount("09120000000", "user-1"); app.prefs.setAccountRole("female")
        app.prefs.setServerSubscription(true, "yearly", null, null)
        // Nothing listens here: every server call fails at once, like being offline.
        app.apiClient.setBaseUrl("http://127.0.0.1:9")
    }

    @Test fun authLogoutClearsTokensEvenWhenServerIsUnreachable() = runBlocking {
        signIn()
        app.authRepository.logout()
        assertFalse(app.prefs.hasSessionTokensNow())
        assertFalse(app.authRepository.isPremiumEffectiveNow())
    }

    @Test fun offlineLogoutWithUnsyncedChangesWarnsSignsOutAndKeepsUnsyncedData() = runBlocking {
        signIn()
        val day = app.clock.today().toEpochDay()
        app.repository.upsertDailyLog(DailyLogEntity(epochDay = day, note = "not on server yet", createdAt = 1, updatedAt = 1))
        val vm = AppViewModelFactory(app).create(AppViewModel::class.java)
        assertTrue("unsynced change should be reported", vm.unsyncedBeforeLogout() > 0)
        assertNull(vm.logoutAccount())
        assertFalse(app.prefs.hasSessionTokensNow())
        // Not on the server yet, so it is never deleted: kept on the phone for the same account.
        assertFalse(app.database.dailyLogDao().getAll().isEmpty())
        assertEquals("user-1", app.dataSync.heldForAccount())
    }
}
