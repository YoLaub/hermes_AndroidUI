package com.example.hermes

import android.content.Context
import com.example.hermes.core.data.CalendarRepository
import com.example.hermes.core.data.ForumRepository
import com.example.hermes.core.data.HermesPreferences
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.data.NotesRepository
import com.example.hermes.core.mobilecontrol.MobileControlManager

/**
 * Process-scoped dependencies. Anything that must outlive an activity (repositories and above all the
 * mobile-control manager with its relay WebSocket) lives here, not in a composable.
 * Objects tied to one ViewModel's lifetime (speech and audio managers) are NOT here: their
 * ViewModel destroys them in onCleared(), so they are created with it.
 */
class AppContainer(val appContext: Context) {
    val preferences: HermesPreferences by lazy { HermesPreferences(appContext) }
    val repository: HermesRepository by lazy { HermesRepository(preferences) }
    val notesRepository: NotesRepository by lazy { NotesRepository(appContext) }
    val calendarRepository: CalendarRepository by lazy { CalendarRepository(appContext) }
    val forumRepository: ForumRepository by lazy { ForumRepository(appContext) }
    val mobileControlManager: MobileControlManager by lazy { MobileControlManager(appContext, preferences) }
}
