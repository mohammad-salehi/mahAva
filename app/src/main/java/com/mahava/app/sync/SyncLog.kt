package com.mahava.app.sync

/**
 * Short sync diagnostics in logcat (tag "MahSync"): only item ids (e.g. "log/20380"), counts and
 * HTTP codes/server messages, never the content of her data.
 */
internal object SyncLog {
    private const val TAG = "MahSync"
    fun i(msg: String) { try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { } }
    fun w(msg: String) { try { android.util.Log.w(TAG, msg) } catch (_: Throwable) { } }
    fun describe(o: SyncOutcome): String = when (o) {
        is SyncOutcome.Ok -> "ok"
        is SyncOutcome.Skipped -> "skipped"
        is SyncOutcome.Failed -> "failed(${o.reason}${o.httpCode?.let { " $it" } ?: ""}: ${o.message?.take(80)})"
    }
    fun ids(list: Collection<String>, max: Int = 20): String =
        if (list.size <= max) list.joinToString(",", "[", "]") else list.take(max).joinToString(",", "[", ",…+${list.size - max}]")
}
