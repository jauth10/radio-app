package com.iu.radioapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import java.io.IOException

class InMemoryPreferencesDataStore(private val fault: LocalFault = LocalFault()) : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state.onEach { checkReadable() }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        checkReadable()
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    // DataStore reports an unreadable file as IOException.
    private fun checkReadable() {
        if (fault.broken) throw IOException("simulated: preferences file unreadable")
    }
}

class FakeTokenCipher : TokenCipher {
    override fun encrypt(plainText: String): String = "enc:$plainText"
    override fun decrypt(cipherText: String): String? = cipherText.removePrefix("enc:")
}

fun inMemoryUserPreferences(fault: LocalFault = LocalFault()): UserPreferencesDataSource =
    UserPreferencesDataSource(InMemoryPreferencesDataStore(fault), FakeTokenCipher())
