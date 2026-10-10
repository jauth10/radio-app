package com.iu.radioapp.ui.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.domain.Track
import com.iu.radioapp.interactor.SongRequestInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = SongRequestViewModel.Factory::class)
class SongRequestViewModel @AssistedInject constructor(
    @Assisted private val track: Track,
    private val requests: SongRequestInteractor,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(track: Track): SongRequestViewModel
    }

    private val state = MutableStateFlow<SongRequestUiState>(SongRequestUiState.Loading)
    val uiState: StateFlow<SongRequestUiState> = state.asStateFlow()

    init {
        load()
    }

    fun load() {
        state.value = SongRequestUiState.Loading
        viewModelScope.launch {
            val displayName = (requests.getDisplayName() as? Outcome.Success)?.value.orEmpty()
            state.value = when (val outcome = requests.prepareRequest(track)) {
                is Outcome.Success -> {
                    val draft = outcome.value
                    draft.refusal?.let { SongRequestUiState.Empty(draft.track, it) }
                        ?: SongRequestUiState.Content(RequestForm(draft.track, message = "", displayName = displayName))
                }
                is Outcome.Error -> {
                    val form = RequestForm(track, message = "", displayName = displayName)
                    when (outcome.failure) {
                        Failure.Connection -> SongRequestUiState.Offline(form)
                        Failure.Server -> SongRequestUiState.Error(Failure.Server, form)
                        else -> SongRequestUiState.Error(outcome.failure, form = null)
                    }
                }
            }
        }
    }

    fun onMessageChange(message: String) = updateForm { it.copy(message = message) }

    fun onDisplayNameChange(displayName: String) = updateForm { it.copy(displayName = displayName) }

    fun submit() {
        val form = currentForm() ?: return
        if (form.submitting || form.queued) return
        updateForm { it.copy(submitting = true, submitFailure = null) }
        viewModelScope.launch {
            when (val result = requests.submitRequest(form.track, form.message, form.displayName)) {
                is Submission.Queued -> updateForm { it.copy(submitting = false, queued = true) }
                is Submission.Refused -> state.value = SongRequestUiState.Empty(form.track, result.reason)
                is Submission.Failed -> updateForm { it.copy(submitting = false, submitFailure = result.failure) }
            }
        }
    }

    private fun currentForm(): RequestForm? = when (val current = state.value) {
        is SongRequestUiState.Content -> current.form
        is SongRequestUiState.Offline -> current.form
        is SongRequestUiState.Error -> current.form
        else -> null
    }

    private fun updateForm(transform: (RequestForm) -> RequestForm) = state.update { current ->
        when (current) {
            is SongRequestUiState.Content -> current.copy(form = transform(current.form))
            is SongRequestUiState.Offline -> current.copy(form = transform(current.form))
            is SongRequestUiState.Error -> current.copy(form = current.form?.let(transform))
            else -> current
        }
    }
}
