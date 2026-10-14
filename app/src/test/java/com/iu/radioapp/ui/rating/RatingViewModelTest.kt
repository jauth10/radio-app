package com.iu.radioapp.ui.rating

import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.repository.TEST_NOW
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class RatingViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val playout = fixture.playout
    private val sampleTrack = checkNotNull(playout.currentTrack)
    private val viewModels = mutableListOf<RatingViewModel>()

    @After
    fun tearDown() = viewModels.forEach { it.viewModelScope.cancel() }

    private fun TestScope.start(target: RatingTarget = RatingTarget.PLAYLIST): RatingViewModel {
        val viewModel = RatingViewModel(target, fixture.ratingInteractor)
        viewModels += viewModel
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun RatingViewModel.panel(): RatingPanel = when (val state = uiState.value) {
        is RatingUiState.Content -> state.panel
        is RatingUiState.Offline -> checkNotNull(state.panel)
        is RatingUiState.Error -> checkNotNull(state.panel)
        else -> error("no panel in $state")
    }

    private fun TestScope.rate(viewModel: RatingViewModel, value: Int = 4) {
        viewModel.onValueSelected(value)
        viewModel.submit()
        runCurrent()
    }

    private suspend fun deliveries() = fixture.ratingInteractor.observeDeliveries().first()

    @Test
    fun `loading turns into content for the running show and its host`() = runTest {
        val viewModel = RatingViewModel(RatingTarget.HOST, fixture.ratingInteractor).also { viewModels += it }
        assertEquals(RatingUiState.Loading, viewModel.uiState.value)
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        val panel = (viewModel.uiState.value as RatingUiState.Content).panel
        assertEquals("show-1", panel.context.show.showId)
        assertEquals("Alex Host", panel.context.host?.displayName)
        assertNull(panel.refusal)
    }

    @Test
    fun `talk segment is the empty state`() = runTest {
        playout.currentTrack = null

        assertTrue(start().uiState.value is RatingUiState.Empty)
    }

    @Test
    fun `no connection and no cache is offline without a panel`() = runTest {
        playout.nextFailure = Failure.Connection

        assertEquals(RatingUiState.Offline(panel = null, submitted = null), start().uiState.value)
    }

    @Test
    fun `server fault without a cache is an error`() = runTest {
        playout.nextFailure = Failure.Server

        assertEquals(RatingUiState.Error(Failure.Server, panel = null, submitted = null), start().uiState.value)
    }

    @Test
    fun `fresh cache is offline and the rating still queues`() = runTest {
        fixture.trackInteractor.getNowPlaying()
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = 2
        val viewModel = start()
        assertTrue(viewModel.uiState.value is RatingUiState.Offline)

        rate(viewModel)

        assertEquals(DeliveryStatus.OPEN, viewModel.uiState.value.submitted?.status)
    }

    @Test
    fun `stale cache refuses with the interactor's reason before anyone taps`() = runTest {
        fixture.trackInteractor.getNowPlaying()
        fixture.clock.instant = TEST_NOW + 5.minutes + 1.seconds
        playout.nextFailure = Failure.Connection
        val viewModel = start()

        val panel = viewModel.panel()
        assertTrue(panel.context.isStale)
        assertEquals(RefusalReason.CONTEXT_STALE, panel.refusal)
        viewModel.onValueSelected(3)
        assertFalse(viewModel.panel().canSubmit)
    }

    @Test
    fun `host rating without a known host names the reason`() = runTest {
        playout.currentTrack = playout.currentTrack?.copy(hostId = null, hostName = null)

        assertEquals(RefusalReason.HOST_UNKNOWN, start(RatingTarget.HOST).panel().refusal)
    }

    @Test
    fun `rating locks the button for the rest of the session`() = runTest {
        val viewModel = start()

        rate(viewModel)
        viewModel.onValueSelected(5)
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.panel().locked)
        assertFalse(viewModel.panel().canSubmit)
        assertEquals(1, deliveries().size)
    }

    @Test
    fun `the lock is per target`() = runTest {
        rate(start(RatingTarget.PLAYLIST))

        val host = start(RatingTarget.HOST)

        assertFalse(host.panel().locked)
    }

    @Test
    fun `a refusal at submit time does not lock`() = runTest {
        val viewModel = start()
        viewModel.onValueSelected(4)
        // The talk segment starts between showing the panel and tapping.
        playout.currentTrack = null

        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value is RatingUiState.Empty)
        assertTrue(deliveries().isEmpty())
        playout.currentTrack = sampleTrack
        viewModel.reload()
        runCurrent()
        assertFalse(viewModel.panel().locked)
    }

    @Test
    fun `a refused pre-check keeps the submit closed`() = runTest {
        playout.currentTrack = playout.currentTrack?.copy(hostId = null, hostName = null)
        val viewModel = start(RatingTarget.HOST)

        rate(viewModel)

        assertFalse(viewModel.panel().canSubmit)
        assertTrue(deliveries().isEmpty())
    }

    @Test
    fun `station rejection shows its reason`() = runTest {
        // A second session after a restart: the lock is gone, the station still says no (E30).
        rate(start())
        fixture.ratingRepository.deliverOpen()
        val afterRestart = start()

        rate(afterRestart)
        fixture.ratingRepository.deliverOpen()
        runCurrent()

        val submitted = checkNotNull(afterRestart.uiState.value.submitted)
        assertEquals(DeliveryStatus.REJECTED, submitted.status)
        assertEquals("already rated", submitted.rejectionReason)
    }

    @Test
    fun `failed delivery can be retried by hand`() = runTest {
        val viewModel = start()
        rate(viewModel)
        fixture.feedback.nextFailure = Failure.Server
        fixture.feedback.failureRepeatCount = 5
        repeat(5) { fixture.ratingRepository.deliverOpen() }
        runCurrent()
        assertEquals(DeliveryStatus.FAILED, viewModel.uiState.value.submitted?.status)

        viewModel.retryDelivery()
        runCurrent()

        assertEquals(DeliveryStatus.OPEN, viewModel.uiState.value.submitted?.status)
        fixture.ratingRepository.deliverOpen()
        runCurrent()
        assertEquals(DeliveryStatus.DELIVERED, viewModel.uiState.value.submitted?.status)
    }

    @Test
    fun `nothing is submitted without a value`() = runTest {
        val viewModel = start()

        viewModel.submit()
        runCurrent()

        assertFalse(viewModel.panel().canSubmit)
        assertTrue(deliveries().isEmpty())
    }
}
