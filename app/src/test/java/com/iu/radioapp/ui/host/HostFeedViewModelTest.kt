package com.iu.radioapp.ui.host

import androidx.lifecycle.viewModelScope
import com.iu.radioapp.data.remote.s4feedback.ConnectionEvent
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.repository.MutableClock
import com.iu.radioapp.repository.TEST_NOW
import com.iu.radioapp.ui.MainDispatcherRule
import contract.s4feedback.AggregateDto
import contract.s4feedback.EventType
import contract.s4feedback.RatingEventDto
import contract.s4feedback.RatingEventMessage
import contract.s4feedback.RatingTarget
import contract.s4feedback.TimeWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HostFeedViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture(MutableClock(scheduler = main.dispatcher.scheduler))
    private val viewModels = mutableListOf<HostFeedViewModel>()

    @After
    fun tearDown() = viewModels.forEach { it.viewModelScope.cancel() }

    private fun TestScope.start(): HostFeedViewModel {
        val viewModel = HostFeedViewModel(fixture.hostInteractor)
        viewModels += viewModel
        backgroundScope.launch { viewModel.access.collect {} }
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private suspend fun TestScope.login() {
        fixture.hostInteractor.login("1234")
        runCurrent()
    }

    private fun TestScope.emit(vararg sequence: ConnectionEvent) {
        sequence.forEach { fixture.ratingEvents.emit(it) }
        runCurrent()
    }

    private fun TestScope.advance(seconds: Int) {
        advanceTimeBy(seconds.seconds)
        runCurrent()
    }

    private fun HostFeedViewModel.view(): HostFeedView = when (val state = uiState.value) {
        is HostFeedUiState.Content -> state.view
        is HostFeedUiState.Empty -> state.view
        is HostFeedUiState.Offline -> state.view
        is HostFeedUiState.Error -> state.view
        HostFeedUiState.Loading -> error("no view while loading")
    }

    private fun pushedRating(ratingId: String) = ConnectionEvent.MessageReceived(
        RatingEventMessage(
            EventType.NEW_RATING,
            t0 = TEST_NOW,
            rating = RatingEventDto(ratingId, RatingTarget.HOST, 5, "Toll moderiert", TEST_NOW, "Listener A"),
        ),
    )

    @Test
    fun `without a session the login is shown`() = runTest {
        val viewModel = start()

        assertEquals(HostAccess.SignedOut(sessionExpired = false), viewModel.access.value)
        assertEquals(HostFeedUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `after login the host view starts empty with the numbers of the running show`() = runTest {
        val viewModel = start()

        login()

        assertEquals(HostAccess.SignedIn, viewModel.access.value)
        assertTrue(viewModel.uiState.value is HostFeedUiState.Empty)
        assertEquals(1, viewModel.view().numbers?.playlistRatingCount)
        assertEquals(ConnectionState.Connecting, viewModel.view().connection)
    }

    @Test
    fun `a new rating appears without reloading`() = runTest {
        val viewModel = start()
        login()

        emit(ConnectionEvent.Connected, pushedRating("rat-9"))

        val state = viewModel.uiState.value as HostFeedUiState.Content
        val rating = state.view.ratings.single().rating
        assertEquals("rat-9", rating.ratingId)
        assertEquals("Toll moderiert", rating.comment)
        assertEquals("Listener A", rating.displayName)
        assertEquals(ConnectionState.Connected, state.view.connection)
    }

    @Test
    fun `a pushed aggregate updates the numbers`() = runTest {
        val viewModel = start()
        login()
        val aggregate = AggregateDto(4.5, 2, 5.0, 3, TimeWindow(TEST_NOW, TEST_NOW))

        emit(ConnectionEvent.MessageReceived(RatingEventMessage(EventType.AGGREGATE_UPDATED, t0 = TEST_NOW, aggregate = aggregate)))

        assertEquals(2, viewModel.view().numbers?.playlistRatingCount)
        assertEquals(3, viewModel.view().numbers?.hostRatingCount)
    }

    @Test
    fun `five failed attempts show the polling mode`() = runTest {
        val viewModel = start()
        login()

        emit(ConnectionEvent.Reconnecting(1))
        assertEquals(ConnectionState.Reconnecting(1), viewModel.view().connection)
        emit(
            ConnectionEvent.Reconnecting(2),
            ConnectionEvent.Reconnecting(3),
            ConnectionEvent.Reconnecting(4),
            ConnectionEvent.Failed(Failure.Connection),
        )

        assertEquals(ConnectionState.Polling, viewModel.view().connection)
    }

    @Test
    fun `a talk segment hides the numbers`() = runTest {
        val viewModel = start()
        login()

        fixture.playout.currentTrack = null
        advance(10)

        assertNull(viewModel.view().numbers)
        assertTrue(viewModel.view().talkSegment)
    }

    @Test
    fun `stale show data is offline without numbers but keeps the ratings`() = runTest {
        val viewModel = start()
        login()
        emit(ConnectionEvent.Connected, pushedRating("rat-9"))

        fixture.clock.instant = TEST_NOW + 5.minutes
        fixture.playout.nextFailure = Failure.Connection
        advance(10)

        val state = viewModel.uiState.value as HostFeedUiState.Offline
        assertNull(state.view.numbers)
        assertEquals(listOf("rat-9"), state.view.ratings.map { it.rating.ratingId })
    }

    @Test
    fun `a server failure with stale show data is an error without numbers`() = runTest {
        val viewModel = start()
        login()

        fixture.clock.instant = TEST_NOW + 5.minutes
        fixture.playout.nextFailure = Failure.Server
        advance(10)

        val state = viewModel.uiState.value as HostFeedUiState.Error
        assertEquals(Failure.Server, state.failure)
        assertNull(state.view.numbers)
    }

    @Test
    fun `an expired session returns to the login with a hint`() = runTest {
        val viewModel = start()
        login()

        emit(ConnectionEvent.Connected, ConnectionEvent.Failed(Failure.Unauthorized))

        assertEquals(HostAccess.SignedOut(sessionExpired = true), viewModel.access.value)
    }

    @Test
    fun `signing out returns to the login without a hint`() = runTest {
        val viewModel = start()
        login()

        viewModel.signOut()
        runCurrent()

        assertEquals(HostAccess.SignedOut(sessionExpired = false), viewModel.access.value)
    }

    @Test
    fun `a rating from the listener view shows up after returning to the host view`() = runTest {
        val first = start()
        login()
        first.viewModelScope.cancel()

        fixture.ratingInteractor.submitRating(com.iu.radioapp.domain.RatingTarget.PLAYLIST, 4, "Guter Mix")
        fixture.ratingRepository.deliverOpen()
        val second = start()

        val received = (second.uiState.value as HostFeedUiState.Content).view.ratings.single()
        assertEquals("Guter Mix", received.rating.comment)
        assertEquals(FeedSource.CATCH_UP, received.source)
    }
}
