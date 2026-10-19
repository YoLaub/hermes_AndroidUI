package com.example.hermes.features.profiles

import java.text.Normalizer

enum class EnvKeyError { Required, StartsWithDigit, Invalid }

private val ENV_KEY = Regex("[A-Z_][A-Z0-9_]*")

/** What the user types, as an env name: accents dropped, separators to "_", upper case, ASCII only. */
fun sanitizeEnvKey(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase()
        .replace(Regex("[ .\\-]"), "_")
        .filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }

fun envKeyError(key: String): EnvKeyError? = when {
    key.isEmpty() -> EnvKeyError.Required
    key.first().isDigit() -> EnvKeyError.StartsWithDigit
    !ENV_KEY.matches(key) -> EnvKeyError.Invalid
    else -> null
}
