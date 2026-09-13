package com.iu.radioapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
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
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: UserPreferencesDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storeFile = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { storeFile }
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
}
