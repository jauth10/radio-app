package com.iu.radioapp.data.local

/**
 * Encrypts the host session token before it is written to DataStore.
 *
 * An interface rather than a single class for two reasons. It keeps the key
 * handling out of UserPreferencesDataSource, which has enough to do storing three
 * values; and it lets a test exercise the preferences without the Android
 * keystore, which only exists on a device.
 *
 * [decrypt] returns null instead of throwing when the stored text cannot be read
 * back - a key can become invalid, for instance when the screen lock is reset.
 * That is not a programming error but an expired session, and the app reacts to
 * it the same way it reacts to Failure.Unauthorized: establish the host session
 * again. The listener path is unaffected, it has no token.
 */
interface TokenCipher {

    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String?
}
