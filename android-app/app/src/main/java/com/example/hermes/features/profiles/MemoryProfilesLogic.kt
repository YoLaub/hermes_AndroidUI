package com.example.hermes.features.profiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Given from the terminal only: the service refuses them through the API, so the app never offers them. */
internal val ADMIN_ONLY_ZONES = listOf("secrets", "finance")

/** The zones the app may toggle, most sensitive first. */
internal val TOGGLE_ZONES = listOf("health", "legal", "personal", "professional", "git", "other")

private val PROFILE_NAME = Regex("[a-z0-9_-]{1,100}")

internal fun isValidProfileName(name: String): Boolean = PROFILE_NAME.matches(name)

/**
 * The list to send for one side (read or write). An admin-only zone is kept only if the profile already holds it in
 * that same list; it is never added. The result follows the sensitivity order.
 */
internal fun grantsToSend(held: List<String>, selected: Set<String>): List<String> =
    (ADMIN_ONLY_ZONES + TOGGLE_ZONES).filter { zone ->
        if (zone in ADMIN_ONLY_ZONES) zone in held else zone in selected
    }

/**
 * The service's own wording when it refuses (editing off, a zone only the admin gives), shown as is; the usual
 * messages otherwise. Never the raw response.
 */
internal fun profileErrorMessage(error: Throwable): String {
    val text = error.message.orEmpty()
    val detail = Regex("^HTTP (403|409|422): (.*)$", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(2)
        ?.let { body -> runCatching { (Json.parseToJsonElement(body) as? JsonObject)?.get("detail") as? JsonPrimitive }.getOrNull() }
        ?.takeIf { it.isString }?.contentOrNull
    return detail?.takeIf { it.isNotBlank() } ?: validationErrorMessage(error)
}
