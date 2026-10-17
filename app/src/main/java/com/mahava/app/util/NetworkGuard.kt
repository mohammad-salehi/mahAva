package com.mahava.app.util

import android.util.Log
import com.mahava.app.network.MahApiException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Safety net for coroutines the app starts on its own (view-model and app scopes).
 * A failed server call (no internet, timeout, server error, a captive-portal page that is not JSON)
 * must never crash the app: it is logged and the app carries on with its local data.
 * Any other exception is a real bug and still reaches the normal crash handler.
 */
object NetworkGuard {
    fun isNetworkFailure(t: Throwable): Boolean {
        var e: Throwable? = t
        var depth = 0
        while (e != null && depth < 5) {
            if (e is java.io.IOException || e is MahApiException || e is com.google.gson.JsonParseException) return true
            e = e.cause
            depth++
        }
        return false
    }

    val handler = CoroutineExceptionHandler { _, t ->
        if (isNetworkFailure(t)) {
            try { Log.w("Mahava", "background server call failed: ${t.javaClass.simpleName}: ${t.message}") } catch (_: Throwable) { }
        } else {
            val thread = Thread.currentThread()
            thread.uncaughtExceptionHandler?.uncaughtException(thread, t) ?: throw t
        }
    }
}
