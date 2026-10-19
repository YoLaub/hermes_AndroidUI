package com.example.hermes.features.agent

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
