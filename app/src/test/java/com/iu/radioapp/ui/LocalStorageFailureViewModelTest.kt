package com.iu.radioapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.interactor.track
import com.iu.radioapp.ui.host.HostAccess
import com.iu.radioapp.ui.host.HostFeedViewModel
import com.iu.radioapp.ui.host.HostLoginUiState
import com.iu.radioapp.ui.host.HostLoginViewModel
import com.iu.radioapp.ui.nowplaying.NowPlayingUiState
import com.iu.radioapp.ui.nowplaying.NowPlayingViewModel
import com.iu.radioapp.ui.rating.RatingUiState
import com.iu.radioapp.ui.rating.RatingViewModel
import com.iu.radioapp.ui.request.SongRequestUiState
import com.iu.radioapp.ui.request.SongRequestViewModel
import com.iu.radioapp.ui.requests.MyRequestsUiState
import com.iu.radioapp.ui.requests.MyRequestsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalStorageFailureViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val fault = fixture.localFault
    private val viewModels = mutableListOf<ViewModel>()

    @After
    fun tearDown() = viewModels.forEach { it.viewModelScope.cancel() }

    private fun <T : ViewModel> TestScope.started(viewModel: T, vararg states: StateFlow<*>): T {
        viewModels += viewModel
        states.forEach { state -> backgroundScope.launch { state.collect {} } }
        runCurrent()
        return viewModel
    }

    @Test
    fun `now playing shows the local error and recovers on reload`() = runTest {
        fault.broken = true
        val viewModel = NowPlayingViewModel(fixture.trackInteractor).let { started(it, it.uiState) }

        assertEquals(NowPlayingUiState.Error(Failure.Local, cached = null, history = emptyList()), viewModel.uiState.value)

        fault.broken = false
        viewModel.reload()
        runCurrent()

        assertTrue(viewModel.uiState.value is NowPlayingUiState.Content)
    }

    @Test
    fun `my requests shows the local error and recovers on refresh`() = runTest {
        fault.broken = true
        val viewModel = MyRequestsViewModel(fixture.songRequestInteractor).let { started(it, it.uiState) }

        assertEquals(MyRequestsUiState.Error(Failure.Local, emptyList()), viewModel.uiState.value)

        fault.broken = false
        viewModel.refresh()
        runCurrent()

        assertEquals(MyRequestsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `rating shows the local error and recovers on reload`() = runTest {
        fault.broken = true
        val viewModel = RatingViewModel(RatingTarget.PLAYLIST, fixture.ratingInteractor).let { started(it, it.uiState) }

        assertEquals(RatingUiState.Error(Failure.Local, panel = null, submitted = null), viewModel.uiState.value)

        fault.broken = false
        viewModel.reload()
        runCurrent()

        assertTrue(viewModel.uiState.value is RatingUiState.Content)
    }

    @Test
    fun `a rating that cannot be stored names the local failure`() = runTest {
        val viewModel = RatingViewModel(RatingTarget.PLAYLIST, fixture.ratingInteractor).let { started(it, it.uiState) }
        fault.broken = true

        viewModel.onValueSelected(4)
        viewModel.submit()
        runCurrent()

        val panel = (viewModel.uiState.value as RatingUiState.Content).panel
        assertEquals(Failure.Local, panel.submitFailure)
        assertEquals(null, viewModel.uiState.value.submitted)
    }

    @Test
    fun `a request that cannot be stored names the local failure`() = runTest {
        val viewModel = SongRequestViewModel(track(broadcastable = true), fixture.songRequestInteractor)
            .let { started(it, it.uiState) }
        fault.broken = true

        viewModel.submit()
        runCurrent()

        val form = (viewModel.uiState.value as SongRequestUiState.Content).form
        assertEquals(Failure.Local, form.submitFailure)
        assertEquals(false, form.queued)
    }

    @Test
    fun `an unreadable host session is shown with a reload and recovers`() = runTest {
        fault.broken = true
        val viewModel = HostFeedViewModel(fixture.hostInteractor).let { started(it, it.access, it.uiState) }

        assertEquals(HostAccess.Unavailable(Failure.Local), viewModel.access.value)

        fault.broken = false
        viewModel.reload()
        runCurrent()

        assertEquals(HostAccess.SignedOut(sessionExpired = false), viewModel.access.value)
    }

    @Test
    fun `a login that cannot be stored is an error with the code kept`() = runTest {
        val viewModel = HostLoginViewModel(fixture.hostInteractor).also { viewModels += it }
        fault.broken = true

        viewModel.onCodeChange("1234")
        viewModel.login()
        runCurrent()

        assertEquals(HostLoginUiState.Error("1234", Failure.Local), viewModel.uiState.value)
    }
}
