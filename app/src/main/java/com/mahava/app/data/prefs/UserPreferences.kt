package com.mahava.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Lightweight preferences via DataStore.
 * Local premium demo unlock + server auth/subscription entitlement.
 */
class UserPreferences(private val context: Context) {
    private val store = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("mahava_prefs") }
    )

    private val KEY_LAST_UNLOCK = stringPreferencesKey("last_unlock_millis")
    private val KEY_SAMPLE_MODE = booleanPreferencesKey("sample_data_mode")
    private val KEY_PREMIUM = booleanPreferencesKey("premium_unlocked")
    private val KEY_CHECKER_LAST = stringPreferencesKey("checker_last_json")

    private val KEY_ACCESS = stringPreferencesKey("mah_access_token")
    private val KEY_REFRESH = stringPreferencesKey("mah_refresh_token")
    private val KEY_PHONE = stringPreferencesKey("mah_account_phone")
    private val KEY_NAME = stringPreferencesKey("mah_account_name")
    private val KEY_USER_ID = stringPreferencesKey("mah_account_id")
    private val KEY_SERVER_ACTIVE = booleanPreferencesKey("mah_server_sub_active")
    private val KEY_SERVER_PLAN = stringPreferencesKey("mah_server_sub_plan")
    private val KEY_SERVER_ENDS = stringPreferencesKey("mah_server_sub_ends")
    private val KEY_YEARLY_PRICE = intPreferencesKey("mah_yearly_price")
    private val KEY_API_BASE = stringPreferencesKey("mah_api_base_url")

    val lastUnlockMillis: Flow<Long?> = store.data.map { it[KEY_LAST_UNLOCK]?.toLongOrNull() }
    val sampleDataMode: Flow<Boolean> = store.data.map { it[KEY_SAMPLE_MODE] ?: false }
    /** Local debug / demo unlock only. Prefer AuthRepository.isPremiumEffective for gates. */
    val isPremium: Flow<Boolean> = store.data.map { it[KEY_PREMIUM] ?: false }
    val lastCheckerJson: Flow<String?> = store.data.map { it[KEY_CHECKER_LAST] }

    val accessToken: Flow<String?> = store.data.map { it[KEY_ACCESS] }
    val refreshToken: Flow<String?> = store.data.map { it[KEY_REFRESH] }
    val accountPhone: Flow<String?> = store.data.map { it[KEY_PHONE] }
    val accountName: Flow<String?> = store.data.map { it[KEY_NAME] }
    val accountUserId: Flow<String?> = store.data.map { it[KEY_USER_ID] }
    val serverHasActiveSubscription: Flow<Boolean> = store.data.map { it[KEY_SERVER_ACTIVE] ?: false }
    val serverPlan: Flow<String?> = store.data.map { it[KEY_SERVER_PLAN] }
    val serverEndsAt: Flow<String?> = store.data.map { it[KEY_SERVER_ENDS] }
    val yearlyPriceTomans: Flow<Int> = store.data.map { it[KEY_YEARLY_PRICE] ?: 585000 }
    val apiBaseUrlFlow: Flow<String?> = store.data.map { it[KEY_API_BASE] }

    suspend fun setLastUnlock(millis: Long) {
        store.edit { it[KEY_LAST_UNLOCK] = millis.toString() }
    }

    suspend fun setSampleMode(enabled: Boolean) {
        store.edit { it[KEY_SAMPLE_MODE] = enabled }
    }

    suspend fun setPremium(unlocked: Boolean) {
        store.edit { it[KEY_PREMIUM] = unlocked }
    }

    suspend fun setLastCheckerJson(json: String?) {
        store.edit {
            if (json == null) it.remove(KEY_CHECKER_LAST) else it[KEY_CHECKER_LAST] = json
        }
    }

    suspend fun setAccessToken(token: String?) {
        store.edit {
            if (token.isNullOrBlank()) it.remove(KEY_ACCESS) else it[KEY_ACCESS] = token
        }
    }

    suspend fun setRefreshToken(token: String?) {
        store.edit {
            if (token.isNullOrBlank()) it.remove(KEY_REFRESH) else it[KEY_REFRESH] = token
        }
    }

    suspend fun setAccount(phone: String?, name: String?, userId: String?) {
        store.edit {
            if (phone.isNullOrBlank()) it.remove(KEY_PHONE) else it[KEY_PHONE] = phone
            if (name.isNullOrBlank()) it.remove(KEY_NAME) else it[KEY_NAME] = name
            if (userId.isNullOrBlank()) it.remove(KEY_USER_ID) else it[KEY_USER_ID] = userId
        }
    }

    suspend fun setServerSubscription(active: Boolean, plan: String?, endsAt: String?, yearlyPrice: Int?) {
        store.edit {
            it[KEY_SERVER_ACTIVE] = active
            if (plan.isNullOrBlank()) it.remove(KEY_SERVER_PLAN) else it[KEY_SERVER_PLAN] = plan
            if (endsAt.isNullOrBlank()) it.remove(KEY_SERVER_ENDS) else it[KEY_SERVER_ENDS] = endsAt
            if (yearlyPrice != null) it[KEY_YEARLY_PRICE] = yearlyPrice
        }
    }

    suspend fun setApiBaseUrl(url: String?) {
        store.edit {
            if (url.isNullOrBlank()) it.remove(KEY_API_BASE) else it[KEY_API_BASE] = url.trimEnd('/')
        }
    }

    suspend fun clearAuth() {
        store.edit {
            it.remove(KEY_ACCESS)
            it.remove(KEY_REFRESH)
            it.remove(KEY_PHONE)
            it.remove(KEY_NAME)
            it.remove(KEY_USER_ID)
            it[KEY_SERVER_ACTIVE] = false
            it.remove(KEY_SERVER_PLAN)
            it.remove(KEY_SERVER_ENDS)
        }
    }

    suspend fun getAccessToken(): String? = store.data.first()[KEY_ACCESS]
    suspend fun getRefreshToken(): String? = store.data.first()[KEY_REFRESH]
    suspend fun getApiBaseUrl(): String? = store.data.first()[KEY_API_BASE]

    /** Raw: tokens present? */
    suspend fun hasSessionTokensNow(): Boolean {
        val snap = store.data.first()
        return !snap[KEY_ACCESS].isNullOrBlank() && !snap[KEY_REFRESH].isNullOrBlank()
    }

    /**
     * Sync gate for widgets / background.
     * Tokens required. Active server subscription unlocks; local demo flag only if tokens exist
     * (AuthRepository Flow further restricts local to DEBUG).
     */
    suspend fun isPremiumEffectiveNow(): Boolean {
        val snap = store.data.first()
        if (snap[KEY_ACCESS].isNullOrBlank() || snap[KEY_REFRESH].isNullOrBlank()) return false
        if (snap[KEY_SERVER_ACTIVE] == true) return true
        return snap[KEY_PREMIUM] == true
    }
}
