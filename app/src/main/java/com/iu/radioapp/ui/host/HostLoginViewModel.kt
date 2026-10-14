package com.iu.radioapp.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.interactor.HostInteractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostLoginViewModel @Inject constructor(
    private val hosts: HostInteractor,
) : ViewModel() {

    private val state = MutableStateFlow<HostLoginUiState>(HostLoginUiState.Empty)
    val uiState: StateFlow<HostLoginUiState> = state.asStateFlow()

    fun onCodeChange(code: String) {
        if (state.value is HostLoginUiState.Loading) return
        state.value = if (code.isBlank()) HostLoginUiState.Empty else HostLoginUiState.Content(code)
    }

    // A successful login switches the route to the host view; the form is cleared for the next time.
    fun login() {
        val code = when (val current = state.value) {
            is HostLoginUiState.Content -> current.code
            is HostLoginUiState.Offline -> current.code
            is HostLoginUiState.Error -> current.code
            HostLoginUiState.Empty, is HostLoginUiState.Loading -> return
        }
        state.value = HostLoginUiState.Loading(code)
        viewModelScope.launch {
            state.value = when (val outcome = hosts.login(code)) {
                is Outcome.Success -> HostLoginUiState.Empty
                is Outcome.Error -> when (outcome.failure) {
                    Failure.Connection -> HostLoginUiState.Offline(code)
                    else -> HostLoginUiState.Error(code, outcome.failure)
                }
            }
        }
    }
}
