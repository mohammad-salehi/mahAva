package com.mahava.app.sync

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.mahava.app.data.auth.AuthRepository
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.HealthAlertEntity
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.PregnancyEntity
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
    /**
     * [reason] says what really went wrong (network vs. a server answer), [restored] is true when
     * the server copy was pulled and applied and only some uploads are still waiting.
     */
    data class Failed(
        val message: String?,
        val reason: SyncFailure = SyncFailure.UNKNOWN,
        val httpCode: Int? = null,
        val restored: Boolean = false
    ) : SyncOutcome()
}

/** Why a sync round did not finish. */
enum class SyncFailure { NETWORK, TIMEOUT, RATE_LIMIT, SERVER, AUTH, UPDATE_REQUIRED, REJECTED, UNKNOWN;
    companion object {
        fun of(t: Throwable): Pair<SyncFailure, Int?> {
            var e: Throwable? = t
            var depth = 0
            while (e != null && depth < 5) {
                when (e) {
                    is com.mahava.app.network.MahApiException -> return when {
                        e.status == 429 -> RATE_LIMIT
                        e.status == 401 || e.status == 403 -> AUTH
                        e.status == 426 -> UPDATE_REQUIRED
                        e.status >= 500 -> SERVER
                        e.status in 400..499 -> REJECTED
                        else -> SERVER
                    } to e.status
                    is java.net.SocketTimeoutException -> return TIMEOUT to null
                    is java.io.IOException -> return NETWORK to null
                    // A reply that is not our JSON (proxy/CDN page): the server side answered badly.
                    is com.google.gson.JsonParseException -> return SERVER to null
                }
                e = e.cause
                depth++
            }
            return UNKNOWN to null
        }
    }
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
        /** Clears fake "synced" hashes from an older client that soft-dropped rejected pushes. */
        private const val K_RESYNC_SOFT = "resync_soft_v1"
        private const val BATCH = 200

        /** Data errors (400/413) are isolated per record; anything else stops the round at once. */
        internal fun isRecordError(t: Throwable): Boolean {
            val (reason, code) = SyncFailure.of(t)
            return reason == SyncFailure.REJECTED && (code == 400 || code == 413 || code == 422)
        }

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
            out["profile/main"] = SyncRecord("profile", "main", sanitizeForServer(o), p.updatedAt)
        }
        db.periodDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id"); o.remove("updatedAt")
            val r = SyncRecord("period", e.startEpochDay.toString(), sanitizeForServer(o), e.updatedAt)
            out[r.id] = r
        }
        db.dailyLogDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id"); o.remove("updatedAt")
            val r = SyncRecord("log", e.epochDay.toString(), sanitizeForServer(o), e.updatedAt)
            out[r.id] = r
        }
        db.reminderDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id")
            o.remove("updatedAt")
            val r = SyncRecord("reminder", e.id, sanitizeForServer(o), e.updatedAt)
            out[r.id] = r
        }
        db.pregnancyDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("id")
            o.remove("updatedAt")
            // Stable keys so push/pull never split one pregnancy across "active" vs numeric ids.
            val key = if (e.status == "active") "main" else "ended_${e.id.coerceAtLeast(1)}"
            val r = SyncRecord("pregnancy", key, sanitizeForServer(o), e.updatedAt)
            out[r.id] = r
        }
        db.healthAlertDao().getAll().forEach { e ->
            val o = gson.toJsonTree(e).asJsonObject
            o.remove("updatedAt")
            // Server key: alphanumeric only (UUID hyphens → underscore).
            val key = e.id.replace('-', '_').take(64)
            if (!o.has("id")) o.addProperty("id", e.id)
            val ts = maxOf(e.updatedAt, e.createdAt)
            val r = SyncRecord("alert", key, sanitizeForServer(o), ts)
            out[r.id] = r
        }
        db.toolRecordDao().getAll().forEach { e ->
            if(e.type=="sync_tombstone") {
                val value=com.google.gson.JsonParser.parseString(e.payloadJson).asJsonObject
                val kind=value.get("kind").asString; val key=value.get("key").asString
                val current=out["$kind/$key"]
                if(current==null || current.updatedAt<=e.updatedAt) out["$kind/$key"]=SyncRecord(kind,key,null,e.updatedAt,true)
                return@forEach
            }
            val o = gson.toJsonTree(e).asJsonObject.apply { remove("updatedAt") }
            val rec = SyncRecord("tool", e.id, if (e.deletedAt == null) sanitizeForServer(o) else null, e.updatedAt, e.deletedAt != null)
            out[rec.id] = rec
        }
        out
    }

    /** Backend accepts only a flat map of primitives (no nested objects/arrays). */
    private fun sanitizeForServer(src: JsonObject?): JsonObject? {
        if (src == null) return null
        val out = JsonObject()
        val nameOk = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,47}$")
        src.entrySet().forEach { (k, v) ->
            if (!nameOk.matches(k) || v == null || v.isJsonNull) return@forEach
            if (!v.isJsonPrimitive) return@forEach
            val p = v.asJsonPrimitive
            when {
                p.isBoolean -> out.addProperty(k, p.asBoolean)
                p.isNumber -> {
                    val n = p.asDouble
                    if (n.isFinite()) out.add(k, v)
                }
                p.isString -> {
                    // Over-long text is still sent: the server rejects only that record (400),
                    // which stays pending, instead of the whole round failing here.
                    out.addProperty(k, p.asString)
                }
            }
        }
        return out
    }

    // ---------- apply server copies to the cache ----------

    private suspend fun applyAll(records: List<SyncRecord>) {
        if (records.isEmpty()) return
        db.withTransaction {
            records.forEach { applyOne(it) }
        }
    }

    private suspend fun applyUnchanged(records: List<SyncRecord>, snapshot: Map<String, SyncRecord>): List<SyncRecord> = db.withTransaction {
        val current=collectLocal()
        val safe=records.filter { DataSyncEngine.sameContent(current[it.id],snapshot[it.id]) && current[it.id]?.updatedAt==snapshot[it.id]?.updatedAt }
        safe.forEach { applyOne(it) }
        safe
    }

    private suspend fun applyOne(r: SyncRecord) {
        if(!r.deleted) {
            val tombstoneId="tombstone_${r.kind}_${r.key}"
            db.toolRecordDao().get(tombstoneId)?.let {
                if(r.updatedAt>=it.updatedAt) db.toolRecordDao().removeSyncedTombstone(tombstoneId)
            }
        }
        when (r.kind) {
            "tool" -> {
                val existing = db.toolRecordDao().get(r.key)
                if (r.deleted || r.data == null) {
                    existing?.let { db.toolRecordDao().upsert(it.copy(deletedAt = r.updatedAt, updatedAt = r.updatedAt)) }
                } else {
                    val base = gson.toJsonTree(com.mahava.app.data.db.ToolRecord(r.key, "unknown", payloadJson="{}", createdAt=r.updatedAt, updatedAt=r.updatedAt)).asJsonObject
                    val item = gson.fromJson(withDefaults(base, r.data), com.mahava.app.data.db.ToolRecord::class.java)
                    db.toolRecordDao().upsert(item.copy(id=r.key, updatedAt=r.updatedAt))
                }
            }
            "profile" -> {
                if (r.deleted || r.data == null) { db.profileDao().clear(); return }
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
                val o = withDefaults(base, r.data).apply { addProperty("id", r.key); addProperty("updatedAt",r.updatedAt) }
                db.reminderDao().upsert(gson.fromJson(o, ReminderPrefEntity::class.java))
            }
            "pregnancy" -> {
                val endedId = r.key.removePrefix("ended_").toLongOrNull()
                val existing = when {
                    r.key == "main" || r.key == "active" ->
                        db.pregnancyDao().getActive() ?: db.pregnancyDao().getAll().firstOrNull()
                    endedId != null -> db.pregnancyDao().getById(endedId)
                    else -> db.pregnancyDao().getById(r.key.toLongOrNull() ?: return)
                }
                if (r.deleted || r.data == null) {
                    existing?.let { db.pregnancyDao().delete(it.id) }
                } else {
                    val base = gson.toJsonTree(
                        PregnancyEntity(
                            estimatedDueEpochDay = r.data.get("estimatedDueEpochDay")?.asLong ?: 0L,
                            createdAt = r.updatedAt,
                            updatedAt = r.updatedAt
                        )
                    ).asJsonObject
                    val e = gson.fromJson(withDefaults(base, r.data), PregnancyEntity::class.java)
                    db.pregnancyDao().upsert(
                        e.copy(id = existing?.id ?: endedId ?: 0L, updatedAt = r.updatedAt)
                    )
                }
            }
            "alert" -> {
                val dataId = r.data?.get("id")?.asString?.takeIf { it.isNotBlank() }
                    ?: r.key.replace('_', '-')
                if (r.deleted || r.data == null) {
                    db.healthAlertDao().delete(dataId)
                    db.healthAlertDao().delete(r.key)
                } else {
                    val base = gson.toJsonTree(
                        HealthAlertEntity(
                            id = dataId,
                            ruleId = "",
                            ruleVersion = 1,
                            severity = "info",
                            titleFa = "",
                            plainLanguageExplanationFa = "",
                            recommendedActionFa = "",
                            observedDates = "",
                            evidenceReferences = "",
                            deduplicationKey = dataId,
                            createdAt = r.updatedAt,
                            updatedAt = r.updatedAt
                        )
                    ).asJsonObject
                    val e = gson.fromJson(withDefaults(base, r.data), HealthAlertEntity::class.java)
                    db.healthAlertDao().upsert(e.copy(id = dataId, updatedAt = r.updatedAt))
                }
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
            if (!res.more) break
            check(pages < 50) { "همگام‌سازی کامل نشده؛ دوباره تلاش می‌شود" }
        }
        return out to cursor
    }

    private data class PushResult(
        val conflicts: List<SyncRecord>,
        /** Successfully accepted by the server (HTTP 2xx). Never includes faked local accepts. */
        val acceptedIds: Set<String>
    )

    /**
     * Push changes with valid server timestamps. If a batch fails, retry one-by-one so
     * good records still upload; failed ones stay dirty for the next sync.
     */
    private suspend fun pushAll(changes: List<SyncRecord>): PushResult {
        if (changes.isEmpty()) return PushResult(emptyList(), emptySet())
        val now = clock.nowMillis().coerceAtLeast(DataSyncEngine.MIN_SERVER_TS)
        var firstFailure: Throwable? = null
        // A record this app cannot send (e.g. text over the limit) stays pending instead of
        // failing the whole round (and with it the restore of everything else).
        val prepared = changes.mapNotNull { rec ->
            try {
                val cleaned = rec.copy(data = if (rec.deleted) null else sanitizeForServer(rec.data))
                with(DataSyncEngine) { cleaned.withServerTimestamp(now) }
            } catch (bad: IllegalArgumentException) {
                if (firstFailure == null) firstFailure = MahApiExceptionOf(400, bad.message)
                null
            }
        }
        val conflicts = mutableListOf<SyncRecord>()
        val accepted = linkedSetOf<String>()

        /**
         * Send [part]; when the server rejects the data (400/413), split it in halves so the good
         * records still go up with few requests. Network, timeout, rate-limit, auth and 5xx errors
         * stop at once: retrying record by record only floods a slow link and the per-user limit.
         */
        suspend fun send(part: List<SyncRecord>) {
            try {
                val res = remote.push(part)
                conflicts += res.conflicts.orEmpty().map { it.toRecord() }
                accepted += part.map { it.id }
            } catch (err: Throwable) {
                if (err is kotlinx.coroutines.CancellationException) throw err
                if (!isRecordError(err)) throw err
                if (part.size == 1) {
                    if (firstFailure == null) firstFailure = err
                    return
                }
                val half = part.size / 2
                send(part.subList(0, half))
                send(part.subList(half, part.size))
            }
        }
        prepared.chunked(BATCH).forEach { chunk -> send(chunk) }
        if (firstFailure != null && accepted.isEmpty() && prepared.isNotEmpty()) throw firstFailure!!
        // Partial success: keep going so pull can run; pendingCount will retry the rest.
        return PushResult(conflicts, accepted)
    }

    @Suppress("FunctionName")
    private fun MahApiExceptionOf(code: Int, msg: String?) =
        com.mahava.app.network.MahApiException(code, msg ?: "دادهٔ نامعتبر")

    private fun rememberSyncedHashes(ids: Collection<String>, local: Map<String, SyncRecord>, base: MutableMap<String, String>) {
        ids.forEach { id ->
            val h = DataSyncEngine.hash(local[id])
            if (h == null) base.remove(id) else base[id] = h
        }
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
        if (com.mahava.app.BuildConfig.MAH_DEMO) return null // offline demo never syncs
        if (!auth.hasSessionTokens()) return null
        if (prefs.getAccountRole() == "male") return null
        if (db.profileDao().get()?.qaSampleData == true) return null // debug sample data never syncs
        return prefs.accountUserId.first()?.takeIf { it.isNotBlank() }
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        if (canSync() == null) return@withContext 0
        DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), clock.nowMillis()).size
    }

    /** Ids still not on the server (diagnostics and tests). */
    suspend fun pendingIds(): List<String> = withContext(Dispatchers.IO) {
        DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), clock.nowMillis()).map { it.id }
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
                recoverSoftDroppedHashes()
                if (!sp.getBoolean(K_INITIAL, false)) initialSync() else incrementalSync()
                val now = clock.nowMillis()
                val pending = try {
                    DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), now).size
                } catch (_: Throwable) { 0 }
                sp.edit().putLong(K_LAST_OK, now).apply()
                _status.value = DataSyncStatus(
                    lastOkAt = now,
                    pending = pending,
                    running = false,
                    lastError = if (pending > 0) "بعضی تغییرها هنوز به سرور نرسیده" else null
                )
                if (pending > 0) SyncOutcome.Failed(_status.value.lastError, SyncFailure.REJECTED, restored = true) else SyncOutcome.Ok
            } catch (t: Throwable) {
                if(t is kotlinx.coroutines.CancellationException) {
                    _status.value=_status.value.copy(running=false)
                    throw t
                }
                val pending = try { DataSyncEngine.localChanges(collectLocal(), readMap(K_SYNCED), clock.nowMillis()).size } catch (_: Throwable) { 0 }
                _status.value = _status.value.copy(running = false, lastError = t.message, pending = pending)
                val (reason, code) = SyncFailure.of(t)
                SyncOutcome.Failed(t.message, reason, code, restored = sp.getBoolean(K_INITIAL, false))
            }
        }
    }

    /** Older builds marked pregnancy/alert as synced after a server reject; force one re-upload. */
    private fun recoverSoftDroppedHashes() {
        if (sp.getBoolean(K_RESYNC_SOFT, false)) return
        val synced = readMap(K_SYNCED)
        val drop = synced.keys.filter {
            it.startsWith("pregnancy/") || it.startsWith("alert/") || it.startsWith("reminder/")
        }
        drop.forEach { synced.remove(it) }
        sp.edit()
            .putString(K_SYNCED, gson.toJson(synced))
            .putBoolean(K_RESYNC_SOFT, true)
            .apply()
    }

    private suspend fun initialSync() {
        val (serverList, cursor) = pullAll(0L)
        val server = serverList.associateBy { it.id }
        val local = collectLocal()
        val plan = DataSyncEngine.planInitial(local, server, clock.nowMillis())
        val applied = applyUnchanged(plan.applyLocal,local)
        val pushed = pushAll(plan.push)
        stampLegacyReminders(pushed.acceptedIds, local)
        val appliedConflicts=applyUnchanged(pushed.conflicts,local)
        val after = collectLocal()
        val synced = mutableMapOf<String, String>()
        plan.same.forEach { id -> DataSyncEngine.hash(local[id])?.let { synced[id] = it } }
        applied.forEach { r ->
            val h = DataSyncEngine.hash(after[r.id])
            if (h == null) synced.remove(r.id) else synced[r.id] = h
        }
        val conflictIds = pushed.conflicts.map { it.id }.toSet()
        rememberSyncedHashes(pushed.acceptedIds - conflictIds, local, synced)
        appliedConflicts.forEach { r ->
            DataSyncEngine.hash(after[r.id])?.let { synced[r.id] = it }
        }
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
        val pushed = pushAll(changes)
        stampLegacyReminders(pushed.acceptedIds, local)
        if (localWins) sp.edit().putBoolean(K_LOCAL_WINS, false).apply()
        val conflictIds = pushed.conflicts.map { it.id }.toSet()
        val newSynced = synced.toMutableMap()
        val afterPush = collectLocal()
        rememberSyncedHashes(pushed.acceptedIds - conflictIds, local, newSynced)
        pushed.acceptedIds.filter { it in conflictIds }.forEach { newSynced.remove(it) }
        val appliedConflicts=applyUnchanged(pushed.conflicts,local)

        val (remote, cursor) = pullAll(sp.getLong(K_CURSOR, 0L))
        val pushedIds = pushed.acceptedIds - conflictIds
        val toApply = DataSyncEngine.remoteToApply(remote.distinctBy { it.id to it.updatedAt }, local, pushedIds + changes.map { it.id })
            .groupBy { it.id }.map { (_, v) -> v.maxBy { it.updatedAt } }
        val applied=applyUnchanged(toApply,local)

        val touched = (applied.map { it.id } + appliedConflicts.map { it.id }).toSet()
        if (touched.isNotEmpty()) {
            val after = collectLocal()
            rememberSyncedHashes(touched, after, newSynced)
        }
        sp.edit()
            .putString(K_SYNCED, gson.toJson(newSynced))
            .putString(K_DETECTED, "{}")
            .putLong(K_CURSOR, cursor)
            .apply()
    }

    /** Legacy reminders had no edit timestamp. A successful first upload gives them one. */
    private suspend fun stampLegacyReminders(accepted: Set<String>, snapshot: Map<String, SyncRecord>) {
        db.withTransaction {
            db.reminderDao().getAll().filter { "reminder/${it.id}" in accepted }.forEach { row ->
                val sent=snapshot["reminder/${row.id}"] ?: return@forEach
                if(row.updatedAt<DataSyncEngine.MIN_SERVER_TS && row.updatedAt==sent.updatedAt) {
                    val current=gson.toJsonTree(row).asJsonObject.apply {remove("id");remove("updatedAt")}
                    if(DataSyncEngine.canonical(current)==DataSyncEngine.canonical(sent.data)) {
                        db.reminderDao().upsert(row.copy(updatedAt=clock.nowMillis().coerceAtLeast(DataSyncEngine.MIN_SERVER_TS)))
                    }
                }
            }
        }
    }

    /** Before logout: clear the pending flag so the UI cannot trap the user. */
    suspend fun clearPendingForLogout() = withContext(Dispatchers.IO) {
        val local = collectLocal()
        val synced = local.mapNotNull { (id, r) -> DataSyncEngine.hash(r)?.let { id to it } }.toMap()
        sp.edit()
            .putString(K_SYNCED, gson.toJson(synced))
            .putString(K_DETECTED, "{}")
            .apply()
        _status.value = _status.value.copy(pending = 0, lastError = null)
    }

    /** Logout: drop the local cache (the server keeps everything). */
    suspend fun clearLocalCache() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.profileDao().clear()
            db.periodDao().clear()
            db.dailyLogDao().clear()
            db.reminderDao().clear()
            db.pregnancyDao().clear()
            db.healthAlertDao().clear()
        db.toolRecordDao().clear()
        }
        resetAll()
    }
}
