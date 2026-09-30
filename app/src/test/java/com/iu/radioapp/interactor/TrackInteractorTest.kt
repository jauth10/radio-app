package com.iu.radioapp.interactor

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.NowPlayingState
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.repository.TEST_NOW
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TrackInteractorTest {

    private val fixture = InteractorFixture()
    private val interactor = fixture.trackInteractor
    private val playout = fixture.playout

    private suspend fun state(): NowPlayingState = (interactor.getNowPlaying() as Outcome.Success).value

    private suspend fun cachedAfter(age: Duration): NowPlayingState.Cached {
        state()
        fixture.clock.instant = TEST_NOW + age
        playout.nextFailure = Failure.Connection
        return state() as NowPlayingState.Cached
    }

    @Test
    fun `live track comes back as Live`() = runTest {
        val live = state() as NowPlayingState.Live

        assertEquals("trk-1", live.nowPlaying.playback.track.trackId)
        assertEquals("host-1", live.nowPlaying.show?.host?.hostId)
    }

    @Test
    fun `talk segment is its own state and not a failure`() = runTest {
        playout.currentTrack = null

        assertEquals(NowPlayingState.TalkSegment, state())
    }

    @Test
    fun `cache just under five minutes old is not stale`() = runTest {
        val cached = cachedAfter(4.minutes + 59.seconds)

        assertFalse(cached.isStale)
        assertEquals(TEST_NOW, cached.fetchedAt)
        assertEquals(Failure.Connection, cached.cause)
    }

    @Test
    fun `cache exactly five minutes old is not stale yet`() = runTest {
        assertFalse(cachedAfter(5.minutes).isStale)
    }

    @Test
    fun `cache just over five minutes old is stale`() = runTest {
        val cached = cachedAfter(5.minutes + 1.seconds)

        assertTrue(cached.isStale)
        assertEquals("trk-1", cached.nowPlaying.playback.track.trackId)
    }

    @Test
    fun `server failure with a cache carries Server as cause`() = runTest {
        state()
        playout.nextFailure = Failure.Server

        assertEquals(Failure.Server, (state() as NowPlayingState.Cached).cause)
    }

    @Test
    fun `connection failure without a cache is passed on`() = runTest {
        playout.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), interactor.getNowPlaying())
    }

    @Test
    fun `track detail merges the S1 duration with the S2 broadcastable flag`() = runTest {
        val onAir = (state() as NowPlayingState.Live).nowPlaying.playback.track

        val detail = (interactor.getTrackDetail(onAir) as Outcome.Success).value

        assertEquals(210, detail.durationSeconds)
        assertEquals(true, detail.broadcastable)
    }

    @Test
    fun `track detail passes an archive failure on`() = runTest {
        fixture.archive.nextFailure = Failure.Connection

        assertEquals(Outcome.Error(Failure.Connection), interactor.getTrackDetail(track()))
    }

    @Test
    fun `refreshHistory fills the history from the station`() = runTest {
        assertEquals(Outcome.Success(Unit), interactor.refreshHistory())

        assertEquals(listOf("trk-1", "trk-2", "trk-3"), interactor.observeHistory().first().map { it.track.trackId })
    }
}
