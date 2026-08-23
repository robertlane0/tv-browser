package com.example.tvbrowser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    fun globalUaDefault(): Flow<UaMode> =
        dataStore.data.map { prefs ->
            runCatching { UaMode.valueOf(prefs[GLOBAL_UA_DEFAULT] ?: "") }
                .getOrDefault(UaMode.DESKTOP)
        }

    fun contentFilterEnabled(): Flow<Boolean> =
        dataStore.data.map { it[CONTENT_FILTER_ENABLED] ?: false }

    fun webviewVersionWarnedMajor(): Flow<Int> =
        dataStore.data.map { it[WEBVIEW_VERSION_WARNED_MAJOR] ?: 0 }

    fun cleartextNoticeShown(): Flow<Boolean> =
        dataStore.data.map { it[CLEARTEXT_NOTICE_SHOWN] ?: false }

    suspend fun setGlobalUaDefault(mode: UaMode) {
        dataStore.edit { it[GLOBAL_UA_DEFAULT] = mode.name }
    }

    suspend fun setContentFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[CONTENT_FILTER_ENABLED] = enabled }
    }

    suspend fun setWebviewVersionWarnedMajor(major: Int) {
        dataStore.edit { it[WEBVIEW_VERSION_WARNED_MAJOR] = major }
    }

    suspend fun setCleartextNoticeShown(shown: Boolean) {
        dataStore.edit { it[CLEARTEXT_NOTICE_SHOWN] = shown }
    }

    companion object {
        val GLOBAL_UA_DEFAULT = stringPreferencesKey("global_ua_default")
        val CONTENT_FILTER_ENABLED = booleanPreferencesKey("content_filter_enabled")
        val WEBVIEW_VERSION_WARNED_MAJOR = intPreferencesKey("webview_version_warned_major")
        val CLEARTEXT_NOTICE_SHOWN = booleanPreferencesKey("cleartext_notice_shown")

        @Volatile
        private var instance: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: PreferencesRepository(
                    PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    ) { context.applicationContext.preferencesDataStoreFile("settings") }
                ).also { instance = it }
            }
    }
}
