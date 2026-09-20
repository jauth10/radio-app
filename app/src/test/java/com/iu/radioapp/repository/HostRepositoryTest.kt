package com.iu.radioapp.repository

import app.cash.turbine.test
import com.iu.radioapp.data.local.inMemoryUserPreferences
import com.iu.radioapp.data.remote.s1playout.FakePlayoutDataSource
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class HostRepositoryTest {

    private val clock = MutableClock()
    private val playout = FakePlayoutDataSource(clock)
    private val preferences = inMemoryUserPreferences()
    private val repository = HostRepository(playout, preferences)

    @Test
    fun `login stores the token and returns the session`() = runTest {
        val session = (repository.login("1234", "device-1") as Outcome.Success).value

        assertEquals("host-1", session.host.hostId)
        assertEquals(TEST_NOW + 1.hours, session.validUntil)
        assertEquals("fake-session-device-1", preferences.hostSessionToken.first())
    }

    @Test
    fun `invalid code is unauthorized and stores nothing`() = runTest {
        playout.nextFailure = Failure.Unauthorized

        assertEquals(Outcome.Error(Failure.Unauthorized), repository.login("0000", "device-1"))
        assertNull(preferences.hostSessionToken.first())
    }

    @Test
    fun `too many attempts is a rejection and stores nothing`() = runTest {
        val tooMany = Failure.Rejected(reason = "too many attempts", retryable = true)
        playout.nextFailure = tooMany

        assertEquals(Outcome.Error(tooMany), repository.login("0000", "device-1"))
        assertNull(preferences.hostSessionToken.first())
    }

    @Test
    fun `connection failure is passed on`() = runTest {
        playout.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), repository.login("1234", "device-1"))
    }

    @Test
    fun `session is active between login and endSession`() = runTest {
        repository.observeSessionActive().test {
            assertEquals(false, awaitItem())
            repository.login("1234", "device-1")
            assertEquals(true, awaitItem())
            repository.endSession()
            assertEquals(false, awaitItem())
        }
    }
}
