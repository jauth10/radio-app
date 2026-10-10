package com.iu.radioapp.ui.request

import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.Track
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.interactor.track
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongRequestViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()

    // As the request screen gets it from S1 or the route: the archive flag is unknown.
    private fun fromPlayout(trackId: String = "trk-1"): Track = track(trackId).copy(durationSeconds = null, broadcastable = null)

    private fun viewModel(track: Track = fromPlayout()) = SongRequestViewModel(track, fixture.songRequestInteractor)

    private suspend fun requests() = fixture.songRequestInteractor.observeRequests().first()

    @Test
    fun `loading turns into the form with the archive data and the stored name`() = runTest {
        fixture.listenerRepository.setDisplayName("Jo")
        val viewModel = viewModel()
        assertEquals(SongRequestUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        val form = (viewModel.uiState.value as SongRequestUiState.Content).form
        assertEquals(true, form.track.broadcastable)
        assertEquals("Jo", form.displayName)
    }

    @Test
    fun `track the archive marks as not broadcastable is empty with the refusal`() = runTest {
        val viewModel = viewModel(fromPlayout("trk-3"))

        advanceUntilIdle()

        assertEquals(RefusalReason.TRACK_NOT_BROADCASTABLE, (viewModel.uiState.value as SongRequestUiState.Empty).refusal)
    }

    @Test
    fun `unreachable archive is offline and the request still queues`() = runTest {
        fixture.archive.nextFailure = Failure.Connection
        fixture.archive.failureRepeatCount = 2
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SongRequestUiState.Offline)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue((viewModel.uiState.value as SongRequestUiState.Offline).form.queued)
        assertEquals(DeliveryStatus.OPEN, requests().single().deliveryStatus)
    }

    @Test
    fun `archive server fault is an error with a usable form`() = runTest {
        fixture.archive.nextFailure = Failure.Server
        val viewModel = viewModel()

        advanceUntilIdle()

        val state = viewModel.uiState.value as SongRequestUiState.Error
        assertEquals(Failure.Server, state.failure)
        assertTrue(state.form != null)
    }

    @Test
    fun `track unknown to the archive is an error with the station reason and no form`() = runTest {
        val viewModel = viewModel(fromPlayout("trk-unknown"))

        advanceUntilIdle()

        val state = viewModel.uiState.value as SongRequestUiState.Error
        assertTrue(state.failure is Failure.Rejected)
        assertNull(state.form)
    }

    @Test
    fun `submit queues message and display name, then the form is done`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onMessageChange("Bitte laut")
        viewModel.onDisplayNameChange("Kim")

        viewModel.submit()
        advanceUntilIdle()

        assertTrue((viewModel.uiState.value as SongRequestUiState.Content).form.queued)
        assertEquals("Bitte laut", requests().single().request.message)
        assertEquals(Outcome.Success("Kim"), fixture.songRequestInteractor.getDisplayName())
    }

    @Test
    fun `a second tap after queueing does not queue twice`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, requests().size)
    }
}
