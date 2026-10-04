package com.example.hermes.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import com.example.hermes.core.security.SecretCodec
import com.example.hermes.core.security.SecretKeys
import com.example.hermes.core.security.SecretCipher
import com.example.hermes.core.security.SecretMigration
import com.example.hermes.core.security.keystoreSecretCipher
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hermes_preferences")

class HermesPreferences(
    private val context: Context,
    cipher: SecretCipher = keystoreSecretCipher()
) {

    private val codec = SecretCodec(cipher)

    /**
     * A secret as a flow. The raw value is compared first so a change to an unrelated preference does
     * not decrypt again, and keystore work runs off the collector's thread (often the main one).
     */
    private fun secretFlow(key: Preferences.Key<String>): Flow<String?> =
        context.dataStore.data
            .map { it[key] }
            .distinctUntilChanged()
            .map { stored -> stored?.let { codec.open(it).value } }
            .flowOn(Dispatchers.IO)

    /**
     * Stores a secret sealed and returns whether it was stored. If sealing is impossible (keystore
     * unavailable) nothing is written in plaintext and the previous value is removed, so a stale
     * credential is never used silently: the caller can tell the user and they re-enter it.
     */
    private fun MutablePreferences.putSecret(key: Preferences.Key<String>, value: String): Boolean {
        val sealed = codec.sealOrNull(value)
        if (sealed == null) {
            Log.w("HermesPreferences", "event=secret_not_stored key=${key.name}")
            remove(key)
            return false
        }
        this[key] = sealed
        return true
    }

    /** Rewrites secrets written in plaintext by older versions. Idempotent; safe to run at every start. */
    suspend fun migrateSecrets() {
        context.dataStore.edit { preferences ->
            val stored = SecretKeys.NAMES.mapNotNull { name ->
                preferences[stringPreferencesKey(name)]?.let { name to it }
            }.toMap()
            SecretMigration.legacyToSealed(stored, SecretKeys.NAMES, codec).forEach { (name, sealed) ->
                preferences[stringPreferencesKey(name)] = sealed
            }
        }
    }

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
        val KEY_MOBILE_DEVICE_ID = stringPreferencesKey("mobile_device_id")
        val KEY_MOBILE_DEVICE_TOKEN = stringPreferencesKey("mobile_device_token")
        val KEY_MOBILE_RELAY_URL = stringPreferencesKey("mobile_relay_url")
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8000"
        const val DEFAULT_OPENBAO_URL = "https://hermes-bao.john-world.store"
        const val DEFAULT_OPENBAO_MOUNT = "hermes"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val mobileDeviceId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_MOBILE_DEVICE_ID]
    }

    val mobileDeviceToken: Flow<String?> = secretFlow(KEY_MOBILE_DEVICE_TOKEN)

    val mobileRelayUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_MOBILE_RELAY_URL]
    }

    val sessionCookie: Flow<String?> = secretFlow(KEY_SESSION_COOKIE)

    val password: Flow<String?> = secretFlow(KEY_PASSWORD)

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
        preferences[KEY_OPENBAO_URL] ?: DEFAULT_OPENBAO_URL
    }

    val openbaoToken: Flow<String?> = secretFlow(KEY_OPENBAO_TOKEN)

    val openbaoMount: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPENBAO_MOUNT] ?: DEFAULT_OPENBAO_MOUNT
    }

    suspend fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = cleanUrl
        }
    }

    /** Returns false if the cookie could not be stored securely (and was therefore not stored). */
    suspend fun setSessionCookie(cookie: String?): Boolean {
        var stored = true
        context.dataStore.edit { preferences ->
            if (cookie != null) {
                stored = preferences.putSecret(KEY_SESSION_COOKIE, cookie)
            } else {
                preferences.remove(KEY_SESSION_COOKIE)
            }
        }
        return stored
    }

    suspend fun setPassword(pwd: String?): Boolean {
        var stored = true
        context.dataStore.edit { preferences ->
            if (!pwd.isNullOrBlank()) {
                stored = preferences.putSecret(KEY_PASSWORD, pwd)
            } else {
                preferences.remove(KEY_PASSWORD)
            }
        }
        return stored
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

    suspend fun setOpenbaoToken(token: String?): Boolean {
        var stored = true
        context.dataStore.edit { preferences ->
            if (token != null && token.isNotBlank()) {
                stored = preferences.putSecret(KEY_OPENBAO_TOKEN, token.trim())
            } else {
                preferences.remove(KEY_OPENBAO_TOKEN)
            }
        }
        return stored
    }

    suspend fun setOpenbaoMount(mount: String) {
        val cleanMount = mount.trim().trim('/')
        context.dataStore.edit { preferences ->
            preferences[KEY_OPENBAO_MOUNT] = cleanMount.ifBlank { DEFAULT_OPENBAO_MOUNT }
        }
    }

    suspend fun setMobileDeviceId(deviceId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MOBILE_DEVICE_ID] = deviceId.trim()
        }
    }

    /** Returns false if the token could not be stored securely (pairing must then be reported as failed). */
    suspend fun setMobileDeviceToken(token: String?): Boolean {
        var stored = true
        context.dataStore.edit { preferences ->
            if (token != null && token.isNotBlank()) {
                stored = preferences.putSecret(KEY_MOBILE_DEVICE_TOKEN, token.trim())
            } else {
                preferences.remove(KEY_MOBILE_DEVICE_TOKEN)
            }
        }
        return stored
    }

    suspend fun setMobileRelayUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        context.dataStore.edit { preferences ->
            preferences[KEY_MOBILE_RELAY_URL] = cleanUrl
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_SESSION_COOKIE)
            preferences.remove(KEY_LAST_SESSION_ID)
        }
    }
}
