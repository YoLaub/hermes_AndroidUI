package com.example.hermes

import android.content.Context
import com.example.hermes.core.data.AvatarStore
import com.example.hermes.core.data.HermesPreferences
import com.example.hermes.core.data.HermesRepository
import com.example.hermes.core.mobilecontrol.AgentPhoneLink
import com.example.hermes.core.mobilecontrol.ControlPolicy
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
    val avatarStore: AvatarStore by lazy { AvatarStore(appContext) }
    val mobileControlManager: MobileControlManager by lazy {
        // The strict policy unless this is the agent-phone edition.
        MobileControlManager(appContext, preferences, if (BuildConfig.AGENT_PHONE) ControlPolicy.LITE else ControlPolicy.FULL)
    }

    /** Only used by the agent-phone edition: keeps the relay connection open from the stored pairing. */
    val agentPhoneLink: AgentPhoneLink by lazy { AgentPhoneLink(mobileControlManager, preferences) }
}
