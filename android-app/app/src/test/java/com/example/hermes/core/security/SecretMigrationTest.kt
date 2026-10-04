package com.example.hermes.core.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class SecretMigrationTest {

    private val cipher = AesGcmSecretCipher { KEY }
    private val codec = SecretCodec(cipher)

    companion object {
        private val KEY = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    @Test
    fun theSecretKeysAreExactlyTheFourPersistedSecrets() {
        assertEquals(
            setOf("session_cookie", "password", "openbao_token", "mobile_device_token"),
            SecretKeys.NAMES
        )
    }

    @Test
    fun openReturnsSealedValuesAndFlagsLegacyPlaintext() {
        val sealed = codec.seal("tok_abc")
        val a = codec.open(sealed)
        assertEquals("tok_abc", a.value)
        assertFalse(a.wasLegacyPlaintext)

        val b = codec.open("tok_legacy_plaintext")
        assertEquals("tok_legacy_plaintext", b.value)
        assertTrue(b.wasLegacyPlaintext)
    }

    @Test
    fun anUndecryptableSealedValueIsTreatedAsUnsetNotReturnedRaw() {
        val other = SecretCodec(AesGcmSecretCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() })
        val r = codec.open(other.seal("tok_abc"))
        assertNull(r.value)
        assertFalse(r.wasLegacyPlaintext)
    }

    @Test
    fun migrationRewritesOnlyLegacyPlaintextSecrets() {
        val stored = mapOf(
            "password" to "hunter2",
            "openbao_token" to codec.seal("already_sealed"),
            "mobile_device_token" to "tok_device_plain",
            "server_url" to "http://10.0.2.2:8000",       // not a secret: untouched
            "last_session_id" to "abc"                      // not a secret: untouched
        )
        val rewrites = SecretMigration.legacyToSealed(stored, SecretKeys.NAMES, codec)
        assertEquals(setOf("password", "mobile_device_token"), rewrites.keys)
        assertEquals("hunter2", codec.open(rewrites.getValue("password")).value)
        assertFalse(rewrites.getValue("password").contains("hunter2"))
        assertEquals("tok_device_plain", codec.open(rewrites.getValue("mobile_device_token")).value)
    }

    @Test
    fun migrationIsIdempotent() {
        val stored = mutableMapOf("password" to "hunter2")
        stored.putAll(SecretMigration.legacyToSealed(stored, SecretKeys.NAMES, codec))
        assertTrue(SecretMigration.legacyToSealed(stored, SecretKeys.NAMES, codec).isEmpty())
    }

    @Test
    fun ifSealingFailsTheLegacyValueIsLeftForTheNextAttemptNotLost() {
        val broken = SecretCodec(AesGcmSecretCipher { throw IllegalStateException("keystore unavailable") })
        val stored = mapOf("password" to "hunter2")
        assertTrue(SecretMigration.legacyToSealed(stored, SecretKeys.NAMES, broken).isEmpty())
    }
}
