package com.mahava.app.sync

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * One synced item of the woman's own data. kind: profile | period | log | reminder | settings.
 * key is a stable natural key (profile: "main", period: start epochDay, log: epochDay,
 * reminder: id, settings: name), so the same item matches across phones.
 */
data class SyncRecord(
    val kind: String,
    val key: String,
    val data: JsonObject?,
    val updatedAt: Long,
    val deleted: Boolean = false
) {
    val id: String get() = "$kind/$key"
}

/** What a sync round should do. Pure data so it can be unit-tested without Android. */
data class SyncPlan(
    val push: List<SyncRecord>,
    val applyLocal: List<SyncRecord>,
    /** Items already equal on both sides (just remember them as synced). */
    val same: List<String>
)

/**
 * Last-write-wins merge rules for the server-is-source-of-truth sync.
 * The local Room database is a cache that keeps the app usable offline; changes are
 * detected by comparing each item's content hash with the hash at the last successful sync.
 */
object DataSyncEngine {

    /** Stable text form: sorted keys, nulls dropped, numbers normalized (7.0 == 7). */
    fun canonical(obj: JsonObject?): String {
        if (obj == null) return "null"
        val sb = StringBuilder("{")
        obj.keySet().sorted().forEach { k ->
            val v = obj.get(k)
            if (v == null || v.isJsonNull) return@forEach
            if (sb.length > 1) sb.append(',')
            sb.append('"').append(k).append("\":").append(value(v))
        }
        return sb.append('}').toString()
    }

    private fun value(v: JsonElement): String {
        if (v.isJsonPrimitive) {
            val p = v.asJsonPrimitive
            return when {
                p.isNumber -> try {
                    BigDecimal(p.asString).stripTrailingZeros().toPlainString()
                } catch (_: Throwable) { p.asString }
                p.isBoolean -> p.asBoolean.toString()
                else -> JsonPrimitive(p.asString).toString()
            }
        }
        return v.toString()
    }

    fun hash(rec: SyncRecord?): String? {
        if (rec == null || rec.deleted) return null
        val md = MessageDigest.getInstance("SHA-256").digest(canonical(rec.data).toByteArray(Charsets.UTF_8))
        return md.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun sameContent(a: SyncRecord?, b: SyncRecord?): Boolean =
        (a == null || a.deleted) && (b == null || b.deleted) ||
            (a != null && b != null && !a.deleted && !b.deleted && canonical(a.data) == canonical(b.data))

    /**
     * First sync of this account on this phone (right after login).
     * - Server only -> restore to the phone. Phone only -> upload once.
     * - Both differ -> newer updatedAt wins; the profile always comes from the server
     *   (the phone's copy is only the onboarding answers).
     * - If the server already has data and the phone has nothing but onboarding answers
     *   (no daily logs, no period with an end date), the server copy replaces the phone copy.
     */
    fun planInitial(local: Map<String, SyncRecord>, server: Map<String, SyncRecord>): SyncPlan {
        val serverLive = server.filterValues { !it.deleted }
        val localHasRealData = local.values.any { it.kind == "log" } ||
            local.values.any { it.kind == "period" && it.data?.get("endEpochDay")?.let { e -> !e.isJsonNull } == true }
        val serverWinsAll = serverLive.values.any { it.kind == "period" || it.kind == "log" } && !localHasRealData
        val push = mutableListOf<SyncRecord>()
        val apply = mutableListOf<SyncRecord>()
        val same = mutableListOf<String>()
        for (id in local.keys + serverLive.keys) {
            if (id in same || push.any { it.id == id } || apply.any { it.id == id }) continue
            val l = local[id]
            val s = serverLive[id]
            when {
                l != null && s == null -> {
                    if (serverWinsAll && (l.kind == "period" || l.kind == "log")) {
                        apply += l.copy(data = null, deleted = true) // drop onboarding-only guesses
                    } else push += l
                }
                l == null && s != null -> apply += s
                l != null && s != null -> when {
                    sameContent(l, s) -> same += id
                    l.kind == "profile" || serverWinsAll -> apply += s
                    s.updatedAt >= l.updatedAt -> apply += s
                    else -> push += l
                }
            }
        }
        return SyncPlan(push, apply, same)
    }

    /**
     * Regular round: push what changed on the phone since the last sync (including deletions),
     * then take server changes for items the phone did not change.
     * [synced] maps item id -> content hash at the last successful sync.
     * [detectedAt] gives a timestamp for items without their own updatedAt (reminders, settings).
     */
    fun localChanges(
        local: Map<String, SyncRecord>,
        synced: Map<String, String>,
        now: Long,
        detectedAt: Map<String, Long> = emptyMap()
    ): List<SyncRecord> {
        val out = mutableListOf<SyncRecord>()
        for ((id, rec) in local) {
            if (hash(rec) != synced[id]) {
                val ts = if (rec.updatedAt > 0) rec.updatedAt else detectedAt[id] ?: now
                out += rec.copy(updatedAt = ts)
            }
        }
        for (id in synced.keys) {
            if (id !in local) {
                val (kind, key) = id.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                out += SyncRecord(kind, key, null, detectedAt[id] ?: now, deleted = true)
            }
        }
        return out
    }

    /** Server changes to apply: skip what the phone itself changed in this round and what is already equal. */
    fun remoteToApply(remote: List<SyncRecord>, local: Map<String, SyncRecord>, pushedIds: Set<String>): List<SyncRecord> =
        remote.filter { r -> r.id !in pushedIds && !sameContent(local[r.id], r) }
}
