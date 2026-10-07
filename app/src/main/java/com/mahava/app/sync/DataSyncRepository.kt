package com.mahava.app.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.mahava.app.data.auth.AuthRepository
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.ReminderPrefEntity
import com.mahava.app.data.db.UserProfileEntity
import com.mahava.app.data.prefs.UserPreferences
import com.mahava.app.network.MahApiClient
import com.mahava.app.network.SyncRecordDto
import com.mahava.app.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DataSyncStatus(
    val lastOkAt: Long = 0L,
    val pending: Int = 0,
    val running: Boolean = false,
    val lastError: String? = null
)

sealed class SyncOutcome {
    object Ok : SyncOutcome()
    object Skipped : SyncOutcome()
    data class Failed(val message: String?) : SyncOutcome()
}

/** The server side of the sync (real API in the app, an in-memory fake in tests). */
interface DataSyncRemote {
    suspend fun pull(since: Long): com.mahava.app.network.DataPullDto
    suspend fun push(changes: List<SyncRecord>): com.mahava.app.network.DataPushDto
}

class MahDataSyncRemote(private val api: MahApiClient, private val auth: AuthRepository) : DataSyncRemote {
    override suspend fun pull(since: Long) = auth.withToken { api.dataPull(it, since) }
    override suspend fun push(changes: List<SyncRecord>) = auth.withToken { api.dataPush(it, changes) }
}

/**
 * The server is the source of truth for the woman's data; Room is the offline cache.
 * Changes made offline are found by hash comparison and pushed when the network is back
 * (the WorkManager job is network-constrained and retried).
 */
class DataSyncRepository(
    context: Context,
    private val db: MahavaDatabase,
    private val prefs: UserPreferences,
    private val api: MahApiClient,
    private val auth: AuthRepository,
    private val clock: AppClock,
    private val remote: DataSyncRemote = MahDataSyncRemote(api, auth)
) {
    private val sp = context.getSharedPreferences("mahava_data_sync", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutex = Mutex()
    private val _status = MutableStateFlow(DataSyncStatus(lastOkAt = sp.getLong(K_LAST_OK, 0L)))
    val status: StateFlow<DataSyncStatus> = _status

    companion object {
        private const val K_USER = "user"
        private const val K_SYNCED = "synced"
        private const val K_DETECTED = "detected"
        private const val K_CURSOR = "cursor"
        private const val K_INITIAL = "initial_done"
        private const val K_LAST_OK = "last_ok"
        private const val K_LOCAL_WINS = "local_wins"
        private const val BATCH = 400

        /** Device-specific profile fields that never leave the phone. */
        val PROFILE_LOCAL_ONLY = setOf("id", "updatedAt", "lockEnabled", "lockTimeoutSeconds", "qaSampleData", "schemaVersion", "contentVersionShown")

        /** Non-null entity fields: when the server copy lacks one, the default stays. */
        private val NON_NULL_FIELDS = setOf(
            "id", "epochDay", "startEpochDay", "createdAt", "updatedAt", "noSymptoms", "intimacyLogged", "stillOngoing",
            "onboardingDone", "goal", "cycleLengthUnknown", "bleedLengthUnknown", "hormonalContraception",
            "postpartumOrBreastfeeding", "perimenopause", "fertilityTrackingEnabled", "pregnancyMode", "calendarType",
            "lockEnabled", "lockTimeoutSeconds", "privateNotifications", "hiddenBodyCategories", "contentVersionShown",
            "qaSampleData", "schemaVersion", "enabled", "hour", "minute"
        )
    }

    // ---------- local snapshot ----------

    suspend fun collectLocal(): Map<String, SyncRecord> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, SyncRecord>()
        db.profileDao().get()?.let { p ->
            val o = gson.toJsonTree(p).asJsonObject
            PROFILE_LOCAL_ONLY.forEach { o.remove(it) }
            out["profile/main"] = SyncRecord("profile", "main", o, p.updatedAt)
        }
        db.periodDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id"); o.remove("updatedAt")
            val r = SyncRecord("period", e.startEpochDay.toString(), o, e.updatedAt)
            out[r.id] = r
        }
        db.dailyLogDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id"); o.remove("updatedAt")
            val r = SyncRecord("log", e.epochDay.toString(), o, e.updatedAt)
            out[r.id] = r
        }
        db.reminderDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id")
            val r = SyncRecord("reminder", e.id, o, 0L)
            out[r.id] = r
        }
        out
    }

    // ---------- apply server copies to the cache ----------

    private suspend fun applyAll(records: List<SyncRecord>) {
        if (records.isEmpty()) return
        db.withTransaction {
            records.forEach { applyOne(it) }
        }
    }

    private suspend fun applyOne(r: SyncRecord) {
        when (r.kind) {
            "profile" -> {
                if (r.deleted || r.data == null) return
                val cur = db.profileDao().get()
                val o = r.data.deepCopy()
                PROFILE_LOCAL_ONLY.forEach { o.remove(it) }
                // Start from this phone's profile (or the defaults) so device-only fields and
                // fields the server copy lacks keep valid values (Gson skips Kotlin defaults).
                val base = gson.toJsonTree(cur ?: UserProfileEntity()).asJsonObject
                val p = gson.fromJson(withDefaults(base, o, keepFromBase = PROFILE_LOCAL_ONLY), UserProfileEntity::class.java)
                db.profileDao().upsert(p.copy(id = 1, updatedAt = r.updatedAt))
            }
            "period" -> {
                val start = r.key.toLongOrNull() ?: return
                val existing = db.periodDao().getAll().find { it.startEpochDay == start }
                if (r.deleted || r.data == null) {
                    existing?.let { db.periodDao().delete(it.id) }
                } else {
                    val base = gson.toJsonTree(PeriodEventEntity(startEpochDay = start, createdAt = r.updatedAt, updatedAt = r.updatedAt)).asJsonObject
                    val e = gson.fromJson(withDefaults(base, r.data), PeriodEventEntity::class.java)
                    db.periodDao().upsert(e.copy(id = existing?.id ?: 0L, startEpochDay = start, updatedAt = r.updatedAt))
                }
            }
            "log" -> {
                val day = r.key.toLongOrNull() ?: return
                if (r.deleted || r.data == null) {
                    db.dailyLogDao().deleteByDay(day)
                } else {
                    val existing = db.dailyLogDao().getByDay(day)
                    val base = gson.toJsonTree(DailyLogEntity(epochDay = day, createdAt = r.updatedAt, updatedAt = r.updatedAt)).asJsonObject
                    val e = gson.fromJson(withDefaults(base, r.data), DailyLogEntity::class.java)
                    db.dailyLogDao().upsert(e.copy(id = existing?.id ?: 0L, epochDay = day, updatedAt = r.updatedAt))
                }
            }
            "reminder" -> {
                if (r.deleted || r.data == null) return
                val base = gson.toJsonTree(ReminderPrefEntity(id = r.key)).asJsonObject
                val o = withDefaults(base, r.data).apply { addProperty("id", r.key) }
                db.reminderDao().upsert(gson.fromJson(o, ReminderPrefEntity::class.java))
            }
            "settings" -> { } // nothing app-side yet (the old partner-consent switch is gone)
        }
    }

    /**
     * The server copy over the entity's defaults: every field the server has wins, missing
     * fields keep their default (a non-null default never turns into null).
     */
    private fun withDefaults(base: JsonObject, data: JsonObject, keepFromBase: Set<String> = emptySet()): JsonObject {
        val out = base.deepCopy()
        data.entrySet().forEach { (k, v) -> if (k !in keepFromBase && !v.isJsonNull) out.add(k, v) }
        // A field the server explicitly cleared (absent = null) must be cleared here too,
        // unless it is non-null by default (kept from the base) or device-only.
        base.keySet().filter { it !in keepFromBase && !data.has(it) && it !in NON_NULL_FIELDS }.forEach { out.remove(it) }
        return out
    }

    // ---------- network ----------

    private fun SyncRecordDto.toRecord() = SyncRecord(kind, key, data, updatedAt, deleted)

    private suspend fun pullAll(since: Long): Pair<List<SyncRecord>, Long> {
        val out = mutableListOf<SyncRecord>()
        var cursor = since
        var pages = 0
        while (true) {
            val res = remote.pull(cursor)
            out += res.records.orEmpty().map { it.toRecord() }
            cursor = res.cursor
            pages += 1
            if (!res.more || pages >= 50) break
        }
        return out to cursor
    }

    /** Returns the server copies that were newer than ours (conflicts we lost). */
    private suspend fun pushAll(changes: List<SyncRecord>): List<SyncRecord> {
        val conflicts = mutableListOf<SyncRecord>()
        changes.chunked(BATCH).forEach { chunk ->
            val res = remote.push(chunk)
            conflicts += res.conflicts.orEmpty().map { it.toRecord() }
        }
        return conflicts
    }

    // ---------- state ----------

    private fun readMap(key: String): MutableMap<String, String> = try {
        gson.fromJson<MutableMap<String, String>>(sp.getString(key, null) ?: "{}", object : TypeToken<MutableMap<String, String>>() {}.type)
            ?: mutableMapOf()
    } catch (_: Throwable) { mutableMapOf() }

    private fun readLongMap(key: String): MutableMap<String, Long> = try {
        gson.fromJson<MutableMap<String, Long>>(sp.getString(key, null) ?: "{}", object : TypeToken<MutableMap<String, Long>>() {}.type)
            ?: mutableMapOf()
    } catch (_: Throwable) { mutableMapOf() }

    private fun resetState(userId: String) {
        sp.edit().clear().putString(K_USER, userId).apply()
    }

    /** Forget sync state (logout / account deletion). */
    fun resetAll() {
        sp.edit().clear().apply()
        _status.value = DataSyncStatus()
    }

    private suspend fun canSync(): String? {
        if (!auth.hasSessionTokens()) return null
        if (prefs.getAccountRole() == "male") return null
        if (db.profileDao().get()?.qaSampleData == true) return null // debug sample data never syncs
        return prefs.accountUserId.first()?.takeIf { it.isNotBlank() }
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        if (canSync() == null || !sp.getBoolean(K_INITIAL, false)) return@withContext 0
        DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), clock.nowMillis()).size
    }

    suspend fun refreshPending() {
        val n = try { pendingCount() } catch (_: Throwable) { 0 }
        _status.value = _status.value.copy(pending = n)
    }

    fun isInitialDone(): Boolean = sp.getBoolean(K_INITIAL, false)

    /** Next round sends every changed item stamped "now" (used after restoring a backup file). */
    fun markLocalWins() {
        sp.edit().putBoolean(K_LOCAL_WINS, true).apply()
    }

    // ---------- sync ----------

    suspend fun sync(): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val userId = canSync() ?: return@withContext SyncOutcome.Skipped
            _status.value = _status.value.copy(running = true)
            try {
                if (sp.getString(K_USER, null) != userId) resetState(userId)
                if (!sp.getBoolean(K_INITIAL, false)) initialSync() else incrementalSync()
                val now = clock.nowMillis()
                sp.edit().putLong(K_LAST_OK, now).apply()
                _status.value = DataSyncStatus(lastOkAt = now, pending = 0, running = false, lastError = null)
                SyncOutcome.Ok
            } catch (t: Throwable) {
                val pending = try { DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), clock.nowMillis()).size } catch (_: Throwable) { 0 }
                _status.value = _status.value.copy(running = false, lastError = t.message, pending = pending)
                SyncOutcome.Failed(t.message)
            }
        }
    }

    private suspend fun initialSync() {
        val (serverList, cursor) = pullAll(0L)
        val server = serverList.associateBy { it.id }
        val local = collectLocal()
        val plan = DataSyncEngine.planInitial(local, server, clock.nowMillis())
        applyAll(plan.applyLocal)
        val conflicts = pushAll(plan.push)
        applyAll(conflicts)
        val after = collectLocal()
        val synced = after.mapNotNull { (id, r) -> DataSyncEngine.hash(r)?.let { id to it } }.toMap()
        sp.edit()
            .putString(K_SYNCED, gson.toJson(synced))
            .putString(K_DETECTED, "{}")
            .putLong(K_CURSOR, cursor)
            .putBoolean(K_INITIAL, true)
            .apply()
    }

    private suspend fun incrementalSync() {
        val now = clock.nowMillis()
        val synced = readMap(K_SYNCED)
        val detected = readLongMap(K_DETECTED)
        val local = collectLocal()

        // Remember when an offline change was first seen (for items without their own timestamp).
        val dirtyIds = local.filter { (id, r) -> DataSyncEngine.hash(r) != synced[id] }.keys + (synced.keys - local.keys)
        dirtyIds.forEach { if (it !in detected) detected[it] = now }
        detected.keys.retainAll(dirtyIds)
        sp.edit().putString(K_DETECTED, gson.toJson(detected)).apply()

        val localWins = sp.getBoolean(K_LOCAL_WINS, false)
        val changes = DataSyncEngine.localChanges(local, synced, now, detected)
            .let { list -> if (localWins) list.map { it.copy(updatedAt = now) } else list }
        val conflicts = pushAll(changes)
        if (localWins) sp.edit().putBoolean(K_LOCAL_WINS, false).apply()
        val conflictIds = conflicts.map { it.id }.toSet()
        val newSynced = synced.toMutableMap()
        changes.forEach { c ->
            if (c.id in conflictIds) return@forEach
            val h = DataSyncEngine.hash(c)
            if (h == null) newSynced.remove(c.id) else newSynced[c.id] = h
        }
        applyAll(conflicts)

        val (remote, cursor) = pullAll(sp.getLong(K_CURSOR, 0L))
        val pushedIds = changes.map { it.id }.toSet() - conflictIds
        val toApply = DataSyncEngine.remoteToApply(remote.distinctBy { it.id to it.updatedAt }, local, pushedIds)
            .groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedAt } }
        applyAll(toApply)

        val touched = (toApply.map { it.id } + conflictIds).toSet()
        if (touched.isNotEmpty()) {
            val after = collectLocal()
            touched.forEach { id ->
                val h = DataSyncEngine.hash(after[id])
                if (h == null) newSynced.remove(id) else newSynced[id] = h
            }
        }
        sp.edit()
            .putString(K_SYNCED, gson.toJson(newSynced))
            .putString(K_DETECTED, "{}")
            .putLong(K_CURSOR, cursor)
            .apply()
    }

    /** Logout: drop the local cache (the server keeps everything). */
    suspend fun clearLocalCache() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.profileDao().clear()
            db.periodDao().clear()
            db.dailyLogDao().clear()
            db.reminderDao().clear()
        }
        resetAll()
    }
}
