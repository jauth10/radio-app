package com.iu.radioapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.UUID
import kotlin.time.Instant

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
     * Generating inside DataStore's edit block is what keeps it stable: the
     * "is it still missing?" check sits inside edit, which serialises concurrent
     * writes, so two collectors starting at the same moment cannot end up with
     * two different ids - even though both of them saw null a moment earlier.
     *
     * Read first, write only when there is nothing to read. DataStore's edit
     * always takes the exclusive single-writer path, even when the lambda turns
     * out to be a no-op - calling it on every read would serialise every single
     * lookup of the id behind the write lock, and this id is needed for every
     * song request and every rating.
     *
     * The write still happens before the store is collected, not inside the
     * collector: a write waiting on a read of the same store is a deadlock
     * waiting to be reproduced on a slow device.
     */
    val listenerId: Flow<String> = flow {
        if (dataStore.data.first()[KEY_LISTENER_ID] == null) {
            ensureListenerId()
        }
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

    /** Token and expiry read together, so a reader never sees one without the other. */
    val hostSession: Flow<StoredHostSession?> = dataStore.data.map { preferences ->
        preferences[KEY_HOST_SESSION_TOKEN]?.let(tokenCipher::decrypt)?.let { token ->
            StoredHostSession(
                token = token,
                validUntil = preferences[KEY_HOST_SESSION_VALID_UNTIL]?.let(Instant::fromEpochMilliseconds),
                startedAt = preferences[KEY_HOST_SESSION_STARTED_AT]?.let(Instant::fromEpochMilliseconds),
            )
        }
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

    suspend fun setHostSession(token: String, validUntil: Instant, startedAt: Instant) {
        val cipherText = tokenCipher.encrypt(token)
        dataStore.edit { preferences ->
            preferences[KEY_HOST_SESSION_TOKEN] = cipherText
            preferences[KEY_HOST_SESSION_VALID_UNTIL] = validUntil.toEpochMilliseconds()
            preferences[KEY_HOST_SESSION_STARTED_AT] = startedAt.toEpochMilliseconds()
        }
    }

    /** Ends the host session. The listener id and the display name stay. */
    suspend fun clearHostSession() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_HOST_SESSION_TOKEN)
            preferences.remove(KEY_HOST_SESSION_VALID_UNTIL)
            preferences.remove(KEY_HOST_SESSION_STARTED_AT)
        }
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
        private val KEY_HOST_SESSION_VALID_UNTIL = longPreferencesKey("host_session_valid_until")
        private val KEY_HOST_SESSION_STARTED_AT = longPreferencesKey("host_session_started_at")
    }
}

data class StoredHostSession(val token: String, val validUntil: Instant?, val startedAt: Instant?)
