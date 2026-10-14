package com.iu.radioapp.ui.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.StaleMarker
import com.iu.radioapp.ui.common.deliveryText
import com.iu.radioapp.ui.common.failureText
import com.iu.radioapp.ui.common.refusalText

object RatingTags {
    const val SUBMIT = "rating_submit"
    const val COMMENT = "rating_comment"
    const val STATUS = "rating_status"
    const val RETRY = "rating_retry"
    fun value(value: Int) = "rating_value_$value"
}

/** Both targets share one back stack entry, so each keeps its ViewModel and session lock across tab switches. */
@Composable
fun RatingTabsRoute() {
    var selected by rememberSaveable { mutableStateOf(RatingTarget.PLAYLIST) }
    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected.ordinal) {
            RatingTarget.entries.forEach { target ->
                Tab(
                    selected = target == selected,
                    onClick = { selected = target },
                    text = {
                        Text(stringResource(if (target == RatingTarget.PLAYLIST) R.string.rating_tab_playlist else R.string.rating_tab_host))
                    },
                )
            }
        }
        RatingRoute(selected)
    }
}

@Composable
fun RatingRoute(target: RatingTarget) {
    val viewModel = hiltViewModel<RatingViewModel, RatingViewModel.Factory>(key = target.name) { it.create(target) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RatingScreen(
        state = state,
        onValueSelected = viewModel::onValueSelected,
        onCommentChange = viewModel::onCommentChange,
        onSubmit = viewModel::submit,
        onReload = viewModel::reload,
        onRetryDelivery = viewModel::retryDelivery,
    )
}

@Composable
fun RatingScreen(
    state: RatingUiState,
    onValueSelected: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onRetryDelivery: () -> Unit,
) {
    if (state is RatingUiState.Loading) {
        LoadingView()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is RatingUiState.Content -> RatingPanelView(state.panel, onValueSelected, onCommentChange, onSubmit)
            is RatingUiState.Empty -> MessageView(
                title = stringResource(R.string.talk_segment_title),
                body = refusalText(RefusalReason.NO_SHOW_ON_AIR),
                actionLabel = stringResource(R.string.action_reload),
                onAction = onReload,
            )
            is RatingUiState.Offline -> if (state.panel == null) {
                MessageView(
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.offline_no_data),
                    actionLabel = stringResource(R.string.action_reload),
                    onAction = onReload,
                )
            } else {
                NoticeBanner(stringResource(R.string.rating_offline_hint))
                RatingPanelView(state.panel, onValueSelected, onCommentChange, onSubmit)
            }
            is RatingUiState.Error -> if (state.panel == null) {
                MessageView(
                    title = stringResource(R.string.error_title),
                    body = failureText(state.failure),
                    actionLabel = stringResource(R.string.action_reload),
                    onAction = onReload,
                )
            } else {
                NoticeBanner(failureText(state.failure), isError = true)
                RatingPanelView(state.panel, onValueSelected, onCommentChange, onSubmit)
            }
            RatingUiState.Loading -> Unit
        }
        state.submitted?.let { submitted ->
            Card(modifier = Modifier.fillMaxWidth().testTag(RatingTags.STATUS)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.rating_status_title), style = MaterialTheme.typography.labelLarge)
                    Text(deliveryText(submitted.status, submitted.rejectionReason))
                    if (submitted.status == DeliveryStatus.FAILED) {
                        TextButton(onClick = onRetryDelivery, modifier = Modifier.testTag(RatingTags.RETRY)) {
                            Text(stringResource(R.string.action_retry_delivery))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingPanelView(
    panel: RatingPanel,
    onValueSelected: (Int) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val host = panel.context.host
        Text(
            when {
                panel.target == RatingTarget.PLAYLIST -> stringResource(R.string.rating_subject_playlist)
                host != null -> stringResource(R.string.rating_subject_host, host.displayName)
                else -> stringResource(R.string.rating_tab_host)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (panel.context.isStale) StaleMarker()
    }
    panel.refusal?.let { Text(refusalText(it), color = MaterialTheme.colorScheme.error) }
    if (panel.locked) Text(stringResource(R.string.rating_locked))
    val editable = panel.refusal == null && !panel.locked && !panel.submitting
    Text(stringResource(R.string.rating_value_label))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { value ->
            val description = stringResource(R.string.rating_value_description, value)
            FilterChip(
                selected = panel.value == value,
                onClick = { onValueSelected(value) },
                enabled = editable,
                label = { Text(value.toString()) },
                modifier = Modifier.testTag(RatingTags.value(value)).semantics { contentDescription = description },
            )
        }
    }
    OutlinedTextField(
        value = panel.comment,
        onValueChange = onCommentChange,
        enabled = editable,
        label = { Text(stringResource(R.string.rating_comment_label)) },
        modifier = Modifier.fillMaxWidth().testTag(RatingTags.COMMENT),
    )
    panel.submitRefusal?.takeIf { it != panel.refusal }?.let { Text(refusalText(it), color = MaterialTheme.colorScheme.error) }
    panel.submitFailure?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error) }
    Button(onClick = onSubmit, enabled = panel.canSubmit, modifier = Modifier.testTag(RatingTags.SUBMIT)) {
        Text(stringResource(R.string.action_submit_rating))
    }
}
