package com.example.hermes.ui.components

import kotlinx.serialization.Serializable

/** Number of tints in HermesColors.AvatarPalette (all readable with dark text, AA). */
const val AVATAR_PALETTE_SIZE = 8

/** What the owner chose for one profile's avatar; any field may be absent. Stored on the phone only. */
@Serializable
data class AvatarSpec(val initials: String? = null, val color: Int? = null, val image: String? = null)

sealed interface AvatarLook {
    data class Image(val file: String) : AvatarLook
    data class Initials(val text: String, val color: Int) : AvatarLook
}

/** One or two letters or digits, upper case. */
fun sanitizeInitials(input: String): String = input.filter { it.isLetterOrDigit() }.take(2).uppercase()

/** A tint picked from the profile id, so each agent keeps the same colour without any setting. */
fun defaultColorIndex(profileId: String): Int = Math.floorMod(profileId.hashCode(), AVATAR_PALETTE_SIZE)

/** Image › custom initials and colour › the persona's initials and default colour. */
fun avatarLook(persona: Persona, spec: AvatarSpec?, imageExists: Boolean): AvatarLook {
    spec?.image?.let { if (imageExists) return AvatarLook.Image(it) }
    val initials = spec?.initials?.takeIf { it.isNotBlank() } ?: persona.initials
    val color = spec?.color?.takeIf { it in 0 until AVATAR_PALETTE_SIZE } ?: defaultColorIndex(persona.id)
    return AvatarLook.Initials(initials, color)
}

data class CropSquare(val left: Int, val top: Int, val size: Int)

/** The largest centred square of a width × height picture. */
fun centeredSquare(width: Int, height: Int): CropSquare {
    val size = minOf(width, height)
    return CropSquare(left = (width - size) / 2, top = (height - size) / 2, size = size)
}
