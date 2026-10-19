package com.example.hermes.ui.components

import com.example.hermes.core.model.ProfileInfo

/** An agent as the UI shows it. Built from the server's profile list, never written in code. */
data class Persona(val id: String, val name: String, val initials: String, val model: String? = null)

fun initialsOf(name: String): String {
    val words = name.trim().split(' ', '-', '_').filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
        else -> words[0].take(2).uppercase()
    }
}

fun personaOf(profile: ProfileInfo): Persona {
    val name = profile.name.replaceFirstChar { it.uppercase() }
    return Persona(id = profile.name, name = name, initials = initialsOf(name), model = profile.model)
}

fun personasOf(profiles: List<ProfileInfo>): List<Persona> = profiles.map(::personaOf)

/** The persona for [id], or one derived from the id when the profile list does not have it. */
fun personaOrFallback(personas: List<Persona>, id: String): Persona =
    personas.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
        ?: personaOf(ProfileInfo(name = id))
