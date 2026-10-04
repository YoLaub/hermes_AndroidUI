package com.example.hermes

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Owns the process-scoped container so state survives activity recreation. */
class HermesApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        // Seal secrets that an older version stored in plaintext. Readers cope in the meantime.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.preferences.migrateSecrets()
            } catch (e: Exception) {
                // Best effort: an unreadable DataStore must not crash every cold start.
                Log.w("HermesApp", "event=secret_migration_failed error=${e.javaClass.simpleName}")
            }
        }
    }
}
