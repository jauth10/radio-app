package com.iu.radioapp.ui.host

import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.interactor.InteractorFixture
import com.iu.radioapp.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HostLoginViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = InteractorFixture()
    private val viewModel = HostLoginViewModel(fixture.hostInteractor)

    @After
    fun tearDown() = viewModel.viewModelScope.cancel()

    private suspend fun sessionActive() = fixture.hostInteractor.observeSessionActive().first()

    @Test
    fun `the form is empty until a code is typed`() = runTest {
        assertEquals(HostLoginUiState.Empty, viewModel.uiState.value)

        viewModel.onCodeChange("ALEX")
        assertEquals(HostLoginUiState.Content("ALEX"), viewModel.uiState.value)

        viewModel.onCodeChange(" ")
        assertEquals(HostLoginUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `an empty form does not log in`() = runTest {
        viewModel.login()
        runCurrent()

        assertEquals(HostLoginUiState.Empty, viewModel.uiState.value)
        assertEquals(false, sessionActive())
    }

    @Test
    fun `a valid code shows loading and then unlocks the host view`() = runTest {
        viewModel.onCodeChange("ALEX-2026")

        viewModel.login()
        assertEquals(HostLoginUiState.Loading("ALEX-2026"), viewModel.uiState.value)
        runCurrent()

        assertEquals(HostLoginUiState.Empty, viewModel.uiState.value)
        assertEquals(true, sessionActive())
    }

    @Test
    fun `an invalid code is an error and unlocks nothing`() = runTest {
        fixture.playout.nextFailure = Failure.Unauthorized
        viewModel.onCodeChange("0000")

        viewModel.login()
        runCurrent()

        assertEquals(HostLoginUiState.Error("0000", Failure.Unauthorized), viewModel.uiState.value)
        assertEquals(false, sessionActive())
    }

    @Test
    fun `the lockout after too many attempts shows the station's reason`() = runTest {
        val lockout = Failure.Rejected(reason = "too many failed login attempts", retryable = true)
        fixture.playout.nextFailure = lockout
        viewModel.onCodeChange("0000")

        viewModel.login()
        runCurrent()

        assertEquals(HostLoginUiState.Error("0000", lockout), viewModel.uiState.value)
    }

    @Test
    fun `no connection is offline and the code can be sent again`() = runTest {
        fixture.playout.nextFailure = Failure.Connection
        viewModel.onCodeChange("ALEX-2026")
        viewModel.login()
        runCurrent()
        assertEquals(HostLoginUiState.Offline("ALEX-2026"), viewModel.uiState.value)

        viewModel.login()
        runCurrent()

        assertEquals(true, sessionActive())
    }
}
