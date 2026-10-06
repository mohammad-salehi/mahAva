package com.mahava.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Lightweight preferences via DataStore (cached in user's Gradle home).
 * Profile domain data lives in Room; this holds UI session flags.
 */
class UserPreferences(private val context: Context) {
    private val store = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("mahava_prefs") }
    )

    private val KEY_LAST_UNLOCK = stringPreferencesKey("last_unlock_millis")
    private val KEY_SAMPLE_MODE = booleanPreferencesKey("sample_data_mode")

    val lastUnlockMillis: Flow<Long?> = store.data.map { it[KEY_LAST_UNLOCK]?.toLongOrNull() }
    val sampleDataMode: Flow<Boolean> = store.data.map { it[KEY_SAMPLE_MODE] ?: false }

    suspend fun setLastUnlock(millis: Long) {
        store.edit { it[KEY_LAST_UNLOCK] = millis.toString() }
    }

    suspend fun setSampleMode(enabled: Boolean) {
        store.edit { it[KEY_SAMPLE_MODE] = enabled }
    }
}
