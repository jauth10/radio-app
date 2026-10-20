package com.iu.radioapp.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.OutboxEntry
import com.iu.radioapp.domain.Outcome
import com.iu.radioapp.domain.RatingContext
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RatingWithDelivery
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.Submission
import com.iu.radioapp.domain.referenceIdFor
import com.iu.radioapp.interactor.RatingInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = RatingViewModel.Factory::class)
class RatingViewModel @AssistedInject constructor(
    @Assisted private val target: RatingTarget,
    private val ratings: RatingInteractor,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(target: RatingTarget): RatingViewModel
    }

    private data class Input(
        val value: Int? = null,
        val comment: String = "",
        val submitting: Boolean = false,
        val refusal: RefusalReason? = null,
        val failure: Failure? = null,
    )

    private val reloads = MutableStateFlow(0)
    private val input = MutableStateFlow(Input())

    // Session lock: not persisted, gone after a process restart. The station stays the authority (E30).
    private val ratedReferences = MutableStateFlow<Set<String>>(emptySet())
    private val submittedKey = MutableStateFlow<String?>(null)

    // Re-reading keeps the interactor's five minute verdict current while the screen is open.
    private val context = reloads.flatMapLatest {
        flow {
            emit(null)
            while (true) {
                emit(ratings.getRatingContext())
                delay(REFRESH_INTERVAL)
            }
        }
    }

    private data class Deliveries(val submitted: SubmittedRating?, val undelivered: List<RatingWithDelivery>)

    private val deliveries = reloads.flatMapLatest {
        combine(submittedKey, ratings.observeDeliveries(), ratings.observeUndeliveredRatings(target), ::toDeliveries)
    }

    val uiState: StateFlow<RatingUiState> =
        combine(context, input, ratedReferences, deliveries, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RatingUiState.Loading)

    fun reload() {
        reloads.value++
    }

    fun onValueSelected(value: Int) = input.update { it.copy(value = value, refusal = null, failure = null) }

    fun onCommentChange(comment: String) = input.update { it.copy(comment = comment) }

    // The lock holds here, not only on the button.
    fun submit() {
        val panel = currentPanel() ?: return
        if (panel.locked || panel.refusal != null) return
        val current = input.value
        if (current.submitting) return
        val value = current.value ?: return
        input.update { it.copy(submitting = true, refusal = null, failure = null) }
        viewModelScope.launch {
            when (val result = ratings.submitRating(target, value, current.comment)) {
                is Submission.Queued -> {
                    ratedReferences.update { it + result.value.referenceId }
                    submittedKey.value = result.value.idempotencyKey
                    input.value = Input()
                }
                is Submission.Refused -> {
                    input.update { it.copy(submitting = false, refusal = result.reason) }
                    reload()
                }
                is Submission.Failed -> input.update { it.copy(submitting = false, failure = result.failure) }
            }
        }
    }

    fun retryDelivery() {
        val key = submittedKey.value ?: return
        viewModelScope.launch { ratings.retry(key) }
    }

    // The retried rating moves into the status card, so its new delivery stays in view.
    fun retryFailed(idempotencyKey: String) {
        submittedKey.value = idempotencyKey
        viewModelScope.launch { ratings.retry(idempotencyKey) }
    }

    fun dismissRejected(idempotencyKey: String) {
        viewModelScope.launch { ratings.dismissRejected(idempotencyKey) }
    }

    private fun currentPanel(): RatingPanel? = when (val state = uiState.value) {
        is RatingUiState.Content -> state.panel
        is RatingUiState.Offline -> state.panel
        is RatingUiState.Error -> state.panel
        else -> null
    }

    // The rating in the status card is left out of the undelivered list, so it never shows twice.
    private fun toDeliveries(
        key: String?,
        entries: Outcome<List<OutboxEntry>>,
        undelivered: Outcome<List<RatingWithDelivery>>,
    ): Outcome<Deliveries> {
        val all = when (entries) {
            is Outcome.Success -> entries.value
            is Outcome.Error -> return entries
        }
        val undeliveredRatings = when (undelivered) {
            is Outcome.Success -> undelivered.value
            is Outcome.Error -> return undelivered
        }
        val submitted = all.firstOrNull { it.idempotencyKey == key }
            ?.let { SubmittedRating(it.idempotencyKey, it.status, it.rejectionReason) }
        return Outcome.Success(Deliveries(submitted, undeliveredRatings.filter { it.rating.idempotencyKey != key }))
    }

    private fun toUiState(
        outcome: Outcome<RatingContext?>?,
        input: Input,
        rated: Set<String>,
        delivery: Outcome<Deliveries>,
    ): RatingUiState {
        val deliveries = when (delivery) {
            is Outcome.Success -> delivery.value
            is Outcome.Error -> return RatingUiState.Error(delivery.failure, panel = null, submitted = null)
        }
        return toUiState(outcome, input, rated, deliveries.submitted, deliveries.undelivered)
    }

    private fun toUiState(
        outcome: Outcome<RatingContext?>?,
        input: Input,
        rated: Set<String>,
        submitted: SubmittedRating?,
        undelivered: List<RatingWithDelivery>,
    ): RatingUiState = when (outcome) {
        null -> RatingUiState.Loading
        is Outcome.Error -> when (outcome.failure) {
            Failure.Connection -> RatingUiState.Offline(panel = null, submitted = submitted, undelivered = undelivered)
            else -> RatingUiState.Error(outcome.failure, panel = null, submitted = submitted, undelivered = undelivered)
        }
        is Outcome.Success -> {
            val context = outcome.value
            if (context == null) {
                RatingUiState.Empty(submitted, undelivered)
            } else {
                val panel = RatingPanel(
                    target = target,
                    context = context,
                    refusal = ratings.refusalFor(context, target),
                    locked = context.referenceIdFor(target) in rated,
                    value = input.value,
                    comment = input.comment,
                    submitting = input.submitting,
                    submitRefusal = input.refusal,
                    submitFailure = input.failure,
                )
                when (val cause = context.cause) {
                    null -> RatingUiState.Content(panel, submitted, undelivered)
                    Failure.Connection -> RatingUiState.Offline(panel, submitted, undelivered)
                    else -> RatingUiState.Error(cause, panel, submitted, undelivered)
                }
            }
        }
    }

    companion object {
        val REFRESH_INTERVAL = 10.seconds
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
