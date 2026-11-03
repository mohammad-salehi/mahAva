package com.mahava.app.content

import android.content.Context
import com.google.gson.JsonObject

/**
 * Privacy policy + terms of use. The text is ONE combined Mah document: backend
 * src/content/legal-mah-fa.md, served at GET /api/mah/auth/legal. The app keeps the last copy it
 * fetched (SharedPreferences) so the screens also work offline; the accepted version is sent
 * with registration and stored on the server.
 */
object LegalDocuments {
    /** Used only until the first successful fetch (must match backend LEGAL_VERSION). */
    private const val FALLBACK_VERSION = "2026-11-03"
    private const val PREFS = "mah_legal_cache"

    @Volatile var privacyVersion: String = FALLBACK_VERSION; private set
    @Volatile var termsVersion: String = FALLBACK_VERSION; private set
    @Volatile var updatedAtFa: String = ""; private set
    @Volatile var privacyFa: String = ""; private set
    @Volatile var termsFa: String = ""; private set

    val PRIVACY_VERSION: String get() = privacyVersion
    val TERMS_VERSION: String get() = termsVersion
    val UPDATED_AT_FA: String get() = updatedAtFa

    const val OFFLINE_FA = "برای دیدن متن کامل، یک بار به اینترنت وصل شو و دوباره این صفحه را باز کن."

    private var loaded = false

    /** Loads the cached copy (cheap; safe to call often). */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        privacyFa = p.getString("privacy", "").orEmpty()
        termsFa = p.getString("terms", "").orEmpty()
        privacyVersion = p.getString("privacyVersion", null) ?: FALLBACK_VERSION
        termsVersion = p.getString("termsVersion", null) ?: FALLBACK_VERSION
        updatedAtFa = p.getString("updatedAtFa", "").orEmpty()
    }

    /** Fetches the shared text from the server and caches it. Returns true on success. */
    suspend fun refresh(context: Context, fetch: suspend () -> JsonObject): Boolean {
        load(context)
        return try {
            val o = fetch()
            val privacy = o.get("privacy")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val terms = o.get("terms")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (privacy.isBlank() || terms.isBlank()) return false
            privacyFa = privacy
            termsFa = terms
            privacyVersion = o.get("privacyVersion")?.asString ?: FALLBACK_VERSION
            termsVersion = o.get("termsVersion")?.asString ?: FALLBACK_VERSION
            updatedAtFa = o.get("updatedAtFa")?.asString.orEmpty()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("privacy", privacyFa).putString("terms", termsFa)
                .putString("privacyVersion", privacyVersion).putString("termsVersion", termsVersion)
                .putString("updatedAtFa", updatedAtFa).apply()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
