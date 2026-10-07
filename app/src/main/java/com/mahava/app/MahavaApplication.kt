package com.mahava.app

import android.app.Application
import android.content.res.Configuration
import java.util.Locale
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.mahava.app.content.ContentRepository
import com.mahava.app.data.backup.BackupManager
import com.mahava.app.data.crypto.CryptoManager
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.prefs.UserPreferences
import com.mahava.app.data.auth.AuthRepository
import com.mahava.app.network.MahApiClient
import com.mahava.app.data.repo.MahavaRepository
import com.mahava.app.reminders.ReminderScheduler
import com.mahava.app.util.AppClock
import com.mahava.app.util.SystemAppClock
import com.mahava.app.widget.CycleWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

open class MahavaApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /**
     * Test hook for the system file picker (SAF). Instrumented tests set this so backup/restore/export
     * can run without the system picker UI. Arguments: (mode "create"|"open", suggested file name).
     * Always null in normal use.
     */
    @Volatile var documentPickerOverride: ((String, String) -> android.net.Uri?)? = null

    lateinit var clock: AppClock
        private set
    lateinit var database: MahavaDatabase
        private set
    lateinit var repository: MahavaRepository
        private set
    lateinit var contentRepository: ContentRepository
        private set
    lateinit var backupManager: BackupManager
        private set
    lateinit var prefs: UserPreferences
        private set
    lateinit var apiClient: MahApiClient
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set
    lateinit var cryptoManager: CryptoManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Prefer Persian resources/layout when available
        val fa = Locale("fa")
        Locale.setDefault(fa)
        val config = Configuration(resources.configuration)
        config.setLocale(fa)
        config.setLayoutDirection(fa)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        clock = createClock()
        database = createDatabase()
        repository = MahavaRepository(database, clock)
        contentRepository = ContentRepository(this)
        cryptoManager = CryptoManager()
        backupManager = BackupManager(database, cryptoManager)
        prefs = UserPreferences(this)
        apiClient = MahApiClient()
        authRepository = AuthRepository(apiClient, prefs)
        reminderScheduler = ReminderScheduler(this)
        createNotificationChannel()
        appScope.launch {
            repository.observeCycleResult().collectLatest {
                CycleWidgetUpdater.requestUpdate(this@MahavaApplication)
            }
        }
        appScope.launch {
            authRepository.isPremiumEffective.distinctUntilChanged().collectLatest {
                CycleWidgetUpdater.requestUpdate(this@MahavaApplication)
            }
        }
    }

    protected open fun createClock(): AppClock = SystemAppClock()
    protected open fun createDatabase(): MahavaDatabase = MahavaDatabase.build(this)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }
}
