package com.iu.radioapp.interactor

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HostInteractorTest {

    private val fixture = InteractorFixture()
    private val interactor = fixture.hostInteractor

    @Test
    fun `login uses the listener id as device id and activates the session`() = runTest {
        val listenerId = (fixture.listenerRepository.getListener() as Outcome.Success).value.listenerId

        val session = (interactor.login(" 1234 ") as Outcome.Success).value

        assertEquals("host-1", session.host.hostId)
        assertEquals("fake-session-$listenerId", fixture.preferences.hostSessionToken.first())
        assertEquals(true, interactor.observeSessionActive().first())
    }

    @Test
    fun `invalid code is unauthorized and leaves the session inactive`() = runTest {
        fixture.playout.nextFailure = Failure.Unauthorized

        assertEquals(Outcome.Error(Failure.Unauthorized), interactor.login("0000"))
        assertEquals(false, interactor.observeSessionActive().first())
    }

    @Test
    fun `endSession deactivates the session`() = runTest {
        interactor.login("1234")

        assertEquals(Outcome.Success(Unit), interactor.endSession())

        assertEquals(false, interactor.observeSessionActive().first())
    }

    @Test
    fun `aggregate is read for the show on air`() = runTest {
        val aggregate = checkNotNull((interactor.getCurrentAggregate() as Outcome.Success).value)

        assertEquals(1, aggregate.playlistRatingCount)
        assertEquals(4.0, aggregate.averagePlaylistRating, 0.0)
    }

    @Test
    fun `talk segment yields no aggregate`() = runTest {
        fixture.playout.currentTrack = null

        assertEquals(Outcome.Success(null), interactor.getCurrentAggregate())
    }

    @Test
    fun `unauthorized from the aggregate is passed on`() = runTest {
        fixture.feedback.nextFailure = Failure.Unauthorized

        assertEquals(Outcome.Error(Failure.Unauthorized), interactor.getCurrentAggregate())
    }

    @Test
    fun `offline without a cached show is passed on as connection failure`() = runTest {
        fixture.playout.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), interactor.getCurrentAggregate())
    }
}
