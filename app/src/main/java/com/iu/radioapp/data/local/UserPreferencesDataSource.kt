package com.iu.radioapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID

/**
 * The three single values that are not worth a table: listener id, display name,
 * host session token.
 *
 * They live in DataStore and not in Room because none of them is a record. There
 * is exactly one of each, they are never queried, joined or counted, and nothing
 * about them has to be transactional - the criterion that put cache, history and
 * outbox into Room.
 */
class UserPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher,
) {

    /**
     * The listener id, created once on first access and kept from then on.
     *
     * There are no user accounts (it is deliberately out of scope), so this UUID
     * is the only thing that ties a song request or a rating to a person. It has
     * to survive every restart, which is why it is written the moment it is first
     * read instead of being generated per session.
     *
     * Generating inside DataStore's edit block is what keeps it stable: edit
     * serialises concurrent writes, so two collectors starting at the same moment
     * cannot end up with two different ids.
     *
     * The write happens before the store is collected, not inside the collector.
     * Writing from within a data collector would mean a write waiting on a read
     * of the same store, which is a deadlock waiting to be reproduced on a slow
     * device.
     */
    val listenerId: Flow<String> = flow {
        ensureListenerId()
        emitAll(dataStore.data.mapNotNull { it[KEY_LISTENER_ID] }.distinctUntilChanged())
    }

    /** Null while the listener has not given a name - doing so is voluntary. */
    val displayName: Flow<String?> = dataStore.data.map { it[KEY_DISPLAY_NAME] }

    /**
     * The decrypted host session token, or null when there is no usable session.
     *
     * Null also covers a stored value that can no longer be decrypted; see
     * [TokenCipher].
     */
    val hostSessionToken: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_HOST_SESSION_TOKEN]?.let(tokenCipher::decrypt)
    }

    /** Reads the listener id once, for callers that need a value and not a stream. */
    suspend fun requireListenerId(): String = listenerId.first()

    suspend fun setDisplayName(displayName: String?) {
        dataStore.edit { preferences ->
            if (displayName == null) {
                preferences.remove(KEY_DISPLAY_NAME)
            } else {
                preferences[KEY_DISPLAY_NAME] = displayName
            }
        }
    }

    /** Stores the token encrypted; the plain text never reaches the file. */
    suspend fun setHostSessionToken(token: String) {
        val cipherText = tokenCipher.encrypt(token)
        dataStore.edit { preferences -> preferences[KEY_HOST_SESSION_TOKEN] = cipherText }
    }

    /** Ends the host session. The listener id and the display name stay. */
    suspend fun clearHostSession() {
        dataStore.edit { preferences -> preferences.remove(KEY_HOST_SESSION_TOKEN) }
    }

    /** Writes a fresh id if there is none yet, and returns the one that is now stored. */
    private suspend fun ensureListenerId(): String {
        val preferences = dataStore.edit { editable ->
            if (editable[KEY_LISTENER_ID] == null) {
                editable[KEY_LISTENER_ID] = UUID.randomUUID().toString()
            }
        }
        return requireNotNull(preferences[KEY_LISTENER_ID]) {
            "listener id must exist after it was written"
        }
    }

    companion object {
        /** File name of the preferences store, used by the Hilt module. */
        const val STORE_NAME = "user_preferences"

        private val KEY_LISTENER_ID = stringPreferencesKey("listener_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_HOST_SESSION_TOKEN = stringPreferencesKey("host_session_token")
    }
}
