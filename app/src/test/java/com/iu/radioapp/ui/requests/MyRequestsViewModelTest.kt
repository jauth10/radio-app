package com.iu.radioapp.ui.requests

import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.interactor.track
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyRequestsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val interactor = fixture.songRequestInteractor
    private lateinit var viewModel: MyRequestsViewModel

    @After
    fun tearDown() = viewModel.viewModelScope.cancel()

    private fun TestScope.start(): MyRequestsViewModel {
        viewModel = MyRequestsViewModel(interactor)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private suspend fun queue(): String =
        (interactor.submitRequest(track(broadcastable = true), "Bitte") as Submission.Queued).value.idempotencyKey

    @Test
    fun `loading turns into empty without requests`() = runTest {
        val viewModel = start()
        assertEquals(MyRequestsUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(MyRequestsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a queued request shows up as open straight away`() = runTest {
        val viewModel = start()
        advanceUntilIdle()

        queue()
        advanceUntilIdle()

        val row = (viewModel.uiState.value as MyRequestsUiState.Content).requests.single()
        assertEquals(DeliveryStatus.OPEN, row.deliveryStatus)
    }

    @Test
    fun `delivery reaches the screen through the flow with the station status`() = runTest {
        queue()
        val viewModel = start()
        advanceUntilIdle()

        fixture.songRequestRepository.deliverOpen()
        advanceUntilIdle()

        val row = (viewModel.uiState.value as MyRequestsUiState.Content).requests.single()
        assertEquals(DeliveryStatus.DELIVERED, row.deliveryStatus)
        assertTrue(row.request.requestId != null)
    }

    @Test
    fun `station rejection shows its reason and is not delivered again`() = runTest {
        queue()
        fixture.requests.nextFailure = Failure.Rejected("Wunschlimit erreicht", retryable = true)
        fixture.songRequestRepository.deliverOpen()
        val viewModel = start()
        advanceUntilIdle()

        fixture.songRequestRepository.deliverOpen()
        advanceUntilIdle()

        val row = (viewModel.uiState.value as MyRequestsUiState.Content).requests.single()
        assertEquals(DeliveryStatus.REJECTED, row.deliveryStatus)
        assertEquals("Wunschlimit erreicht", row.request.rejectionReason)
    }

    @Test
    fun `status refresh without connection is offline and keeps the list`() = runTest {
        queue()
        fixture.songRequestRepository.deliverOpen()
        fixture.requests.nextFailure = Failure.Connection
        val viewModel = start()

        advanceUntilIdle()

        assertEquals(1, (viewModel.uiState.value as MyRequestsUiState.Offline).requests.size)
    }

    @Test
    fun `status refresh server fault is an error and keeps the list`() = runTest {
        queue()
        fixture.songRequestRepository.deliverOpen()
        fixture.requests.nextFailure = Failure.Server
        val viewModel = start()

        advanceUntilIdle()

        val state = viewModel.uiState.value as MyRequestsUiState.Error
        assertEquals(Failure.Server, state.failure)
        assertEquals(1, state.requests.size)
    }

    @Test
    fun `failed delivery can be retried by hand`() = runTest {
        val key = queue()
        fixture.requests.nextFailure = Failure.Server
        fixture.requests.failureRepeatCount = 5
        repeat(5) { fixture.songRequestRepository.deliverOpen() }
        val viewModel = start()
        advanceUntilIdle()
        assertEquals(DeliveryStatus.FAILED, (viewModel.uiState.value as MyRequestsUiState.Content).requests.single().deliveryStatus)

        viewModel.retry(key)
        advanceUntilIdle()

        assertEquals(DeliveryStatus.OPEN, (viewModel.uiState.value as MyRequestsUiState.Content).requests.single().deliveryStatus)
    }
}
