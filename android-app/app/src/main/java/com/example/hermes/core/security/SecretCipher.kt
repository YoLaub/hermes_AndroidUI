package com.example.hermes.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface SecretCipher {
    /** Returns a self-describing sealed string; throws if sealing is impossible. */
    fun encrypt(plain: String): String

    /** Returns the secret, or null if [stored] is not sealed, is damaged, or the key does not match. */
    fun decrypt(stored: String): String?
}

/**
 * AES-256-GCM with a random IV per value. The key comes from [keyProvider] so tests use an in-memory
 * key and production uses the Android Keystore (see [keystoreSecretCipher]).
 * Layout: `enc:v1:` + Base64(iv(12) + ciphertext + 16-byte tag).
 */
@OptIn(ExperimentalEncodingApi::class)
class AesGcmSecretCipher(private val keyProvider: () -> SecretKey) : SecretCipher {

    companion object {
        const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())   // the provider generates the IV
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.Default.encode(sealed)
    }

    override fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return null
        return try {
            val raw = Base64.Default.decode(stored.removePrefix(PREFIX))
            if (raw.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            null  // tampered, wrong key, malformed or keystore unavailable: treated as "no secret"
        }
    }
}

private const val KEY_ALIAS = "hermes_secrets_v1"

private class KeystoreKeyHolder {
    @Volatile
    private var key: SecretKey? = null

    fun get(): SecretKey = key ?: synchronized(this) { key ?: loadOrCreateKey().also { key = it } }
}

/** Production cipher: a non-exportable AES key held by the Android Keystore. */
fun keystoreSecretCipher(): SecretCipher {
    val holder = KeystoreKeyHolder()
    return AesGcmSecretCipher { holder.get() }
}

private fun loadOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
    )
    return generator.generateKey()
}
