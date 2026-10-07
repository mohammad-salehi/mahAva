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
import kotlinx.coroutines.flow.debounce
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
    lateinit var partnerRepository: com.mahava.app.partner.PartnerRepository

    /** Full sync of her own data with the server (server = source of truth, Room = offline cache). */
    lateinit var dataSync: com.mahava.app.sync.DataSyncRepository
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
        partnerRepository = com.mahava.app.partner.PartnerRepository(apiClient, authRepository, prefs, database, repository, clock)
        dataSync = com.mahava.app.sync.DataSyncRepository(this, database, prefs, apiClient, authRepository, clock)
        createNotificationChannel()
        com.mahava.app.partner.PartnerSync.createChannel(this)
        startPartnerSharing()
        startDataSync()
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

    /**
     * Woman's side: when her logs or cycle change and she is actively paired (with consent),
     * upload the new snapshot right away. The periodic worker covers day changes and retries.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startPartnerSharing() {
        appScope.launch {
            try {
                if (partnerRepository.cachedStatus()?.pair != null) {
                    com.mahava.app.partner.PartnerSync.ensurePeriodic(this@MahavaApplication)
                }
            } catch (_: Throwable) { }
        }
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.observeDailyLogs(),
                repository.observeCycleResult()
            ) { logs, cycle -> logs.size.toLong() * 31 + (logs.lastOrNull()?.updatedAt ?: 0L) + cycle.hashCode() }
                .distinctUntilChanged()
                .debounce(2500)
                .collectLatest {
                    try {
                        if (prefs.getAccountRole() != "male" && prefs.getPartnerConsent() &&
                            partnerRepository.isActivelyPaired()
                        ) {
                            com.mahava.app.partner.PartnerSync.syncNow(this@MahavaApplication)
                        }
                    } catch (_: Throwable) { }
                }
        }
    }

    /**
     * Any change to her data (profile, periods, logs, reminders, consent) queues a sync.
     * The job waits for internet, so offline edits go up when the phone is back online.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startDataSync() {
        appScope.launch {
            try { scheduleBackgroundWork(this@MahavaApplication) } catch (_: Throwable) { }
        }
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.observeProfile(),
                repository.observePeriods(),
                repository.observeDailyLogs(),
                repository.observeReminders(),
                prefs.partnerConsent
            ) { p, periods, logs, rem, consent ->
                listOf(p?.updatedAt, periods.size, periods.sumOf { it.updatedAt % 1_000_003 }, logs.size,
                    logs.sumOf { it.updatedAt % 1_000_003 }, rem.hashCode(), consent).hashCode()
            }
                .distinctUntilChanged()
                .debounce(3000)
                .collectLatest {
                    try {
                        if (authRepository.hasSessionTokens() && prefs.getAccountRole() != "male") {
                            com.mahava.app.sync.DataSync.syncNow(this@MahavaApplication)
                        }
                    } catch (_: Throwable) { }
                }
        }
    }

    companion object {
        /**
         * (Re)enqueue the unique background jobs. Called on app start, after boot and after an
         * app update, so partner updates, notifications and the widget keep working with the app closed.
         */
        suspend fun scheduleBackgroundWork(app: MahavaApplication) {
            if (!app.authRepository.hasSessionTokens()) return
            val role = app.prefs.getAccountRole()
            if (role != "male") com.mahava.app.sync.DataSync.ensurePeriodic(app)
            if (app.partnerRepository.cachedStatus()?.pair != null || com.mahava.app.partner.PartnerSync.isAwaiting(app)) {
                com.mahava.app.partner.PartnerSync.ensurePeriodic(app)
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
