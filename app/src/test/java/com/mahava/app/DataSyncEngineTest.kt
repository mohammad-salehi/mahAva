package com.mahava.app

import com.google.gson.JsonObject
import com.mahava.app.sync.DataSyncEngine
import com.mahava.app.sync.SyncRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSyncEngineTest {
    private fun obj(vararg kv: Pair<String, Any?>) = JsonObject().apply {
        kv.forEach { (k, v) ->
            when (v) {
                null -> add(k, com.google.gson.JsonNull.INSTANCE)
                is Number -> addProperty(k, v)
                is Boolean -> addProperty(k, v)
                else -> addProperty(k, v.toString())
            }
        }
    }
    private fun log(day: Long, ts: Long, vararg kv: Pair<String, Any?>) =
        SyncRecord("log", day.toString(), obj("epochDay" to day, *kv), ts)
    private fun period(start: Long, ts: Long, end: Long? = null) =
        SyncRecord("period", start.toString(), obj("startEpochDay" to start, "endEpochDay" to end), ts)
    private fun map(vararg r: SyncRecord) = r.associateBy { it.id }

    @Test
    fun canonicalIgnoresKeyOrderNullsAndNumberFormat() {
        val a = obj("b" to 7.0, "a" to "x", "n" to null)
        val b = obj("a" to "x", "b" to 7)
        assertEquals(DataSyncEngine.canonical(a), DataSyncEngine.canonical(b))
        assertEquals(DataSyncEngine.hash(SyncRecord("log", "1", a, 1)), DataSyncEngine.hash(SyncRecord("log", "1", b, 2)))
    }

    @Test
    fun firstLoginMergesUploadsLocalOnlyAndRestoresServerOnly() {
        val local = map(log(100, 10, "moods" to "sad"), log(101, 50, "moods" to "calm"), period(90, 5, 94))
        val server = map(log(101, 40, "moods" to "happy"), log(102, 20, "moods" to "tired"))
        val plan = DataSyncEngine.planInitial(local, server)
        assertEquals(setOf("log/100", "log/101", "period/90"), plan.push.map { it.id }.toSet()) // local 101 is newer
        assertEquals(listOf("log/102"), plan.applyLocal.map { it.id })
    }

    @Test
    fun firstLoginServerNewerWinsAndProfileComesFromServer() {
        val local = map(log(101, 10, "moods" to "calm"), SyncRecord("profile", "main", obj("goal" to "track_period"), 999))
        val server = map(log(101, 40, "moods" to "happy"), SyncRecord("profile", "main", obj("goal" to "ttc"), 1))
        val plan = DataSyncEngine.planInitial(local, server)
        assertEquals(setOf("log/101", "profile/main"), plan.applyLocal.map { it.id }.toSet())
        assertTrue(plan.push.isEmpty())
    }

    @Test
    fun newPhoneWithOnlyOnboardingAnswersTakesServerCopy() {
        // Onboarding made a guessed period (no end, no logs); the account already has real data.
        val local = map(period(200, 99), SyncRecord("profile", "main", obj("onboardingDone" to true), 99))
        val server = map(period(198, 1, 202), log(199, 1, "moods" to "sad"), SyncRecord("profile", "main", obj("onboardingDone" to true, "goal" to "ttc"), 1))
        val plan = DataSyncEngine.planInitial(local, server)
        assertTrue(plan.push.isEmpty())
        val ids = plan.applyLocal.associateBy { it.id }
        assertTrue(ids["period/200"]!!.deleted)
        assertTrue("period/198" in ids && "log/199" in ids && "profile/main" in ids)
    }

    @Test
    fun offlineEditsAndDeletesArePushedOnce() {
        val a = log(100, 10, "moods" to "sad")
        val b = log(101, 10, "moods" to "calm")
        val synced = mapOf(a.id to DataSyncEngine.hash(a)!!, b.id to DataSyncEngine.hash(b)!!, "period/90" to "old")
        val edited = log(100, 30, "moods" to "happy")
        val reminder = SyncRecord("reminder", "daily_log", obj("enabled" to true), 0)
        val changes = DataSyncEngine.localChanges(map(edited, b, reminder), synced, now = 1000, detectedAt = mapOf("reminder/daily_log" to 500))
        val byId = changes.associateBy { it.id }
        assertEquals(setOf("log/100", "reminder/daily_log", "period/90"), byId.keys)
        assertEquals(30L, byId["log/100"]!!.updatedAt)
        assertEquals(500L, byId["reminder/daily_log"]!!.updatedAt)
        assertTrue(byId["period/90"]!!.deleted)
        // After a successful push nothing is pending.
        val after = synced.toMutableMap().apply {
            changes.forEach { c -> DataSyncEngine.hash(c)?.let { put(c.id, it) } ?: remove(c.id) }
        }
        assertTrue(DataSyncEngine.localChanges(map(edited, b, reminder), after, 2000).isEmpty())
    }

    @Test
    fun remoteChangesSkipOwnPushesAndEqualItems() {
        val local = map(log(100, 10, "moods" to "sad"), log(101, 10, "moods" to "calm"))
        val remote = listOf(
            log(100, 50, "moods" to "angry"), // pushed by us this round -> skip
            log(101, 60, "moods" to "calm"), // same content -> skip
            log(102, 70, "moods" to "tired"), // from another phone -> apply
            SyncRecord("log", "101", null, 80, deleted = true).copy(key = "103") // delete of an item the phone does not have -> nothing to do
        )
        val apply = DataSyncEngine.remoteToApply(remote, local, pushedIds = setOf("log/100"))
        assertEquals(listOf("log/102"), apply.map { it.id })
    }
}
