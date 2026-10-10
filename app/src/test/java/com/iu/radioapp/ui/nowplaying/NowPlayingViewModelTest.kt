package com.iu.radioapp.ui.nowplaying

import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.repository.TEST_NOW
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val playout = fixture.playout
    private lateinit var viewModel: NowPlayingViewModel

    @After
    fun tearDown() = viewModel.viewModelScope.cancel()

    private fun TestScope.start(): NowPlayingViewModel {
        viewModel = NowPlayingViewModel(fixture.trackInteractor)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun primeCache() {
        fixture.trackInteractor.getNowPlaying()
    }

    // The fake shares one failure switch between history and current track; the ViewModel calls both on start.
    private fun failPlayout(failure: Failure) {
        playout.nextFailure = failure
        playout.failureRepeatCount = 2
    }

    @Test
    fun `loading turns into content with track, album and host`() = runTest {
        val viewModel = start()
        assertEquals(NowPlayingUiState.Loading, viewModel.uiState.value)

        runCurrent()

        val state = viewModel.uiState.value as NowPlayingUiState.Content
        val playback = state.nowPlaying.playback
        assertEquals("Sample Song", playback.track.title)
        assertEquals("Fake Album", playback.track.album)
        assertEquals("Alex Host", state.nowPlaying.show?.host?.displayName)
    }

    @Test
    fun `talk segment is the empty state, not an error`() = runTest {
        playout.currentTrack = null
        val viewModel = start()

        runCurrent()

        assertTrue(viewModel.uiState.value is NowPlayingUiState.Empty)
    }

    @Test
    fun `no connection and no cache is offline without content`() = runTest {
        failPlayout(Failure.Connection)
        val viewModel = start()

        runCurrent()

        val state = viewModel.uiState.value as NowPlayingUiState.Offline
        assertNull(state.cached)
    }

    @Test
    fun `no connection with a cache is offline with the last track and its fetch time`() = runTest {
        primeCache()
        failPlayout(Failure.Connection)
        val viewModel = start()

        runCurrent()

        val cached = checkNotNull((viewModel.uiState.value as NowPlayingUiState.Offline).cached)
        assertEquals("Sample Song", cached.nowPlaying.playback.track.title)
        assertEquals(TEST_NOW, cached.fetchedAt)
        assertFalse(cached.isStale)
    }

    @Test
    fun `cache older than five minutes is marked stale as the interactor judges it`() = runTest {
        primeCache()
        fixture.clock.instant = TEST_NOW + 5.minutes + 1.seconds
        failPlayout(Failure.Connection)
        val viewModel = start()

        runCurrent()

        assertTrue(checkNotNull((viewModel.uiState.value as NowPlayingUiState.Offline).cached).isStale)
    }

    @Test
    fun `server fault without a cache is an error`() = runTest {
        failPlayout(Failure.Server)
        val viewModel = start()

        runCurrent()

        val state = viewModel.uiState.value as NowPlayingUiState.Error
        assertEquals(Failure.Server, state.failure)
        assertNull(state.cached)
    }

    @Test
    fun `server fault with a cache is an error that still shows the cache`() = runTest {
        primeCache()
        failPlayout(Failure.Server)
        val viewModel = start()

        runCurrent()

        val state = viewModel.uiState.value as NowPlayingUiState.Error
        assertEquals(Failure.Server, state.failure)
        assertEquals("Sample Song", state.cached?.nowPlaying?.playback?.track?.title)
    }

    @Test
    fun `history stays visible while offline`() = runTest {
        primeCache()
        failPlayout(Failure.Connection)
        val viewModel = start()

        runCurrent()

        val history = (viewModel.uiState.value as NowPlayingUiState.Offline).history
        assertEquals("Sample Song", history.first().track.title)
    }

    @Test
    fun `a track change shows up with the next poll`() = runTest {
        val viewModel = start()
        runCurrent()
        playout.currentTrack = playout.currentTrack?.copy(trackId = "trk-2", title = "Second Song")

        advanceTimeBy(NowPlayingViewModel.REFRESH_INTERVAL)
        runCurrent()

        val state = viewModel.uiState.value as NowPlayingUiState.Content
        assertEquals("Second Song", state.nowPlaying.playback.track.title)
        assertTrue(NowPlayingViewModel.REFRESH_INTERVAL < 15.seconds)
    }

    @Test
    fun `reload after offline brings the content back`() = runTest {
        failPlayout(Failure.Connection)
        val viewModel = start()
        runCurrent()

        assertTrue(viewModel.uiState.value is NowPlayingUiState.Offline)

        viewModel.reload()
        runCurrent()

        assertTrue(viewModel.uiState.value is NowPlayingUiState.Content)
    }
}
