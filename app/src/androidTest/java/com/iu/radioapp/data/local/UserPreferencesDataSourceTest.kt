package com.iu.radioapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UserPreferencesDataSourceTest {

    private lateinit var storeFile: File
    private lateinit var dataStore: CountingDataStore
    private lateinit var preferences: UserPreferencesDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storeFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        dataStore = CountingDataStore(PreferenceDataStoreFactory.create { storeFile })
        preferences = UserPreferencesDataSource(
            dataStore = dataStore,
            tokenCipher = KeystoreTokenCipher(keyAlias = "com.iu.radioapp.test.token"),
        )
    }

    @After
    fun tearDown() {
        storeFile.delete()
    }

    /**
     * The listener id is the only thing tying a request or a rating to a person;
     * there are no accounts. If it changed between two reads, the station would
     * see every request as coming from someone new and the per-hour limits would
     * stop working.
     */
    @Test
    fun theListenerIdStaysTheSameAcrossReads() = runTest {
        val first = preferences.listenerId.first()
        val second = preferences.listenerId.first()
        val third = preferences.requireListenerId()

        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun theListenerIdSurvivesANewInstanceOnTheSameStore() = runTest {
        val first = preferences.listenerId.first()

        val reopened = UserPreferencesDataSource(
            dataStore = dataStore,
            tokenCipher = KeystoreTokenCipher(keyAlias = "com.iu.radioapp.test.token"),
        )

        assertEquals(first, reopened.listenerId.first())
    }

    @Test
    fun theListenerIdIsAUuid() = runTest {
        val listenerId = preferences.listenerId.first()

        assertEquals(listenerId, UUID.fromString(listenerId).toString())
    }

    @Test
    fun theDisplayNameIsOptionalAndCanBeClearedAgain() = runTest {
        assertNull(preferences.displayName.first())

        preferences.setDisplayName("Jasper")
        assertEquals("Jasper", preferences.displayName.first())

        preferences.setDisplayName(null)
        assertNull(preferences.displayName.first())
    }

    @Test
    fun theSessionTokenComesBackDecrypted() = runTest {
        preferences.setHostSessionToken("session-token-value")

        assertEquals("session-token-value", preferences.hostSessionToken.first())
    }

    /** What lands in the file must not be the token itself. */
    @Test
    fun theSessionTokenIsNotStoredInPlainText() = runTest {
        preferences.setHostSessionToken("session-token-value")

        val stored = dataStore.data.first().asMap().values.map(Any::toString)
        assertNotEquals(emptyList<String>(), stored)
        assert(stored.none { it.contains("session-token-value") }) {
            "the plain token must not appear in the store"
        }
    }

    /**
     * Review finding from PR #5: the id used to be written through
     * DataStore.edit on every single read, which pushed every lookup through the
     * exclusive writer path. Counting the writes is the only way to state that
     * as a test instead of as a claim - DataStore is an interface, so a thin
     * wrapper can observe it.
     */
    @Test
    fun readingTheListenerIdRepeatedlyWritesOnlyOnce() = runTest {
        val first = preferences.requireListenerId()
        val writesAfterFirstRead = dataStore.writes

        repeat(4) { preferences.requireListenerId() }

        assertEquals("creating the id is one write", 1, writesAfterFirstRead)
        assertEquals("further reads must not write at all", 1, dataStore.writes)
        assertEquals(first, preferences.requireListenerId())
    }

    @Test
    fun endingTheHostSessionKeepsTheListenerIdentity() = runTest {
        val listenerId = preferences.listenerId.first()
        preferences.setDisplayName("Jasper")
        preferences.setHostSessionToken("session-token-value")

        preferences.clearHostSession()

        assertNull(preferences.hostSessionToken.first())
        assertEquals(listenerId, preferences.listenerId.first())
        assertEquals("Jasper", preferences.displayName.first())
    }

    /**
     * Passes everything through and counts the writes. [data] is delegated as a
     * property getter so reads stay untouched.
     */
    private class CountingDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> {

        var writes = 0
            private set

        override val data: Flow<Preferences> get() = delegate.data

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            writes++
            return delegate.updateData(transform)
        }
    }
}
