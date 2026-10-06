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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val app: MahavaApplication
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    private val _locked = MutableStateFlow(false)

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
        viewModelScope.launch {
            val p = repo.ensureProfile()
            _locked.value = p.lockEnabled
            reminderScheduler.ensurePeriodic()
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
        val p = state.value.profile ?: return
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (!p.lockEnabled) return
        val elapsedSec = (android.os.SystemClock.elapsedRealtime() - since) / 1000
        if (elapsedSec >= p.lockTimeoutSeconds) _locked.value = true
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
            _message.value = "آماده‌ای! اطلاعاتت روی گوشی ذخیره شد."
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
        _message.value = "همهٔ اطلاعات پاک شد"
    }

    suspend fun exportBackup(password: CharArray): ByteArray =
        backupManager.exportEncrypted(password, clock.nowMillis())

    suspend fun restoreBackup(password: CharArray, bytes: ByteArray): RestoreResult =
        backupManager.restoreEncrypted(password, bytes)

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
