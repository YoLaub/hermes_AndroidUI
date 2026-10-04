package com.example.hermes.core.security

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretCipherTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val cipher = AesGcmSecretCipher { key }

    @Test
    fun roundTripReturnsTheOriginalSecret() {
        val sealed = cipher.encrypt("tok_super_secret_123")
        assertEquals("tok_super_secret_123", cipher.decrypt(sealed))
    }

    @Test
    fun sealedValueIsMarkedAndNeverContainsThePlaintext() {
        val sealed = cipher.encrypt("tok_super_secret_123")
        assertTrue(sealed.startsWith(AesGcmSecretCipher.PREFIX))
        assertFalse(sealed.contains("tok_super_secret_123"))
        val raw = Base64.getDecoder().decode(sealed.removePrefix(AesGcmSecretCipher.PREFIX))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("tok_super_secret_123"))
    }

    @Test
    fun theSameSecretSealsDifferentlyEachTimeBecauseTheIvIsRandom() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun aTamperedValueIsRejectedNotDecryptedToGarbage() {
        val sealed = cipher.encrypt("tok_super_secret_123")
        val raw = Base64.getDecoder().decode(sealed.removePrefix(AesGcmSecretCipher.PREFIX))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = AesGcmSecretCipher.PREFIX + Base64.getEncoder().encodeToString(raw)
        assertNull(cipher.decrypt(tampered))
    }

    @Test
    fun aValueSealedWithAnotherKeyCannotBeOpened() {
        val other = AesGcmSecretCipher { newKey() }
        assertNull(cipher.decrypt(other.encrypt("tok_super_secret_123")))
    }

    @Test
    fun notSealedOrMalformedInputDecryptsToNull() {
        for (bad in listOf("", "plain-token", "enc:v1:", "enc:v1:@@@not-base64@@@", "enc:v1:" + Base64.getEncoder().encodeToString(ByteArray(5)))) {
            assertNull("input: $bad", cipher.decrypt(bad))
        }
    }

    @Test
    fun aMissingKeyMeansNothingIsDecryptedAndNothingIsLeaked() {
        val broken = AesGcmSecretCipher { throw IllegalStateException("keystore unavailable") }
        assertNull(broken.decrypt(cipher.encrypt("x")))
        assertThrows(IllegalStateException::class.java) { broken.encrypt("x") }
    }
}
