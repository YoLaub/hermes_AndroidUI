package com.example.hermes.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Tab roots
@Serializable data object ChatRoute : NavKey
@Serializable data object InboxRoute : NavKey
@Serializable data object SpaceRoute : NavKey
@Serializable data object ControlRoute : NavKey
@Serializable data object AgentRoute : NavKey

// Pushed screens
@Serializable data object KanbanRoute : NavKey
@Serializable data object KanbanTaskRoute : NavKey
@Serializable data object NotesRoute : NavKey
@Serializable data object AgendaRoute : NavKey
@Serializable data object ForumRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object RelaySettingsRoute : NavKey
@Serializable data object SkillsRoute : NavKey
@Serializable data class SkillDetailRoute(val name: String) : NavKey
@Serializable data object MemoryRoute : NavKey
@Serializable data object WorkspacesRoute : NavKey
@Serializable data object GuardRoute : NavKey
@Serializable data object EnvRoute : NavKey
@Serializable data object OpenBaoRoute : NavKey
@Serializable data object NewMemoryProfileRoute : NavKey
@Serializable data object NewMandateRoute : NavKey

fun rootOf(tab: Tab): NavKey = when (tab) {
    Tab.Chat -> ChatRoute
    Tab.Inbox -> InboxRoute
    Tab.Space -> SpaceRoute
    Tab.Control -> ControlRoute
    Tab.Agent -> AgentRoute
}
