package com.mahava.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mahava.app.MahavaApplication
import com.mahava.app.content.ContentItem
import com.mahava.app.content.ContentRepository
import com.mahava.app.cycle.CycleEngineResult
import com.mahava.app.data.backup.BackupManager
import com.mahava.app.data.backup.RestoreResult
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.data.repo.MahavaRepository
import com.mahava.app.pattern.PatternInsight
import com.mahava.app.reminders.ReminderScheduler
import com.mahava.app.util.AppClock
import com.google.gson.Gson
import com.mahava.app.pattern.PatternAnalyzer
import com.mahava.app.pattern.SymptomLogPoint
import com.mahava.app.pattern.PeriodStartPoint
import com.mahava.app.pattern.PhaseClusterInsight
import com.mahava.app.pattern.CycleTrendsResult
import com.mahava.app.pattern.CycleTrendsAnalyzer
import com.mahava.app.network.MahApiException
import com.mahava.app.insight.DailyInsightEngine
import com.mahava.app.insight.DailyInsight
import com.mahava.app.data.prefs.UserPreferences
import com.mahava.app.data.auth.AuthRepository
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.content.PhaseForecastEngine
import com.mahava.app.content.PhaseForecast
import com.mahava.app.checker.CheckerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class AppUiState(
    val ready: Boolean = false,
    val profile: UserProfileEntity? = null,
    val periods: List<PeriodEventEntity> = emptyList(),
    val dailyLogs: List<DailyLogEntity> = emptyList(),
    val cycle: CycleEngineResult? = null,
    val patterns: List<PatternInsight> = emptyList(),
    val reminders: List<ReminderPrefEntity> = emptyList(),
    val message: String? = null,
    val locked: Boolean = false
)

class AppViewModel(
    private val repo: MahavaRepository,
    private val contentRepo: ContentRepository,
    private val backupManager: BackupManager,
    private val clock: AppClock,
    private val reminderScheduler: ReminderScheduler,
    private val app: MahavaApplication,
    private val authRepository: AuthRepository = app.authRepository,
    private val prefs: UserPreferences = app.prefs
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    private val _locked = MutableStateFlow(false)

    val isPremium: StateFlow<Boolean> = authRepository.isPremiumEffective
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _forceUpdate = MutableStateFlow<com.mahava.app.network.MahForceUpdateDto?>(null)
    val forceUpdate: StateFlow<com.mahava.app.network.MahForceUpdateDto?> = _forceUpdate

    private val _inbox = MutableStateFlow<List<com.mahava.app.network.MahInboxItemDto>>(emptyList())
    val inboxItems: StateFlow<List<com.mahava.app.network.MahInboxItemDto>> = _inbox
    val accountPhone: StateFlow<String?> = prefs.accountPhone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val serverHasActiveSubscription: StateFlow<Boolean> = prefs.serverHasActiveSubscription
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val serverPlan: StateFlow<String?> = prefs.serverPlan
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val serverEndsAt: StateFlow<String?> = prefs.serverEndsAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val yearlyPriceTomans: StateFlow<Int> = prefs.yearlyPriceTomans
        .stateIn(viewModelScope, SharingStarted.Eagerly, 585000)
    private val checkerJson: StateFlow<String?> = prefs.lastCheckerJson
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Partner ----
    private val partnerRepo get() = app.partnerRepository
    val accountRole: StateFlow<String> = prefs.accountRole
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val intendedRole: StateFlow<String> = prefs.intendedRole
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val sharedFromPartner: StateFlow<Boolean> = prefs.sharedFromPartner
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val partnerConsent: StateFlow<Boolean> = prefs.partnerConsent
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val partnerStatus: StateFlow<com.mahava.app.network.PartnerStatusDto?> = app.partnerRepository.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val partnerShare: StateFlow<com.mahava.app.network.PartnerShareDto?> = app.partnerRepository.share
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Server sync of her data ----
    val dataSyncStatus: StateFlow<com.mahava.app.sync.DataSyncStatus> = app.dataSync.status


    private data class Core(val profile: UserProfileEntity?, val periods: List<PeriodEventEntity>, val logs: List<DailyLogEntity>, val cycle: CycleEngineResult, val patterns: List<PatternInsight>)
    private val core = combine(
        repo.observeProfile(),
        repo.observePeriods(),
        repo.observeDailyLogs(),
        repo.observeCycleResult(),
        repo.observePatterns()
    ) { profile, periods, logs, cycle, patterns ->
        Core(profile, periods, logs, cycle, patterns)
    }

    val state: StateFlow<AppUiState> = combine(core, repo.observeReminders(), _message, _locked) { c, reminders, message, locked ->
        AppUiState(
            ready = true,
            profile = c.profile,
            periods = c.periods,
            dailyLogs = c.logs,
            cycle = c.cycle,
            patterns = c.patterns,
            reminders = reminders,
            message = message,
            locked = locked
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        checkForceUpdate()

        viewModelScope.launch {
            val p = repo.ensureProfile()
            _locked.value = p.lockEnabled
            reminderScheduler.ensurePeriodic()
            authRepository.loadApiBaseUrl()
            refreshAccountFromServer()
            if (authRepository.hasSessionTokens()) partnerRefresh()
        }
    }

    fun clearMessage() { _message.value = null }
    fun unlock() { _locked.value = false }
    private var backgroundedAt: Long? = null

    /** Called from Activity.onStop. */
    fun onAppBackgrounded() {
        // While locked (e.g. the system PIN screen is open for unlocking) don't restart the timer.
        if (_locked.value) return
        backgroundedAt = android.os.SystemClock.elapsedRealtime()
    }

    /** Called from Activity.onStart: lock again when the app was away longer than the chosen timeout. */
    fun onAppForegrounded() {
        val p = state.value.profile
        val since = backgroundedAt
        backgroundedAt = null
        if (p != null && since != null && p.lockEnabled) {
            val elapsedSec = (android.os.SystemClock.elapsedRealtime() - since) / 1000
            if (elapsedSec >= p.lockTimeoutSeconds) _locked.value = true
        }
        // Soft session check so expired tokens don't keep premium features open.
        viewModelScope.launch {
            checkForceUpdate()
            if (authRepository.hasSessionTokens()) {
                authRepository.verifySessionOrClear()
                refreshInbox()
                partnerRefresh()
                if (prefs.getAccountRole() != "male") com.mahava.app.sync.DataSync.syncNow(app)
            }
        }
    }

    fun checkForceUpdate() = viewModelScope.launch {
        try {
            val cfg = app.apiClient.appConfig(com.mahava.app.BuildConfig.VERSION_CODE)
            val fu = cfg.forceUpdate
            _forceUpdate.value = if (fu != null && fu.mustUpdate) fu else null
        } catch (_: Throwable) {
            // Offline: do not block the app.
        }
    }

    fun refreshInbox() = viewModelScope.launch {
        try {
            val token = prefs.getAccessToken() ?: return@launch
            val res = app.apiClient.inbox(token)
            _inbox.value = res.items
        } catch (_: Throwable) {
            /* ignore */
        }
    }

    fun dismissInboxItem(id: String) = viewModelScope.launch {
        try {
            val token = prefs.getAccessToken() ?: return@launch
            app.apiClient.dismissInbox(token, id)
            _inbox.value = _inbox.value.filterNot { it.id == id }
        } catch (_: Throwable) { }
    }

    fun relockIfNeeded() {
        val p = state.value.profile
        if (p?.lockEnabled == true) _locked.value = true
    }

    fun contentVersion(): String = contentRepo.load().contentVersion
    fun contentStatus(): String = contentRepo.load().medicalReviewStatus
    fun bodyItems(category: String): List<ContentItem> {
        val s = state.value
        val hidden = s.profile?.hiddenBodyCategories?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        return contentRepo.visibleItems(
            category = category,
            phase = s.cycle?.phase ?: com.mahava.app.cycle.CyclePhase.UNKNOWN,
            pregnancyMode = s.profile?.pregnancyMode == true,
            fertilityEnabled = s.profile?.fertilityTrackingEnabled == true || s.profile?.goal == "ttc",
            cycleDay = s.cycle?.cycleDay,
            hiddenCategories = hidden
        )
    }
    fun allVisibleContent(): List<ContentItem> {
        val s = state.value
        val hidden = s.profile?.hiddenBodyCategories?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        return contentRepo.visibleItems(
            phase = s.cycle?.phase ?: com.mahava.app.cycle.CyclePhase.UNKNOWN,
            pregnancyMode = s.profile?.pregnancyMode == true,
            fertilityEnabled = s.profile?.fertilityTrackingEnabled == true || s.profile?.goal == "ttc",
            cycleDay = s.cycle?.cycleDay,
            hiddenCategories = hidden
        )
    }
    fun contentItem(id: String) = contentRepo.item(id)

    /** The phase items (one per sub-window), in cycle order, for browsing. Hidden in restricted contexts. */
    fun phaseItems(): List<ContentItem> {
        val s = state.value
        val r = s.cycle?.restriction
        if (r != null && r in setOf(
                com.mahava.app.cycle.PredictionRestriction.HORMONAL_CONTRACEPTION,
                com.mahava.app.cycle.PredictionRestriction.PREGNANCY_MODE
            )) return emptyList()
        val order = com.mahava.app.cycle.CycleSubWindow.entries.map { "sw_${it.id}" }
        return contentRepo.load().items.filter { it.isPhase && it.id != "sw_unknown" }.sortedBy { order.indexOf(it.id) }
    }

    fun dayContext(): com.mahava.app.cycle.CycleDayContext {
        val c = state.value.cycle
        return if (c != null) com.mahava.app.cycle.CycleDayContextResolver.resolve(c)
        else com.mahava.app.cycle.CycleDayContext(
            com.mahava.app.cycle.CycleSubWindow.UNKNOWN, null, null, null, null, "چرخه آماده نیست."
        )
    }

    fun phaseTodaySelection(): com.mahava.app.content.PhaseContentSelection {
        val c = state.value.cycle
        val ctx = dayContext()
        val restriction = c?.restriction ?: com.mahava.app.cycle.PredictionRestriction.INSUFFICIENT_DATA
        return contentRepo.selectPhaseForToday(ctx, restriction)
    }

    fun careToday(): com.mahava.app.content.CareSelection {
        val c = state.value.cycle
        val ctx = dayContext()
        val restriction = c?.restriction ?: com.mahava.app.cycle.PredictionRestriction.INSUFFICIENT_DATA
        return contentRepo.selectCareForToday(ctx, restriction, logFor(today()))
    }

    fun phaseHistory(subWindowId: String): com.mahava.app.content.PhaseHistorySummary {
        val sw = com.mahava.app.cycle.CycleSubWindow.fromId(subWindowId) ?: com.mahava.app.cycle.CycleSubWindow.UNKNOWN
        val s = state.value
        return com.mahava.app.content.PhaseHistory.summarize(sw, s.periods, s.dailyLogs)
    }

    fun sourceById(id: String) = contentRepo.sourceById(id)

    /** Short line under the next-period estimate, in plain words. */
    fun predictionBasisShortFa(): String {
        val c = state.value.cycle ?: return ""
        val pred = c.prediction
        return if (pred is com.mahava.app.cycle.PredictionKind.Estimate) {
            if (pred.cyclesUsed >= 2) "بر اساس ${com.mahava.app.util.PersianDigits.toPersian(pred.cyclesUsed)} چرخه‌ای که ثبت کرده‌ای"
            else "بر اساس طول چرخه‌ای که خودت وارد کرده‌ای"
        } else c.basisDescriptionFa
    }

    fun today(): LocalDate = clock.today()

    /**
     * Saves onboarding in one coroutine so profile and periods are written in order.
     * [lastStart] null = user does not remember. [ongoing] = bleeding has not ended yet.
     * Earlier starts in [history] are saved with an unknown end (never a fabricated end date).
     */
    fun saveOnboarding(profile: UserProfileEntity, lastStart: LocalDate?, ongoing: Boolean, history: List<LocalDate>) = viewModelScope.launch {
        repo.saveProfile(profile.copy(onboardingDone = true, lastPeriodStartEpochDay = lastStart?.toEpochDay()))
        try {
            history.filter { lastStart == null || it.isBefore(lastStart) }.distinct().sorted().forEach { d ->
                repo.upsertPeriod(PeriodEventEntity(startEpochDay = d.toEpochDay(), endEpochDay = null, stillOngoing = false, createdAt = 0, updatedAt = 0))
            }
            if (lastStart != null) {
                repo.upsertPeriod(PeriodEventEntity(startEpochDay = lastStart.toEpochDay(), endEpochDay = null, stillOngoing = ongoing, createdAt = 0, updatedAt = 0))
            }
            _message.value = "آماده‌ای! اطلاعاتت ذخیره شد."
        } catch (t: Throwable) {
            _message.value = periodErrorFa(t.message)
        }
    }

    private fun periodErrorFa(code: String?): String = when (code) {
        "future_start" -> "تاریخ شروع نمی‌تواند بعد از امروز باشد."
        "end_before_start" -> "روز پایان نمی‌تواند قبل از روز شروع باشد."
        "ongoing_with_end" -> "اگر پریود هنوز ادامه دارد، روز پایان را خالی بگذار."
        "future_end" -> "روز پایان نمی‌تواند بعد از امروز باشد."
        else -> "ذخیره نشد. لطفاً تاریخ‌ها را بررسی کن."
    }

    /** Debug-only sample data (called from the debug deep link). Flagged with qaSampleData and labeled on Today. */
    fun seedSampleData() = viewModelScope.launch {
        if (!com.mahava.app.BuildConfig.DEBUG) return@launch
        val t = today()
        repo.deleteAllData()
        val now = clock.nowMillis()
        repo.saveProfile(
            UserProfileEntity(
                onboardingDone = true, goal = "track_period",
                lastPeriodStartEpochDay = t.minusDays(14).toEpochDay(),
                typicalCycleLength = 28, typicalBleedLength = 5, regularCycles = true,
                fertilityTrackingEnabled = true, qaSampleData = true, createdAt = now, updatedAt = now
            )
        )
        listOf(70L, 42L, 14L).forEach { back ->
            val s = t.minusDays(back)
            repo.upsertPeriod(PeriodEventEntity(startEpochDay = s.toEpochDay(), endEpochDay = s.plusDays(4).toEpochDay(), stillOngoing = false, createdAt = now, updatedAt = now))
        }
        listOf(
            DailyLogEntity(epochDay = t.minusDays(45).toEpochDay(), physicalSymptoms = "bloating,headache", moods = "irritable", energy = "low", createdAt = now, updatedAt = now),
            DailyLogEntity(epochDay = t.minusDays(17).toEpochDay(), physicalSymptoms = "bloating,breast_tenderness", moods = "anxious", painScore = 3, createdAt = now, updatedAt = now),
            DailyLogEntity(epochDay = t.minusDays(13).toEpochDay(), bleeding = "heavy", painScore = 6, physicalSymptoms = "pain", createdAt = now, updatedAt = now)
        ).forEach { repo.upsertDailyLog(it) }
        _message.value = "دادهٔ نمونه برای آزمایش ساخته شد"
    }

    fun updateProfile(transform: (UserProfileEntity) -> UserProfileEntity) = viewModelScope.launch {
        val cur = repo.ensureProfile()
        repo.saveProfile(transform(cur))
    }

    fun savePeriod(start: LocalDate, end: LocalDate?, ongoing: Boolean, note: String? = null, existingId: Long = 0) =
        viewModelScope.launch {
            try {
                val prev = state.value.periods.find { it.id == existingId && existingId != 0L }
                repo.upsertPeriod(
                    PeriodEventEntity(
                        id = existingId,
                        startEpochDay = start.toEpochDay(),
                        endEpochDay = if (ongoing) null else end?.toEpochDay(),
                        stillOngoing = ongoing,
                        note = note ?: prev?.note,
                        createdAt = prev?.createdAt ?: clock.nowMillis(),
                        updatedAt = clock.nowMillis()
                    )
                )
                _message.value = if (!ongoing && end != null) "پایان پریود ذخیره شد" else "پریود ذخیره شد"
            } catch (t: Throwable) {
                _message.value = periodErrorFa(t.message)
            }
        }

    fun deletePeriod(id: Long) = viewModelScope.launch {
        repo.deletePeriod(id)
        _message.value = "پریود حذف شد"
    }

    fun saveDailyLog(log: DailyLogEntity) = viewModelScope.launch {
        repo.upsertDailyLog(log)
        _message.value = "ذخیره شد"
    }

    private val todaySignalSaveMutex = Mutex()

    /** Keep unrelated daily-log fields when the user taps a signal on Today. */
    fun saveTodaySignals(food: Set<String>, body: Set<String>, mood: String?) = viewModelScope.launch {
        todaySignalSaveMutex.withLock {
            val epochDay = today().toEpochDay()
            val now = clock.nowMillis()
            val current = repo.getDailyLog(epochDay)
                ?: DailyLogEntity(epochDay = epochDay, createdAt = now, updatedAt = now)
            repo.upsertDailyLog(current.copy(
                foodCravings = food.sorted().joinToString(",").ifBlank { null },
                physicalSymptoms = body.sorted().joinToString(",").ifBlank { null },
                moods = mood,
                noSymptoms = if (body.isNotEmpty()) false else current.noSymptoms,
                updatedAt = now
            ))
        }
    }

    fun logFor(day: LocalDate): DailyLogEntity? =
        state.value.dailyLogs.find { it.epochDay == day.toEpochDay() }

    fun saveReminder(pref: ReminderPrefEntity) = viewModelScope.launch {
        repo.saveReminder(pref)
        reminderScheduler.ensurePeriodic()
    }

    fun deleteAll() = viewModelScope.launch {
        repo.deleteAllData()
        reminderScheduler.cancelAll()
        repo.ensureProfile()
        _locked.value = false
        // The sync job sends the deletions to the server too (queued until online).
        _message.value = if (authRepository.hasSessionTokens()) "همهٔ اطلاعات پاک شد؛ از سرور هم پاک می‌شود." else "همهٔ اطلاعات پاک شد"
    }

    suspend fun exportBackup(password: CharArray): ByteArray =
        backupManager.exportEncrypted(password, clock.nowMillis())

    suspend fun restoreBackup(password: CharArray, bytes: ByteArray): RestoreResult {
        val r = backupManager.restoreEncrypted(password, bytes)
        // A restore is an explicit choice: its contents should win over older server copies.
        if (r is RestoreResult.Success) app.dataSync.markLocalWins()
        return r
    }

    suspend fun exportJson(): String = repo.exportJson()
    suspend fun exportCsv(): String = repo.exportCsv()

    fun writeBytesToUri(uri: Uri, bytes: ByteArray): Boolean {
        return try {
            app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } catch (_: Throwable) { false }
    }

    fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Throwable) { null }
    }

    fun setPremium(unlocked: Boolean) = viewModelScope.launch {
        // Demo unlock only meaningful when already logged in (DEBUG gate in AuthRepository).
        prefs.setPremium(unlocked)
        com.mahava.app.widget.CycleWidgetUpdater.requestUpdate(app)
    }

    /**
     * Call when entering a premium feature. Verifies tokens; on invalid/expired clears session
     * so [isPremium] flips false and UI can send user to login.
     * @return true if session still valid.
     */
    fun revalidatePremiumAccess(onNeedLogin: () -> Unit = {}) = viewModelScope.launch {
        if (!authRepository.hasSessionTokens()) {
            onNeedLogin()
            return@launch
        }
        val ok = authRepository.verifySessionOrClear()
        if (!ok) onNeedLogin()
    }

    suspend fun login(phone: String, password: String): String? {
        return try {
            authRepository.login(phone.trim(), password)
            applyIntendedRole()
            restoreFromServer()
        } catch (e: MahApiException) {
            e.message ?: "ورود ناموفق بود."
        } catch (t: Throwable) {
            t.message ?: "ورود ناموفق بود."
        }
    }

    suspend fun register(phone: String, password: String, name: String?, role: String? = null): String? {
        return try {
            authRepository.register(phone.trim(), password, name, role)
            if (!role.isNullOrBlank()) prefs.setIntendedRole(role)
            applyIntendedRole()
            restoreFromServer()
        } catch (e: MahApiException) {
            e.message ?: "ثبت‌نام ناموفق بود."
        } catch (t: Throwable) {
            t.message ?: "ثبت‌نام ناموفق بود."
        }
    }

    /**
     * After login/register: bring her data back from the server (new phone) and upload what
     * this phone had before (first login: upload once and merge). Never blocks login on failure.
     */
    private suspend fun restoreFromServer(): String? {
        if (prefs.getAccountRole() == "male") return null
        val outcome = kotlinx.coroutines.withTimeoutOrNull(30_000) { app.dataSync.sync() }
        com.mahava.app.sync.DataSync.ensurePeriodic(app)
        if (outcome !is com.mahava.app.sync.SyncOutcome.Ok && outcome !is com.mahava.app.sync.SyncOutcome.Skipped) {
            com.mahava.app.sync.DataSync.syncNow(app)
            _message.value = "وارد شدی. بازیابی اطلاعات از سرور کامل نشد؛ با وصل شدن اینترنت خودکار انجام می‌شود."
        }
        return null
    }

    fun refreshSyncPending() = viewModelScope.launch { app.dataSync.refreshPending() }

    /** Manual "sync now" from the account screen. Returns a short Persian result line. */
    suspend fun syncDataNow(): String = when (val r = app.dataSync.sync()) {
        is com.mahava.app.sync.SyncOutcome.Ok -> "اطلاعاتت با سرور همگام شد."
        is com.mahava.app.sync.SyncOutcome.Skipped -> "برای این حساب همگام‌سازی لازم نیست."
        is com.mahava.app.sync.SyncOutcome.Failed -> "همگام نشد (${r.message ?: "اینترنت"}). بعداً خودکار دوباره امتحان می‌شود."
    }

    /**
     * Logout clears the phone's copy (the server keeps everything). First tries to send pending
     * changes; if some could not be sent and [force] is false, returns "pending" so the UI can warn.
     */
    suspend fun logoutAccount(force: Boolean = true): String? {
        if (prefs.getAccountRole() != "male") {
            kotlinx.coroutines.withTimeoutOrNull(10_000) { app.dataSync.sync() }
            if (!force && app.dataSync.pendingCount() > 0) return "pending"
        }
        com.mahava.app.partner.PartnerSync.cancel(app)
        com.mahava.app.sync.DataSync.cancel(app)
        authRepository.logout()
        clearLocalAfterSignOut()
        return null
    }

    /** Delete the account on the server (with all synced data), then clear this phone. */
    suspend fun deleteAccount(password: String): String? {
        return try {
            authRepository.withToken { app.apiClient.deleteAccount(it, password) }
            com.mahava.app.partner.PartnerSync.cancel(app)
            com.mahava.app.sync.DataSync.cancel(app)
            prefs.clearAuth()
            clearLocalAfterSignOut()
            null
        } catch (e: MahApiException) {
            e.message ?: "حذف حساب انجام نشد."
        } catch (t: Throwable) {
            t.message ?: "حذف حساب انجام نشد."
        }
    }

    private suspend fun clearLocalAfterSignOut() {
        app.dataSync.clearLocalCache()
        reminderScheduler.cancelAll()
        _locked.value = false
        repo.ensureProfile()
        com.mahava.app.widget.CycleWidgetUpdater.requestUpdate(app)
    }

    suspend fun refreshAccountFromServer(): Boolean {
        val me = authRepository.fetchMe()
        val sub = authRepository.refreshSubscription()
        return me != null || sub != null
    }

    fun phaseForecast(): PhaseForecast {
        val s = state.value
        val cycle = s.cycle
        val tomorrow = today().plusDays(1)
        val ctx = if (cycle != null) {
            CycleDayContextResolver.resolveOn(cycle, tomorrow)
        } else {
            com.mahava.app.cycle.CycleDayContext(
                CycleSubWindow.UNKNOWN, null, null, null, null, ""
            )
        }
        val phaseHint = when (ctx.subWindow) {
            CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE -> CyclePhase.MENSTRUATION
            CycleSubWindow.FOLLICULAR_EARLY, CycleSubWindow.FOLLICULAR_LATE -> CyclePhase.FOLLICULAR
            CycleSubWindow.PERI_OVULATORY -> CyclePhase.OVULATION_WINDOW
            CycleSubWindow.LUTEAL_EARLY, CycleSubWindow.LUTEAL_MID,
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL, CycleSubWindow.LATE_PERIOD -> CyclePhase.LUTEAL
            CycleSubWindow.UNKNOWN -> cycle?.phase ?: CyclePhase.UNKNOWN
        }
        return PhaseForecastEngine.build(ctx.subWindow, phaseHint, s.dailyLogs.takeLast(14))
    }

    fun dailyInsight(): DailyInsight {
        val s = state.value
        return DailyInsightEngine.build(
            dayContext(),
            s.cycle,
            logFor(today()),
            s.dailyLogs.takeLast(14)
        )
    }

    fun phaseClusters(): List<PhaseClusterInsight> {
        val s = state.value
        val starts = s.periods.map { PeriodStartPoint(it.startEpochDay) }
        val symptoms = s.dailyLogs.flatMap { log ->
            val keys = mutableListOf<String>()
            log.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.let { keys += it }
            if (log.painScore != null && log.painScore > 0) keys += "pain"
            log.moods?.split(',')?.filter { it.isNotBlank() }?.forEach { keys += it }
            log.foodCravings?.split(',')?.filter { it.isNotBlank() }?.forEach { keys += "craving_$it" }
            keys.map { SymptomLogPoint(log.epochDay, it, log.painScore) }
        }
        val typical = s.profile?.typicalCycleLength ?: 28
        return PatternAnalyzer.analyzePhaseClusters(symptoms, starts, typical)
    }

    fun cycleTrends(): CycleTrendsResult {
        val s = state.value
        return CycleTrendsAnalyzer.analyze(
            s.periods,
            s.dailyLogs,
            s.profile?.typicalCycleLength ?: 28
        )
    }

    // ---- Personal pattern (computed on the device) ----

    fun personalSignal(kind: String, key: String): com.mahava.app.pattern.PersonalSignal? {
        val s = state.value
        return com.mahava.app.pattern.PersonalPatternEngine.forSignal(kind, key, s.periods, s.dailyLogs)
    }

    fun personalForPhase(phaseGroup: String): List<com.mahava.app.pattern.PersonalSignal> {
        val s = state.value
        return com.mahava.app.pattern.PersonalPatternEngine.forPhase(phaseGroup, s.periods, s.dailyLogs)
    }

    fun personalCycleSummary(): com.mahava.app.pattern.PersonalCycleSummary? =
        com.mahava.app.pattern.PersonalPatternEngine.cycleSummary(state.value.periods)

    fun completeCycleCount(): Int =
        com.mahava.app.pattern.PersonalPatternEngine.completeCycleCount(state.value.periods)

    /** Today's phase group with the same honest fallbacks as the Today card. */
    fun todayPhaseGroup(): String {
        val s = state.value
        return com.mahava.app.content.TodaySignalContent.effectivePhaseGroup(
            dayContext().subWindow, s.cycle?.phase,
            hormonalContraception = s.profile?.hormonalContraception == true,
            regularCycles = s.profile?.regularCycles
        )
    }

    // ---- Partner actions. Each returns null on success or a Persian error. ----

    private suspend fun partnerCall(block: suspend () -> Unit): String? = try {
        block(); null
    } catch (e: MahApiException) {
        e.message
    } catch (_: java.io.IOException) {
        "اینترنت در دسترس نیست. دوباره تلاش کن."
    } catch (t: Throwable) {
        t.message ?: "خطایی پیش آمد. دوباره تلاش کن."
    }

    fun partnerRefresh() = viewModelScope.launch {
        partnerCall {
            val st = partnerRepo.refreshStatus()
            if (st.pair != null) com.mahava.app.partner.PartnerSync.ensurePeriodic(app)
            if (st.pair?.status == "active") {
                if (st.role == "male") partnerRepo.fetchShare() else partnerRepo.pushSnapshotIfChanged()
            }
        }
    }

    suspend fun partnerSetRole(role: String): String? = partnerCall { partnerRepo.setRole(role) }

    suspend fun partnerCreateCode(): Pair<com.mahava.app.network.PartnerCodeDto?, String?> {
        var code: com.mahava.app.network.PartnerCodeDto? = null
        val err = partnerCall {
            code = partnerRepo.createCode()
            val ttl = partnerRepo.cachedStatus()?.codeTtlMinutes ?: 30
            com.mahava.app.partner.PartnerSync.awaitRequest(app, ttl + 5)
            com.mahava.app.partner.PartnerSync.syncNow(app)
        }
        return code to err
    }

    suspend fun partnerRedeem(code: String): String? = partnerCall {
        partnerRepo.redeem(code)
        com.mahava.app.partner.PartnerSync.ensurePeriodic(app)
        com.mahava.app.partner.PartnerSync.syncNow(app)
    }

    suspend fun partnerRespond(pairId: String, approve: Boolean): String? = partnerCall {
        partnerRepo.respond(pairId, approve)
        com.mahava.app.partner.PartnerSync.ensurePeriodic(app)
    }

    suspend fun partnerUnpair(): String? = partnerCall {
        partnerRepo.unpair()
        com.mahava.app.partner.PartnerSync.cancel(app)
        com.mahava.app.widget.CycleWidgetUpdater.requestUpdate(app)
    }

    fun setPartnerConsent(v: Boolean) = viewModelScope.launch { prefs.setPartnerConsent(v) }

    /** Onboarding shortcut for the partner (man): no cycle questions, straight to login/register. */
    fun startAsPartner() = viewModelScope.launch {
        prefs.setIntendedRole("male")
        val cur = repo.ensureProfile()
        repo.saveProfile(cur.copy(onboardingDone = true, goal = "partner"))
    }

    fun setIntendedRole(role: String) = viewModelScope.launch { prefs.setIntendedRole(role) }

    /** After login: apply the role chosen before the account existed. */
    private suspend fun applyIntendedRole() {
        try {
            val intended = prefs.intendedRole.first()
            if (intended.isNotBlank() && prefs.getAccountRole().isBlank()) partnerRepo.setRole(intended)
            partnerRepo.refreshStatus()
        } catch (_: Throwable) { }
    }

    fun lastCheckerResult(): CheckerResult? {
        val json = checkerJson.value ?: return null
        return try {
            Gson().fromJson(json, CheckerResult::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    fun saveCheckerResult(result: CheckerResult) = viewModelScope.launch {
        prefs.setLastCheckerJson(Gson().toJson(result))
    }


}

class AppViewModelFactory(private val app: MahavaApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(
            repo = app.repository,
            contentRepo = app.contentRepository,
            backupManager = app.backupManager,
            clock = app.clock,
            reminderScheduler = app.reminderScheduler,
            app = app
        ) as T
    }
}
