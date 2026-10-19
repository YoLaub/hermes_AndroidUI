package com.example.hermes.features.agent

import com.example.hermes.core.model.ProfileInfo

enum class WorkspacePathError { Required, NotAbsolute }

/** Why a workspace path cannot be added, or null when it can. The server resolves it; we only ask for an absolute one. */
fun workspacePathError(path: String): WorkspacePathError? {
    val p = path.trim()
    return when {
        p.isEmpty() -> WorkspacePathError.Required
        !p.startsWith("/") -> WorkspacePathError.NotAbsolute
        else -> null
    }
}

/** Whether an editor holds unsaved text; a missing original counts as empty. */
fun isDirty(original: String?, edited: String): Boolean = (original ?: "") != edited

/** The three files of « Mémoire & âme », with the section names the server expects. */
enum class MemorySection(val apiName: String, val fileName: String) {
    Memory("memory", "MEMORY.md"),
    User("user", "USER.md"),
    Soul("soul", "SOUL.md"),
}

enum class ProfileNameError { Required, Format, Taken }

private val PROFILE_ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

/** Why a new profile id cannot be used: the server's id format, and not one that exists. */
fun profileNameError(name: String, existing: List<ProfileInfo>): ProfileNameError? = when {
    name.isEmpty() -> ProfileNameError.Required
    !PROFILE_ID.matches(name) -> ProfileNameError.Format
    existing.any { it.name == name } -> ProfileNameError.Taken
    else -> null
}

/** The configuration a new profile starts from: the default profile, else the first one. */
fun defaultCloneSource(existing: List<ProfileInfo>): String? =
    (existing.firstOrNull { it.isDefault } ?: existing.firstOrNull())?.name
