package com.example.hermes.core.security

/** Names of the DataStore entries that hold secrets and must never be stored in plaintext. */
object SecretKeys {
    val NAMES: Set<String> = setOf("session_cookie", "password", "openbao_token", "mobile_device_token")
}

class SecretCodec(private val cipher: SecretCipher) {

    data class Opened(
        /** The secret, or null if it is sealed but cannot be opened (treated as unset). */
        val value: String?,
        /** True when the stored value was never sealed (written by an older version). */
        val wasLegacyPlaintext: Boolean
    )

    fun seal(plain: String): String = cipher.encrypt(plain)

    /** Null instead of an exception when sealing is impossible (keystore unavailable). */
    fun sealOrNull(plain: String): String? = try {
        seal(plain)
    } catch (e: Exception) {
        null
    }

    fun open(stored: String): Opened = when {
        stored.startsWith(AesGcmSecretCipher.PREFIX) -> Opened(cipher.decrypt(stored), false)
        // A sealed format this version does not know (a future "enc:v2:"): never handed back as a secret.
        stored.startsWith(SEALED_FAMILY) -> Opened(null, false)
        else -> Opened(stored, true)
    }

    private companion object {
        const val SEALED_FAMILY = "enc:"
    }
}

object SecretMigration {
    /**
     * Sealed replacements for the legacy plaintext secrets in [stored]. Already sealed values and
     * non-secret entries are left alone; a value that cannot be sealed is skipped (kept for the next try).
     */
    fun legacyToSealed(stored: Map<String, String>, secretKeys: Set<String>, codec: SecretCodec): Map<String, String> {
        val rewrites = mutableMapOf<String, String>()
        for (key in secretKeys) {
            val value = stored[key] ?: continue
            if (!codec.open(value).wasLegacyPlaintext) continue
            try {
                rewrites[key] = codec.seal(value)
            } catch (e: Exception) {
                // keystore unavailable: leave the legacy value in place, retry on the next start
            }
        }
        return rewrites
    }
}
