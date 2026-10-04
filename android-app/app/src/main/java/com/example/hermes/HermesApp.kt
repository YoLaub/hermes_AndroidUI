package com.example.hermes

import android.app.Application

/** Owns the process-scoped container so state survives activity recreation. */
class HermesApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
