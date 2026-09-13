package com.iu.radioapp.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption with a key that never leaves the Android keystore.
 *
 * The key material stays in system-managed storage; this process only ever holds
 * a handle to it, so the token cannot be recovered from a backup or from the
 * DataStore file alone. GCM is used because it authenticates as well as encrypts:
 * a manipulated stored value fails to decrypt instead of returning garbage.
 *
 * A fresh initialisation vector is generated per encryption - reusing one with
 * GCM would break the scheme - and stored in front of the ciphertext. The whole
 * thing is Base64 encoded because DataStore Preferences holds strings.
 *
 * Deliberately no androidx.security:security-crypto: that library is deprecated,
 * is not in the version catalog, and its EncryptedSharedPreferences would add a
 * second key-value store next to DataStore.
 */
class KeystoreTokenCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : TokenCipher {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + cipherBytes, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String? = try {
        val stored = Base64.decode(cipherText, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, stored, 0, IV_LENGTH_BYTES),
        )
        val plainBytes = cipher.doFinal(stored, IV_LENGTH_BYTES, stored.size - IV_LENGTH_BYTES)
        String(plainBytes, Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        // Key gone or value tampered with: treat the session as expired.
        null
    } catch (e: IllegalArgumentException) {
        // Stored text was not valid Base64, same conclusion.
        null
    }

    /** Returns the existing key or creates it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_KEY_ALIAS = "com.iu.radioapp.host_session_token"
        const val KEY_SIZE_BITS = 256
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
    }
}
