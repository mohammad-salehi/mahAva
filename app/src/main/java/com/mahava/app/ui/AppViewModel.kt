package com.mahava.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope as lifecycleViewModelScope
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
import com.mahava.app.billing.toClaimMap
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
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** How long login waits for the restore before it continues in the background. */
private const val RESTORE_WAIT_MS = 25_000L

/** Matches the backend MAH_OTP_RESEND_COOLDOWN_SEC default; a 429 reply overrides it. */
private const val OTP_RESEND_COOLDOWN_SEC = 60

data class AppUiState(
    val ready: Boolean = false,
    val profile: UserProfileEntity? = null,
    val periods: List<PeriodEventEntity> = emptyList(),
    val dailyLogs: List<DailyLogEntity> = emptyList(),
    val cycle: CycleEngineResult? = null,
    val patterns: List<PatternInsight> = emptyList(),
    val reminders: List<ReminderPrefEntity> = emptyList(),
    val message: String? = null,
    val locked: Boolean = false,
    val health: com.mahava.app.health.HealthUiSnapshot? = null
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

    val tools = app.toolsRepository
    /**
     * Every coroutine this view model starts runs here: the normal view-model scope plus
     * [com.mahava.app.util.NetworkGuard], so a failed server call (offline, timeout) can never
     * crash the app. Declared as a member so it takes over every `viewModelScope` use below.
     */
    private val viewModelScope: kotlinx.coroutines.CoroutineScope by lazy {
        kotlinx.coroutines.CoroutineScope(lifecycleViewModelScope.coroutineContext + com.mahava.app.util.NetworkGuard.handler)
    }

    /** The offline demo build never talks to a server. */
    private val offline get() = com.mahava.app.BuildConfig.MAH_DEMO

    val toolRecords = tools.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _message = MutableStateFlow<String?>(null)
    private val _locked = MutableStateFlow(false)

    val isPremium: StateFlow<Boolean> = authRepository.isPremiumEffective
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Pre-login intro already shown (or the user has an account here); null until loaded. */
    val introSeen: StateFlow<Boolean?> = combine(prefs.introSeen, authRepository.isLoggedIn) { seen, logged -> seen || logged }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markIntroSeen() {
        viewModelScope.launch { prefs.setIntroSeen() }
    }

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val checkerJson: StateFlow<String?> = prefs.lastCheckerJson
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Partner ----
    private val partnerRepo get() = app.partnerRepository
    val accountRole: StateFlow<String> = prefs.accountRole
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val sharedFromPartner: StateFlow<Boolean> = prefs.sharedFromPartner
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** In-app notifications (pairing news, her updates…). */
    val notices: StateFlow<List<com.mahava.app.notice.AppNotice>> = com.mahava.app.notice.AppNotices.items
    val partnerStatus: StateFlow<com.mahava.app.network.PartnerStatusDto?> = app.partnerRepository.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val partnerShare: StateFlow<com.mahava.app.network.PartnerShareDto?> = app.partnerRepository.share
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Server sync of her data ----
    val dataSyncStatus: StateFlow<com.mahava.app.sync.DataSyncStatus> = app.dataSync.status


    private val _analyticsRangeMonths = MutableStateFlow(6)
    val analyticsRangeMonths: StateFlow<Int> = _analyticsRangeMonths

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

    private data class HealthCore(
        val pregnancy: com.mahava.app.data.db.PregnancyEntity?,
        val alerts: List<com.mahava.app.data.db.HealthAlertEntity>,
        val rangeMonths: Int
    )

    private val healthCore = combine(
        repo.observeActivePregnancy(),
        repo.observeHealthAlerts(),
        _analyticsRangeMonths
    ) { pregnancy, alerts, range -> HealthCore(pregnancy, alerts, range) }

    val state: StateFlow<AppUiState> = combine(core, repo.observeReminders(), healthCore, _message, _locked) { c, reminders, h, message, locked ->
        val health = com.mahava.app.health.HealthStore.snapshot(
            profile = c.profile,
            periods = c.periods,
            logs = c.logs,
            cycle = c.cycle,
            pregnancy = h.pregnancy,
            persistedAlerts = h.alerts,
            today = clock.today(),
            nowMillis = clock.nowMillis(),
            content = app.scientificContent,
            analyticsRangeMonths = h.rangeMonths
        )
        AppUiState(
            ready = true,
            profile = c.profile,
            periods = c.periods,
            dailyLogs = c.logs,
            cycle = c.cycle,
            patterns = c.patterns,
            reminders = reminders,
            message = message,
            locked = locked,
            health = health
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    private val alertPersistMutex = Mutex()
    private suspend fun persistNewAlerts(alerts: List<com.mahava.app.health.models.HealthAlert>) {
        var added = false
        alertPersistMutex.withLock {
            alerts.forEach { a ->
                if (repo.getHealthAlertByDedupe(a.deduplicationKey) == null) {
                    repo.upsertHealthAlert(com.mahava.app.health.HealthStore.toEntity(a))
                    added = true
                }
            }
        }
        if (added) pushWomanDataToServer(await = false)
    }

    fun setAnalyticsRangeMonths(months: Int) {
        _analyticsRangeMonths.value = months.coerceIn(1, 24)
    }

    fun savePregnancySetup(
        datingMethod: String,
        lmp: LocalDate?,
        clinicianEdd: LocalDate?,
        clinicianConfirmed: Boolean
    ) = viewModelScope.launch {
        try {
            val entity = com.mahava.app.health.PregnancyService.buildEntity(
                datingMethod = datingMethod,
                lmp = lmp,
                clinicianEdd = clinicianEdd,
                clinicianConfirmed = clinicianConfirmed,
                existingId = repo.getActivePregnancy()?.id ?: 0L,
                nowMillis = clock.nowMillis()
            )
            // End any other active rows first.
            repo.getActivePregnancy()?.let { cur ->
                if (cur.id != 0L && cur.id != entity.id) repo.endPregnancy(cur.id, "replaced")
            }
            repo.upsertPregnancy(entity)
            val profile = repo.ensureProfile()
            repo.saveProfile(profile.copy(pregnancyMode = true, fertilityTrackingEnabled = false))
            pushWomanDataToServer(await = true)
            _message.value = "حالت بارداری ذخیره شد"
        } catch (t: Throwable) {
            _message.value = when (t.message) {
                "lmp_required" -> "تاریخ آخرین پریود را وارد کن."
                "clinician_edd_required" -> "تاریخ تخمینی زایمان پزشک را وارد کن."
                else -> "ذخیره نشد. تاریخ‌ها را بررسی کن."
            }
        }
    }

    fun endPregnancyMode(reason: String? = null) = viewModelScope.launch {
        repo.getActivePregnancy()?.let { repo.endPregnancy(it.id, reason) }
        val profile = repo.ensureProfile()
        repo.saveProfile(profile.copy(pregnancyMode = false))
        pushWomanDataToServer(await = true)
        _message.value = "حالت بارداری پایان یافت"
    }

    fun markAlertRead(id: String) = viewModelScope.launch {
        val cur = repo.getHealthAlert(id) ?: return@launch
        repo.upsertHealthAlert(cur.copy(status = "read", updatedAt = clock.nowMillis()))
        pushWomanDataToServer(await = false)
    }

    fun dismissAlert(id: String) = viewModelScope.launch {
        val cur = repo.getHealthAlert(id) ?: return@launch
        repo.upsertHealthAlert(cur.copy(status = "dismissed", updatedAt = clock.nowMillis()))
        pushWomanDataToServer(await = false)
    }

    fun healthInsight(id: String) = app.scientificContent.insightById(id)
    fun fetalWeek(week: Int) = app.scientificContent.fetalWeek(week)
    fun pregnancyMilestones() = app.scientificContent.milestones()
    fun healthFeed() = app.scientificContent.feed()
    fun scienceSource(id: String) = app.scientificContent.sourceById(id)

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                repo.refreshCalendarDay()
            }
        }
        com.mahava.app.notice.AppNotices.load(app)
        checkForceUpdate()

        viewModelScope.launch {
            val p = repo.ensureProfile()
            _locked.value = p.lockEnabled
            reminderScheduler.ensurePeriodic()
            authRepository.loadApiBaseUrl()
            if (offline) return@launch
            refreshAccountFromServer()
            if (authRepository.hasSessionTokens()) {
                // Woman's durable data lives on the server — restore cache on cold start.
                if (prefs.getAccountRole() != "male") {
                    restoreFromServer()
                    com.mahava.app.sync.DataSync.ensurePeriodic(app)
                }
                partnerRefresh()
            }
        }
        viewModelScope.launch {
            state.collect { s ->
                val alerts = s.health?.activeAlerts ?: return@collect
                persistNewAlerts(alerts)
            }
        }
    }

    /**
     * Room is only an offline cache. After any change to the woman's data, push to the server
     * (source of truth). Queues a WorkManager job if the await fails / is offline.
     */
    private suspend fun pushWomanDataToServer(await: Boolean = true) {
        if (prefs.getAccountRole() == "male") return
        if (!authRepository.hasSessionTokens()) return
        com.mahava.app.sync.DataSync.ensurePeriodic(app)
        if (!await) {
            com.mahava.app.sync.DataSync.syncNow(app)
            return
        }
        val outcome = kotlinx.coroutines.withTimeoutOrNull(20_000) { app.dataSync.sync() }
        if (outcome !is com.mahava.app.sync.SyncOutcome.Ok &&
            outcome !is com.mahava.app.sync.SyncOutcome.Skipped
        ) {
            com.mahava.app.sync.DataSync.syncNow(app)
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
        com.mahava.app.partner.MahPush.schedule(app)
        repo.refreshCalendarDay()
        val p = state.value.profile
        val since = backgroundedAt
        backgroundedAt = null
        if (p != null && since != null && p.lockEnabled) {
            val elapsedSec = (android.os.SystemClock.elapsedRealtime() - since) / 1000
            if (elapsedSec >= p.lockTimeoutSeconds) _locked.value = true
        }
        // Soft session check so expired tokens don't keep premium features open.
        if (!offline) viewModelScope.launch {
            checkForceUpdate()
            if (authRepository.hasSessionTokens()) {
                authRepository.verifySessionOrClear()
                refreshInbox()
                partnerRefresh()
                if (prefs.getAccountRole() != "male") {
                    // Pull latest from server, then flush any offline edits.
                    restoreFromServer()
                }
            }
        }
    }

    fun checkForceUpdate() = viewModelScope.launch {
        if (offline) return@launch
        try {
            val cfg = app.apiClient.appConfig(com.mahava.app.BuildConfig.VERSION_CODE)
            val fu = cfg.forceUpdate
            _forceUpdate.value = if (fu != null && fu.mustUpdate) fu else null
            prefs.setYearlyPrice(cfg.yearlyPriceTomans)
        } catch (_: Throwable) {
            // Offline: do not block the app.
        }
    }

    /** Re-reads the admin-managed yearly price (public app-config, plus status when logged in). */
    fun refreshPricing() = viewModelScope.launch {
        if (offline) return@launch
        try {
            val cfg = app.apiClient.appConfig(com.mahava.app.BuildConfig.VERSION_CODE)
            prefs.setYearlyPrice(cfg.yearlyPriceTomans)
        } catch (_: Throwable) {
            // offline: keep the last server price
        }
        try { authRepository.refreshSubscription() } catch (_: Throwable) { }
    }

    fun refreshInbox() = viewModelScope.launch {
        if (offline) return@launch
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
     * Saves onboarding once (profile + periods) and pushes to the server so the next login
     * never asks again. Returns an error line, or null on success.
     */
    suspend fun saveOnboarding(
        profile: UserProfileEntity,
        lastStart: LocalDate?,
        ongoing: Boolean,
        history: List<LocalDate>
    ): String? {
        return try {
            repo.saveProfile(profile.copy(onboardingDone = true, lastPeriodStartEpochDay = lastStart?.toEpochDay()))
            history.filter { lastStart == null || it.isBefore(lastStart) }.distinct().sorted().forEach { d ->
                repo.upsertPeriod(PeriodEventEntity(startEpochDay = d.toEpochDay(), endEpochDay = null, stillOngoing = false, createdAt = 0, updatedAt = 0))
            }
            if (lastStart != null) {
                repo.upsertPeriod(PeriodEventEntity(startEpochDay = lastStart.toEpochDay(), endEpochDay = null, stillOngoing = ongoing, createdAt = 0, updatedAt = 0))
            }
            pushWomanDataToServer(await = true)
            _message.value = "آماده‌ای! اطلاعاتت روی سرور ذخیره شد."
            null
        } catch (t: Throwable) {
            periodErrorFa(t.message)
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
        pushWomanDataToServer(await = true)
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
                pushWomanDataToServer(await = true)
                _message.value = if (!ongoing && end != null) "پایان پریود ذخیره شد" else "پریود ذخیره شد"
            } catch (t: Throwable) {
                _message.value = periodErrorFa(t.message)
            }
        }

    fun deletePeriod(id: Long) = viewModelScope.launch {
        repo.deletePeriod(id)
        pushWomanDataToServer(await = true)
        _message.value = "پریود حذف شد"
    }

    fun saveDailyLog(log: DailyLogEntity) = viewModelScope.launch {
        repo.upsertDailyLog(log)
        pushWomanDataToServer(await = true)
        _message.value = "ذخیره شد"
    }

    private val todaySignalSaveMutex = Mutex()

    /** Keep unrelated daily-log fields when the user taps a signal on Today. */
    fun toggleTodayFeeling(key: String) = viewModelScope.launch {
        todaySignalSaveMutex.withLock {
            val epoch = today().toEpochDay()
            val now = clock.nowMillis()
            val row = repo.getDailyLog(epoch) ?: DailyLogEntity(epochDay = epoch, createdAt = now, updatedAt = now)
            val symptoms = row.physicalSymptoms.orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            val updated = if (key == "happy") row.copy(moods = if (row.moods == "happy") null else "happy")
            else {
                val next = if (key in symptoms) symptoms-key else symptoms+key
                row.copy(physicalSymptoms = next.sorted().joinToString(",").ifBlank { null }, noSymptoms = if (next.isNotEmpty()) false else row.noSymptoms,
                    painScore = if (key == "pain" && key in symptoms) null else row.painScore)
            }
            repo.upsertDailyLog(updated.copy(updatedAt = now))
        }
        pushWomanDataToServer(await = false)
    }

    /** Where today's mood pick ended up (shown under the three options; never a fake success). */
    enum class MoodSave { SYNCED, LOCAL_ONLY, OFFLINE_PENDING, FAILED }

    /**
     * One tap on «خوبم / معمولی‌ام / حال ندارم»: saved into today's daily log (overallFeeling),
     * then pushed to the server. Tapping the same option again keeps it (she changes it by
     * tapping another one).
     */
    suspend fun saveTodayMood(key: String): MoodSave {
        try {
            todaySignalSaveMutex.withLock {
                val epochDay = today().toEpochDay()
                val now = clock.nowMillis()
                val current = repo.getDailyLog(epochDay) ?: DailyLogEntity(epochDay = epochDay, createdAt = now, updatedAt = now)
                repo.upsertDailyLog(current.copy(overallFeeling = key, updatedAt = now))
            }
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { return MoodSave.FAILED }
        if (prefs.getAccountRole() == "male" || !authRepository.hasSessionTokens()) return MoodSave.LOCAL_ONLY
        com.mahava.app.sync.DataSync.ensurePeriodic(app)
        val outcome = try { kotlinx.coroutines.withTimeoutOrNull(15_000) { app.dataSync.sync() } } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { null }
        return when (outcome) {
            is com.mahava.app.sync.SyncOutcome.Ok -> MoodSave.SYNCED
            is com.mahava.app.sync.SyncOutcome.Skipped -> MoodSave.LOCAL_ONLY
            else -> { com.mahava.app.sync.DataSync.syncNow(app); MoodSave.OFFLINE_PENDING }
        }
    }
    /** «رابطه» on Today: same field as the daily log; null clears it. */
    suspend fun saveTodayIntimacy(choice: String?): MoodSave {
        try {
            todaySignalSaveMutex.withLock {
                val epochDay = today().toEpochDay()
                val now = clock.nowMillis()
                val current = repo.getDailyLog(epochDay) ?: DailyLogEntity(epochDay = epochDay, createdAt = now, updatedAt = now)
                repo.upsertDailyLog(com.mahava.app.content.IntimacyLog.apply(current, choice).copy(updatedAt = now))
            }
        } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { return MoodSave.FAILED }
        if (prefs.getAccountRole() == "male" || !authRepository.hasSessionTokens()) return MoodSave.LOCAL_ONLY
        pushWomanDataToServer(await = false)
        return MoodSave.LOCAL_ONLY
    }
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
        // Frequent taps: queue push (debounced WorkManager + observer also cover this).
        pushWomanDataToServer(await = false)
    }

    fun logFor(day: LocalDate): DailyLogEntity? =
        state.value.dailyLogs.find { it.epochDay == day.toEpochDay() }

    fun saveReminder(pref: ReminderPrefEntity) = viewModelScope.launch {
        repo.saveReminder(pref)
        reminderScheduler.ensurePeriodic()
        pushWomanDataToServer(await = true)
    }

    fun deleteAll() = viewModelScope.launch {
        // Wipe server first — woman's data must not remain only on this phone.
        if (prefs.getAccountRole() != "male" && authRepository.hasSessionTokens()) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(15_000) { app.dataSync.sync() }
                authRepository.withToken { app.apiClient.dataWipe(it) }
            } catch (_: Throwable) {
                _message.value = "حذف از سرور انجام نشد؛ اطلاعات حفظ شد. با اتصال اینترنت دوباره تلاش کن."
                return@launch
            }
        }
        app.dataSync.clearLocalCache()
        reminderScheduler.cancelAll()
        repo.ensureProfile()
        _locked.value = false
        _message.value = if (authRepository.hasSessionTokens()) "همهٔ اطلاعات از این گوشی و سرور پاک شد." else "همهٔ اطلاعات پاک شد"
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
            refreshRoleAfterAuth()
            restoreFromServer(userFacing = true)
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "ورود ناموفق بود.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "ورود ناموفق بود.")
        }
    }

    /** Outcome of an OTP send: [error] null means sent; [fixedOtp] only when the server says SMS is off. */
    data class OtpSendResult(
        val error: String? = null,
        val serverMessage: String? = null,
        val fixedOtp: Boolean = false,
        val cooldownSec: Int = 0
    )

    suspend fun requestOtp(phone: String, purpose: String): OtpSendResult {
        return try {
            val res = authRepository.requestOtp(phone.trim(), purpose)
            OtpSendResult(
                serverMessage = res.message?.trim()?.takeIf { m -> m.isNotEmpty() && m.any { it in '\u0600'..'\u06FF' } },
                fixedOtp = res.debugFixedOtp == true,
                cooldownSec = OTP_RESEND_COOLDOWN_SEC
            )
        } catch (e: MahApiException) {
            OtpSendResult(
                error = com.mahava.app.util.FaText.orFallback(e.message, "ارسال کد ناموفق بود."),
                cooldownSec = if (e.status == 429) (e.retryAfterSec ?: OTP_RESEND_COOLDOWN_SEC) else 0
            )
        } catch (t: Throwable) {
            OtpSendResult(error = com.mahava.app.util.FaText.orFallback(t.message, "ارسال کد ناموفق بود."))
        }
    }

    suspend fun verifyOtp(phone: String, purpose: String, otp: String): String? {
        return try {
            val res = authRepository.verifyOtp(phone.trim(), purpose, otp.trim())
            if (res.ok && res.verified) null else com.mahava.app.util.FaText.orFallback(res.message, "کد تأیید نادرست است.")
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "کد تأیید نادرست است.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "کد تأیید نادرست است.")
        }
    }

    suspend fun register(
        phone: String,
        password: String,
        role: String,
        otp: String,
        acceptedPrivacy: Boolean,
        acceptedTerms: Boolean
    ): String? {
        return try {
            authRepository.register(
                phone = phone.trim(),
                password = password,
                role = role,
                otp = otp.trim(),
                acceptedPrivacy = acceptedPrivacy,
                acceptedTerms = acceptedTerms,
                privacyVersion = com.mahava.app.content.LegalDocuments.PRIVACY_VERSION,
                termsVersion = com.mahava.app.content.LegalDocuments.TERMS_VERSION
            )
            refreshRoleAfterAuth()
            restoreFromServer(userFacing = true)
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "ثبت‌نام ناموفق بود.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "ثبت‌نام ناموفق بود.")
        }
    }

    suspend fun resetPassword(phone: String, otp: String, newPassword: String): String? {
        return try {
            authRepository.resetPassword(phone.trim(), otp.trim(), newPassword)
            null
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "بازیابی رمز ناموفق بود.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "بازیابی رمز ناموفق بود.")
        }
    }

    /**
     * After login/register: bring her data back from the server (new phone) and upload what
     * this phone had before (first login: upload once and merge). Never blocks login on failure.
     */
    /** Persistent banner for the restore after login: a short reason and a retry button. */
    data class RestoreNotice(val text: String, val canRetry: Boolean, val busy: Boolean = false)
    private val _restoreNotice = MutableStateFlow<RestoreNotice?>(null)
    val restoreNotice: StateFlow<RestoreNotice?> = _restoreNotice
    fun dismissRestoreNotice() { _restoreNotice.value = null }

    /** One restore at a time: login, resume and cold start share it instead of queueing up. */
    private var restoreJob: kotlinx.coroutines.Deferred<com.mahava.app.sync.SyncOutcome>? = null

    private fun startRestore(): kotlinx.coroutines.Deferred<com.mahava.app.sync.SyncOutcome> =
        restoreJob?.takeIf { it.isActive }
            ?: viewModelScope.async { app.dataSync.sync() }.also { restoreJob = it }

    /**
     * After login/register (and on start/resume): bring her data back from the server and upload
     * what this phone had. Never blocks login for long: a slow link keeps restoring in the
     * background and the banner reports the real result (network vs. a server answer).
     */
    private suspend fun restoreFromServer(userFacing: Boolean = false): String? {
        if (offline || prefs.getAccountRole() == "male") return null
        val job = startRestore()
        val outcome = kotlinx.coroutines.withTimeoutOrNull(RESTORE_WAIT_MS) { job.await() }
        com.mahava.app.sync.DataSync.ensurePeriodic(app)
        if (outcome == null) {
            // Still running (slow link / VPN): not a failure. Report when it really ends.
            if (userFacing) _restoreNotice.value = RestoreNotice(com.mahava.app.sync.SyncMessages.RESTORE_RUNNING, canRetry = false, busy = true)
            viewModelScope.launch {
                val late = try { job.await() } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) {
                    com.mahava.app.sync.SyncOutcome.Failed(t.message, com.mahava.app.sync.SyncFailure.of(t).first)
                }
                reportRestore(late, userFacing, afterWait = true)
            }
        } else {
            reportRestore(outcome, userFacing, afterWait = false)
        }
        return null
    }

    private fun reportRestore(outcome: com.mahava.app.sync.SyncOutcome, userFacing: Boolean, afterWait: Boolean) {
        val msgs = com.mahava.app.sync.SyncMessages
        when (outcome) {
            is com.mahava.app.sync.SyncOutcome.Ok, is com.mahava.app.sync.SyncOutcome.Skipped -> {
                val showedBanner = _restoreNotice.value != null
                _restoreNotice.value = null
                if (afterWait && (userFacing || showedBanner)) _message.value = msgs.RESTORE_DONE
            }
            is com.mahava.app.sync.SyncOutcome.Failed -> {
                if (msgs.autoRetry(outcome)) com.mahava.app.sync.DataSync.syncNow(app)
                when {
                    // Pulled and applied; only uploads wait. Not a restore failure.
                    outcome.restored && outcome.reason == com.mahava.app.sync.SyncFailure.REJECTED -> {
                        _restoreNotice.value = null
                        if (userFacing) _message.value = msgs.RESTORED_PENDING
                    }
                    // The phone already has her data from an earlier sync: stay quiet on start/resume.
                    outcome.restored && !userFacing -> Unit
                    else -> _restoreNotice.value = RestoreNotice(msgs.restoreFailedFa(outcome), canRetry = true)
                }
            }
        }
    }

    /** Banner button «دوباره امتحان». */
    fun retryRestore() {
        if (restoreJob?.isActive == true) return
        _restoreNotice.value = RestoreNotice("در حال دریافت اطلاعاتت از سرور…", canRetry = false, busy = true)
        viewModelScope.launch { restoreFromServer(userFacing = true) }
    }

    fun refreshSyncPending() = viewModelScope.launch { app.dataSync.refreshPending() }

    /** Manual "sync now" from the account screen. Returns a short Persian result line. */
    suspend fun syncDataNow(): String = when (val r = app.dataSync.sync()) {
        is com.mahava.app.sync.SyncOutcome.Ok -> "اطلاعاتت با سرور همگام شد."
        is com.mahava.app.sync.SyncOutcome.Skipped -> "برای این حساب همگام‌سازی لازم نیست."
        is com.mahava.app.sync.SyncOutcome.Failed -> "همگام نشد: ${com.mahava.app.sync.SyncMessages.reasonFa(r)}."
    }

    suspend fun loadTickets(): List<com.mahava.app.network.MahTicketDto> =
        authRepository.withToken { app.apiClient.listTickets(it) }.tickets.orEmpty()

    suspend fun createTicket(subject: String, body: String): String? = try {
        authRepository.withToken { app.apiClient.createTicket(it, subject, body) }
        null
    } catch (e: MahApiException) {
        com.mahava.app.util.FaText.orFallback(e.message, "ارسال تیکت ناموفق بود.")
    } catch (t: Throwable) {
        com.mahava.app.util.FaText.orFallback(t.message, "ارسال تیکت ناموفق بود.")
    }

    suspend fun replyTicket(id: String, body: String): String? = try {
        authRepository.withToken { app.apiClient.replyTicket(it, id, body) }
        null
    } catch (e: MahApiException) {
        com.mahava.app.util.FaText.orFallback(e.message, "ارسال پاسخ ناموفق بود.")
    } catch (t: Throwable) {
        com.mahava.app.util.FaText.orFallback(t.message, "ارسال پاسخ ناموفق بود.")
    }

    /**
     * Step 1 of logout: one best-effort sync (bounded), then how many changes are still not on
     * the server. The screen warns about them and lets her leave anyway; it never blocks.
     */
    suspend fun unsyncedBeforeLogout(): Int {
        if (com.mahava.app.demo.DemoMode.enabled) return 0 // fake data, nothing to lose
        if (prefs.getAccountRole() == "male") return 0
        // 20 s: a slow mobile link needs more than a few seconds to send a large backlog.
        try { kotlinx.coroutines.withTimeoutOrNull(20_000) { app.dataSync.sync() } } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
        }
        return try { app.dataSync.pendingForLogout() } catch (_: Throwable) { 0 }
    }

    /**
     * Step 2: sign out on this phone. Always works, offline or when the server call fails:
     * tokens are cleared first, the server call is best-effort, and the whole thing runs
     * NonCancellable because clearing the tokens closes the screen that started it (which
     * used to cancel the job before the local copy was cleared).
     */
    suspend fun logoutAccount(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        runCatching { com.mahava.app.partner.PartnerSync.cancel(app) }
        runCatching { com.mahava.app.sync.DataSync.cancel(app) }
        // Counted before the tokens go (afterwards nothing counts as pending). Anything not yet on
        // the server is never deleted: it stays on this phone for the same account.
        val owner = runCatching { prefs.accountUserId.first() }.getOrNull()
        val pending = if (com.mahava.app.demo.DemoMode.enabled || prefs.getAccountRole() == "male") 0
            else runCatching { app.dataSync.pendingCount() }.getOrDefault(1)
        authRepository.logout()
        if (pending > 0 && !owner.isNullOrBlank()) runCatching {
            app.dataSync.holdLocalForAccount(owner)
            reminderScheduler.cancelAll()
            _locked.value = false
            com.mahava.app.notice.AppNotices.clear(app)
        } else runCatching { clearLocalAfterSignOut() }
        if (com.mahava.app.demo.DemoMode.enabled) runCatching { com.mahava.app.demo.DemoMode.onSignedOut(app) }
        null
    }

    /** Demo only: seed the fake account again after a demo logout. */
    suspend fun reenterDemo() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        com.mahava.app.demo.DemoMode.onAppStart(app)
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
            com.mahava.app.util.FaText.orFallback(e.message, "حذف حساب انجام نشد.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "حذف حساب انجام نشد.")
        }
    }

    private suspend fun clearLocalAfterSignOut() {
        app.dataSync.clearLocalCache()
        reminderScheduler.cancelAll()
        _locked.value = false
        repo.ensureProfile()
        com.mahava.app.notice.AppNotices.clear(app)
        com.mahava.app.widget.CycleWidgetUpdater.requestUpdate(app)
    }

    /**
     * Where to go after login/register.
     * - Husband → partner home (no cycle onboarding).
     * - Just registered → onboarding once.
     * - Login → restore profile from server first; onboarding only if the server has none.
     */
    suspend fun homeRouteAfterAuth(justRegistered: Boolean = false): String {
        if (prefs.getAccountRole() == "male") return com.mahava.app.ui.navigation.Routes.PARTNER_HOME
        if (justRegistered) return com.mahava.app.ui.navigation.Routes.ONBOARDING

        // Login path: pull server profile before deciding. Never re-ask if she already finished.
        // Join the restore started by login (if still running) instead of a second full round.
        var outcome = restoreJob?.takeIf { it.isActive }?.let { j ->
            try { j.await() } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { null }
        } ?: app.dataSync.sync()
        if (outcome is com.mahava.app.sync.SyncOutcome.Failed && com.mahava.app.sync.SyncMessages.autoRetry(outcome)) {
            outcome = app.dataSync.sync()
        }
        val profile = repo.ensureProfile()
        if (profile.onboardingDone) return com.mahava.app.ui.navigation.Routes.TODAY

        return when (outcome) {
            is com.mahava.app.sync.SyncOutcome.Ok,
            is com.mahava.app.sync.SyncOutcome.Skipped ->
                // Server really has no completed onboarding (abandoned signup, etc.).
                com.mahava.app.ui.navigation.Routes.ONBOARDING
            is com.mahava.app.sync.SyncOutcome.Failed -> {
                // Offline: do not show the form again (it could overwrite server data later).
                _restoreNotice.value = RestoreNotice(com.mahava.app.sync.SyncMessages.restoreFailedFa(outcome), canRetry = true)
                if (com.mahava.app.sync.SyncMessages.autoRetry(outcome)) com.mahava.app.sync.DataSync.syncNow(app)
                com.mahava.app.ui.navigation.Routes.TODAY
            }
        }
    }

    /** Cold start with a saved session: restore profile so onboarding is not shown by mistake. */
    suspend fun restoreSessionProfileIfNeeded() {
        if (prefs.getAccountRole() == "male") return
        if (!authRepository.hasSessionTokens()) return
        if (repo.ensureProfile().onboardingDone) return
        restoreFromServer(userFacing = true)
    }

    suspend fun refreshAccountFromServer(): Boolean {
        if (offline) return false
        return try {
            val me = authRepository.fetchMe()
            val sub = authRepository.refreshSubscription()
            me != null || sub != null
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun loadPendingDiscount(): com.mahava.app.network.MahDiscountPreviewDto? {
        return try {
            authRepository.withToken { token -> app.apiClient.discountPending(token) }.pending
        } catch (_: Throwable) {
            null
        }
    }

    /** Attach promo code for next purchase, or redeem immediately if 100% off. */
    suspend fun applyDiscountCode(code: String): Pair<String?, com.mahava.app.network.MahDiscountPreviewDto?> {
        return try {
            val attached = authRepository.withToken { token -> app.apiClient.attachDiscount(token, code.trim()) }
            if (attached.freeGrant) {
                authRepository.withToken { token -> app.apiClient.redeemDiscount(token, code.trim()) }
                authRepository.refreshSubscription()
                null to attached.copy(attached = true)
            } else {
                null to attached
            }
        } catch (e: MahApiException) {
            (com.mahava.app.util.FaText.orFallback(e.message, "اعمال کد ناموفق بود.")) to null
        } catch (t: Throwable) {
            (com.mahava.app.util.FaText.orFallback(t.message, "اعمال کد ناموفق بود.")) to null
        }
    }

    suspend fun clearDiscountCode(): String? {
        return try {
            authRepository.withToken { token -> app.apiClient.clearDiscount(token) }
            null
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "حذف کد ناموفق بود.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "حذف کد ناموفق بود.")
        }
    }

    /**
     * Buy yearly subscription via Cafe Bazaar IAB, claim on server, then consume so next year is buyable.
     * Pending discount code on the server is consumed during claim.
     * @return null on success, otherwise a short Persian error.
     */
    suspend fun purchaseYearlyWithBazaar(registry: androidx.activity.result.ActivityResultRegistry): String? {
        if (!authRepository.hasSessionTokens()) return "اول وارد حساب شو."
        if (!com.mahava.app.billing.BazaarBilling.isBazaarInstalled(app)) {
            return com.mahava.app.billing.BazaarBilling.friendlyBazaarError(
                Exception("Bazaar is not installed")
            )
        }
        val billing = com.mahava.app.billing.BazaarBilling(app)
        return try {
            billing.connect().getOrElse {
                return it.message ?: com.mahava.app.billing.BazaarBilling.friendlyBazaarError(it)
            }
            // Recover an owned-but-unconsumed purchase first (e.g. app killed before claim);
            // otherwise Bazaar refuses a new purchase of the same product.
            val owned = billing.queryOwnedYearly().firstOrNull()
            val purchase = (if (owned != null) Result.success(owned) else billing.purchaseYearly(registry)).getOrElse {
                return it.message ?: "خرید انجام نشد."
            }
            authRepository.withToken { token ->
                app.apiClient.claimBazaarPurchase(token, purchase.toClaimMap())
            }
            // Allow repurchase next year (managed product consumed after server grant).
            billing.consume(purchase.purchaseToken)
            authRepository.refreshSubscription()
            null
        } catch (e: MahApiException) {
            com.mahava.app.util.FaText.orFallback(e.message, "فعال‌سازی اشتراک روی سرور ناموفق بود.")
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "خرید ناموفق بود.")
        } finally {
            billing.disconnect()
        }
    }

    /** Re-claim any owned yearly purchases that were not yet applied on the server. */
    suspend fun restoreBazaarPurchases(): String {
        if (!authRepository.hasSessionTokens()) return "اول وارد حساب شو."
        if (!com.mahava.app.billing.BazaarBilling.isBazaarInstalled(app)) {
            return com.mahava.app.billing.BazaarBilling.friendlyBazaarError(
                Exception("Bazaar is not installed")
            )
        }
        val billing = com.mahava.app.billing.BazaarBilling(app)
        return try {
            billing.connect().getOrElse {
                return it.message ?: com.mahava.app.billing.BazaarBilling.friendlyBazaarError(it)
            }
            val owned = billing.queryOwnedYearly()
            if (owned.isEmpty()) return "خرید فعالی در بازار پیدا نشد."
            var claimed = 0
            for (p in owned) {
                try {
                    authRepository.withToken { token ->
                        app.apiClient.claimBazaarPurchase(token, p.toClaimMap())
                    }
                    billing.consume(p.purchaseToken)
                    claimed++
                } catch (_: Throwable) { }
            }
            authRepository.refreshSubscription()
            if (claimed > 0) "اشتراک از خرید بازار بازیابی شد." else "خرید پیدا شد ولی فعال‌سازی تازه لازم نبود."
        } catch (t: Throwable) {
            com.mahava.app.util.FaText.orFallback(t.message, "بازیابی خرید ناموفق بود.")
        } finally {
            billing.disconnect()
        }
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
        com.mahava.app.util.FaText.orFallback(t.message, "خطایی پیش آمد. دوباره تلاش کن.")
    }

    /** Refresh pairing status + (for husband) her shared snapshot. Returns an error message or null. */
    suspend fun partnerRefresh(): String? = if (offline) null else partnerCall {
        val st = partnerRepo.refreshStatus()
        if (st.role.isNotBlank()) prefs.setAccountRole(st.role)
        if (st.pair != null) com.mahava.app.partner.PartnerSync.ensurePeriodic(app)
        if (st.pair?.status == "active") {
            val male = st.pair?.myRole == "male" ||
                st.role == "male" ||
                prefs.getAccountRole() == "male"
            if (male) {
                val share = partnerRepo.fetchShare()
                if (share?.snapshot == null && share?.periods.isNullOrEmpty()) {
                    throw com.mahava.app.network.MahApiException(
                        502,
                        "هنوز چیزی از همسرت نرسیده. اگر پریودش را ثبت کرده، یک بار برنامهٔ ماه را باز کند تا همگام شود؛ بعد این‌جا «به‌روزرسانی» را بزن."
                    )
                }
            } else {
                // Make sure her periods/profile are on the server, then push the cycle snapshot.
                try { app.dataSync.sync() } catch (_: Throwable) { }
                partnerRepo.pushSnapshotIfChanged(force = true)
            }
        }
    }

    fun partnerRefreshAsync() {
        viewModelScope.launch { partnerRefresh() }
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

    /** After login/register: learn this account's role and pairing from the server. */
    private suspend fun refreshRoleAfterAuth() {
        try { partnerRepo.refreshStatus() } catch (_: Throwable) { }
    }

    // ---- In-app notifications ----

    fun markNoticesRead() = com.mahava.app.notice.AppNotices.markAllRead(app)
    fun dismissNoticePopup(id: String) = com.mahava.app.notice.AppNotices.dismissPopup(app, id)
    fun clearNotices() = com.mahava.app.notice.AppNotices.clear(app)
    fun noteInApp(kind: String, title: String, body: String) =
        com.mahava.app.notice.AppNotices.add(app, kind, title, body)

    /**
     * While the app is open: poll the partner feed so requests/approvals show up as in-app
     * dialogs within seconds. Returns how long to wait before the next poll.
     */
    suspend fun partnerForegroundPoll(): Long {
        if (offline) return 60_000
        if (!com.mahava.app.notice.AppNotices.foreground || !authRepository.hasSessionTokens()) return 10_000
        try {
            val poll = partnerRepo.pollChanges()
            com.mahava.app.partner.PartnerSync.process(app, poll)
        } catch (_: Throwable) { }
        val st = try { partnerRepo.cachedStatus() } catch (_: Throwable) { null }
        return when {
            st?.pair?.status == "pending" || com.mahava.app.partner.PartnerSync.isAwaiting(app) -> 5_000
            st?.pair != null -> 30_000
            else -> 60_000
        }
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
