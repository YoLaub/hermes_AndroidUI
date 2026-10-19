package com.example.hermes.features.agent

import com.example.hermes.core.model.ProfileInfo

/** The only profile mobile control accepts (also enforced by the app's manager and by the relay). */
const val CONTROL_PROFILE = "john"

fun isControlProfile(name: String): Boolean = name == CONTROL_PROFILE

/**
 * The active profile and the default one stay; any other can be deleted (after a confirmation).
 * Deletion goes by name, so a profile that shares its name with the default one is kept too.
 */
fun canDeleteProfile(profile: ProfileInfo, active: String, all: List<ProfileInfo> = listOf(profile)): Boolean =
    !profile.isDefault && profile.name != active && all.none { it.name == profile.name && it.isDefault }

fun profileSubtitle(profile: ProfileInfo): String {
    val model = profile.model ?: return "Modèle par défaut"
    return when (profile.skillCount) {
        0 -> model
        1 -> "$model · 1 compétence"
        else -> "$model · ${profile.skillCount} compétences"
    }
}
