package com.example.hermes.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_preferences")

class HermesPreferences(private val context: Context) {

    companion object {
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val KEY_PASSWORD = stringPreferencesKey("password")
        val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val KEY_LAST_SESSION_ID = stringPreferencesKey("last_session_id")
        val KEY_LAST_WORKSPACE = stringPreferencesKey("last_workspace")
        val KEY_OPENBAO_URL = stringPreferencesKey("openbao_url")
        val KEY_OPENBAO_TOKEN = stringPreferencesKey("openbao_token")
        val KEY_OPENBAO_MOUNT = stringPreferencesKey("openbao_mount")
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
        const val DEFAULT_OPENBAO_MOUNT = "secret"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val sessionCookie: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SESSION_COOKIE]
    }

    val password: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_PASSWORD]
    }

    val activeProfile: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_PROFILE] ?: "default"
    }

    val lastSessionId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_SESSION_ID]
    }

    val lastWorkspace: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_WORKSPACE]
    }

    val openbaoUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPENBAO_URL] ?: ""
    }

    val openbaoToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPENBAO_TOKEN]
    }

    val openbaoMount: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPENBAO_MOUNT] ?: DEFAULT_OPENBAO_MOUNT
    }

    suspend fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
        }
    }

    suspend fun setSessionCookie(cookie: String?) {
        context.dataStore.edit { preferences ->
            if (cookie != null) {
                preferences[KEY_SESSION_COOKIE] = cookie
            } else {
                preferences.remove(KEY_SESSION_COOKIE)
            }
        }
    }

    suspend fun setPassword(pwd: String?) {
        context.dataStore.edit { preferences ->
            if (!pwd.isNullOrBlank()) {
                preferences[KEY_PASSWORD] = pwd
            } else {
                preferences.remove(KEY_PASSWORD)
            }
        }
    }

    suspend fun clearPassword() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PASSWORD)
        }
    }

    suspend fun setActiveProfile(profile: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_PROFILE] = profile
        }
    }

    suspend fun setLastSessionId(sessionId: String?) {
        context.dataStore.edit { preferences ->
            if (sessionId != null) {
                preferences[KEY_LAST_SESSION_ID] = sessionId
            } else {
                preferences.remove(KEY_LAST_SESSION_ID)
            }
        }
    }

    suspend fun setLastWorkspace(workspace: String?) {
        context.dataStore.edit { preferences ->
            if (workspace != null) {
                preferences[KEY_LAST_WORKSPACE] = workspace
            } else {
                preferences.remove(KEY_LAST_WORKSPACE)
            }
        }
    }

    suspend fun setOpenbaoUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_OPENBAO_URL] = cleanUrl
        }
    }

    suspend fun setOpenbaoToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token != null && token.isNotBlank()) {
                preferences[KEY_OPENBAO_TOKEN] = token.trim()
            } else {
                preferences.remove(KEY_OPENBAO_TOKEN)
            }
        }
    }

    suspend fun setOpenbaoMount(mount: String) {
        val cleanMount = mount.trim().trim('/')
        context.dataStore.edit { preferences ->
            preferences[KEY_OPENBAO_MOUNT] = cleanMount.ifBlank { DEFAULT_OPENBAO_MOUNT }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_COOKIE)
            preferences.remove(KEY_LAST_SESSION_ID)
        }
    }
}
