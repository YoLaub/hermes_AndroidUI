package com.example.hermes

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object AuthRoute : NavKey
@Serializable data object ChatRoute : NavKey
@Serializable data object KanbanRoute : NavKey
@Serializable data object WorkspaceRoute : NavKey
@Serializable data object MobileControlRoute : NavKey
