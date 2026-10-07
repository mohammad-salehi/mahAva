package com.mahava.app.notice

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One in-app notice (pairing, unpairing, her updates…), shown in the app's notification list. */
data class AppNotice(
    val id: String,
    val kind: String,
    val title: String,
    val body: String,
    val time: Long,
    val read: Boolean = false,
    /** Show once as a dialog the next time the app is open. */
    val popup: Boolean = false
)

/**
 * In-app notifications, kept on the phone (last 50). Approvals and alerts are shown inside the
 * app as dialogs/cards; only the husband also gets system notifications while the app is closed.
 */
object AppNotices {
    private const val SP = "mahava_notices"
    private const val KEY = "items"
    private const val MAX = 50
    private val gson = Gson()
    private val type = object : TypeToken<List<AppNotice>>() {}.type
    private val _items = MutableStateFlow<List<AppNotice>>(emptyList())
    val items: StateFlow<List<AppNotice>> = _items.asStateFlow()
    @Volatile private var loaded = false

    /** True while an activity of the app is visible. */
    @Volatile var foreground: Boolean = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _items.value = read(context)
            loaded = true
        }
    }

    private fun read(context: Context): List<AppNotice> = try {
        val json = context.getSharedPreferences(SP, Context.MODE_PRIVATE).getString(KEY, null)
        if (json.isNullOrBlank()) emptyList() else gson.fromJson<List<AppNotice>>(json, type).orEmpty()
    } catch (_: Throwable) { emptyList() }

    private fun write(context: Context, list: List<AppNotice>) {
        val trimmed = list.sortedByDescending { it.time }.take(MAX)
        _items.value = trimmed
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putString(KEY, gson.toJson(trimmed)).apply()
    }

    @Synchronized
    fun add(context: Context, kind: String, title: String, body: String, popup: Boolean = false, id: String? = null) {
        load(context)
        val now = System.currentTimeMillis()
        val nid = id ?: "$kind-$now"
        val rest = _items.value.filterNot { it.id == nid }
        write(context, listOf(AppNotice(nid, kind, title, body, now, read = false, popup = popup)) + rest)
    }

    @Synchronized
    fun markAllRead(context: Context) {
        load(context)
        if (_items.value.none { !it.read || it.popup }) return
        write(context, _items.value.map { it.copy(read = true, popup = false) })
    }

    @Synchronized
    fun dismissPopup(context: Context, id: String) {
        load(context)
        write(context, _items.value.map { if (it.id == id) it.copy(popup = false, read = true) else it })
    }

    @Synchronized
    fun clear(context: Context) {
        load(context)
        write(context, emptyList())
    }
}
