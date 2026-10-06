package com.mahava.app.data.repo

import com.mahava.app.cycle.CycleEngine
import com.mahava.app.cycle.CycleEngineResult
import com.mahava.app.cycle.PeriodInterval
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.pattern.PatternAnalyzer
import com.mahava.app.pattern.PatternInsight
import com.mahava.app.pattern.PeriodStartPoint
import com.mahava.app.pattern.SymptomLogPoint
import com.mahava.app.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class MahavaRepository(
    private val db: MahavaDatabase,
    private val clock: AppClock,
    private val engine: CycleEngine = CycleEngine()
) {
    fun observeProfile(): Flow<UserProfileEntity?> = db.profileDao().observe()
    fun observePeriods(): Flow<List<PeriodEventEntity>> = db.periodDao().observeAll()
    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = db.dailyLogDao().observeAll()
    fun observeReminders(): Flow<List<ReminderPrefEntity>> = db.reminderDao().observeAll()

    fun observeCycleResult(): Flow<CycleEngineResult> =
        combine(observeProfile(), observePeriods()) { profile, periods ->
            computeCycle(profile, periods)
        }

    fun observePatterns(): Flow<List<PatternInsight>> =
        combine(observePeriods(), observeDailyLogs()) { periods, logs ->
            val starts = periods.map { PeriodStartPoint(it.startEpochDay) }
            val symptoms = logs.flatMap { log ->
                val keys = mutableListOf<String>()
                log.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.let { keys += it }
                if (log.painScore != null && log.painScore > 0) keys += "pain"
                log.moods?.split(',')?.filter { it.isNotBlank() }?.forEach { keys += it }
                keys.map { SymptomLogPoint(log.epochDay, it, log.painScore) }
            }
            PatternAnalyzer.analyze(symptoms, starts)
        }

    fun computeCycle(profile: UserProfileEntity?, periods: List<PeriodEventEntity>): CycleEngineResult {
        val intervals = periods.map {
            PeriodInterval(
                start = LocalDate.ofEpochDay(it.startEpochDay),
                endInclusive = it.endEpochDay?.let { d -> LocalDate.ofEpochDay(d) },
                confirmedByUser = true,
                ongoingFlag = if (it.endEpochDay == null) it.stillOngoing else false
            )
        }
        return engine.compute(
            CycleEngine.Input(
                today = clock.today(),
                periods = intervals,
                typicalCycleLength = profile?.typicalCycleLength,
                typicalBleedLength = profile?.typicalBleedLength,
                regularCycles = profile?.regularCycles,
                hormonalContraception = profile?.hormonalContraception == true,
                postpartumOrBreastfeeding = profile?.postpartumOrBreastfeeding == true,
                perimenopause = profile?.perimenopause == true,
                pregnancyMode = profile?.pregnancyMode == true,
                fertilityTrackingEnabled = profile?.fertilityTrackingEnabled == true || profile?.goal == "ttc"
            )
        )
    }

    suspend fun ensureProfile(): UserProfileEntity {
        val existing = db.profileDao().get()
        if (existing != null) return existing
        val now = clock.nowMillis()
        val p = UserProfileEntity(createdAt = now, updatedAt = now)
        db.profileDao().upsert(p)
        return p
    }

    suspend fun saveProfile(profile: UserProfileEntity) {
        db.profileDao().upsert(profile.copy(updatedAt = clock.nowMillis()))
    }

    suspend fun upsertPeriod(event: PeriodEventEntity): Long {
        val now = clock.nowMillis()
        val today = clock.today().toEpochDay()
        val all = db.periodDao().getAll()
        // Same start day already saved → update it instead of creating a duplicate.
        val sameStart = if (event.id == 0L) all.find { it.startEpochDay == event.startEpochDay } else null
        val base = if (sameStart != null) event.copy(id = sameStart.id, createdAt = sameStart.createdAt, note = event.note ?: sameStart.note) else event
        val withTs = if (base.id == 0L) base.copy(createdAt = now, updatedAt = now) else base.copy(updatedAt = now)
        require(withTs.startEpochDay <= today) { "future_start" }
        if (withTs.endEpochDay != null) {
            require(withTs.endEpochDay >= withTs.startEpochDay) { "end_before_start" }
            require(withTs.endEpochDay <= today) { "future_end" }
            require(!withTs.stillOngoing) { "ongoing_with_end" }
        }
        val id = db.periodDao().upsert(withTs)
        // Only the newest period can still be ongoing: older "ongoing" rows become "end not recorded".
        db.periodDao().getAll()
            .filter { it.startEpochDay < withTs.startEpochDay && it.stillOngoing && it.endEpochDay == null }
            .forEach { db.periodDao().upsert(it.copy(stillOngoing = false, updatedAt = now)) }
        return if (withTs.id != 0L) withTs.id else id
    }

    suspend fun deletePeriod(id: Long) = db.periodDao().delete(id)

    suspend fun upsertDailyLog(log: DailyLogEntity): Long {
        val now = clock.nowMillis()
        val existing = db.dailyLogDao().getByDay(log.epochDay)
        val merged = if (existing == null) {
            log.copy(id = 0, createdAt = now, updatedAt = now)
        } else {
            log.copy(id = existing.id, createdAt = existing.createdAt, updatedAt = now)
        }
        return db.dailyLogDao().upsert(merged)
    }

    suspend fun getDailyLog(epochDay: Long) = db.dailyLogDao().getByDay(epochDay)

    suspend fun saveReminder(pref: ReminderPrefEntity) = db.reminderDao().upsert(pref)

    suspend fun deleteAllData() {
        db.profileDao().clear()
        db.periodDao().clear()
        db.dailyLogDao().clear()
        db.reminderDao().clear()
    }

    suspend fun exportJson(): String {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val map = mapOf(
            "profile" to db.profileDao().get(),
            "periods" to db.periodDao().getAll(),
            "dailyLogs" to db.dailyLogDao().getAll()
        )
        return gson.toJson(map)
    }

    suspend fun exportCsv(): String {
        fun q(v: Any?): String {
            val t = v?.toString() ?: ""
            return if (t.any { it == ',' || it == '"' || it == '\n' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        }
        fun d(epoch: Long?): String = epoch?.let { LocalDate.ofEpochDay(it).toString() } ?: ""
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM so spreadsheet apps read Persian text correctly
        sb.appendLine("type,date,end_date,ongoing,bleeding,pain_0_10,moods,energy,symptoms,discharge,pregnancy_test,note")
        db.periodDao().getAll().forEach {
            sb.appendLine(listOf("period", d(it.startEpochDay), d(it.endEpochDay), it.stillOngoing, "", "", "", "", "", "", "", it.note).joinToString(",") { v -> q(v) })
        }
        db.dailyLogDao().getAll().forEach {
            sb.appendLine(listOf("daily", d(it.epochDay), "", "", it.bleeding, it.painScore, it.moods, it.energy,
                if (it.noSymptoms) "none" else it.physicalSymptoms, it.discharge, it.pregnancyTest, it.note).joinToString(",") { v -> q(v) })
        }
        return sb.toString()
    }
}
