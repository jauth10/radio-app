package com.iu.radioapp.ui.request

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.Track
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.failureText
import com.iu.radioapp.ui.common.refusalText

object SongRequestTags {
    const val MESSAGE = "request_message"
    const val NAME = "request_name"
    const val SUBMIT = "request_submit"
}

@Composable
fun SongRequestRoute(track: Track, onShowMyRequests: () -> Unit) {
    val viewModel = hiltViewModel<SongRequestViewModel, SongRequestViewModel.Factory>(key = track.trackId) {
        it.create(track)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SongRequestScreen(
        state = state,
        onMessageChange = viewModel::onMessageChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onSubmit = viewModel::submit,
        onReload = viewModel::load,
        onShowMyRequests = onShowMyRequests,
    )
}

@Composable
fun SongRequestScreen(
    state: SongRequestUiState,
    onMessageChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onShowMyRequests: () -> Unit,
) {
    when (state) {
        SongRequestUiState.Loading -> LoadingView()
        is SongRequestUiState.Empty -> Column(modifier = Modifier.padding(16.dp)) {
            TrackCard(state.track)
            MessageView(title = refusalText(state.refusal), body = null)
        }
        is SongRequestUiState.Content -> RequestFormView(state.form, null, onMessageChange, onDisplayNameChange, onSubmit, onShowMyRequests)
        is SongRequestUiState.Offline -> RequestFormView(
            state.form,
            { NoticeBanner(stringResource(R.string.request_archive_offline)) },
            onMessageChange, onDisplayNameChange, onSubmit, onShowMyRequests,
        )
        is SongRequestUiState.Error -> if (state.form == null) {
            MessageView(
                title = stringResource(R.string.error_title),
                body = failureText(state.failure),
                actionLabel = stringResource(R.string.action_reload),
                onAction = onReload,
            )
        } else {
            RequestFormView(
                state.form,
                { NoticeBanner(stringResource(R.string.request_archive_error), isError = true) },
                onMessageChange, onDisplayNameChange, onSubmit, onShowMyRequests,
            )
        }
    }
}

@Composable
private fun RequestFormView(
    form: RequestForm,
    notice: (@Composable () -> Unit)?,
    onMessageChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowMyRequests: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        notice?.invoke()
        TrackCard(form.track)
        val editable = !form.submitting && !form.queued
        OutlinedTextField(
            value = form.message,
            onValueChange = onMessageChange,
            enabled = editable,
            label = { Text(stringResource(R.string.request_message_label)) },
            modifier = Modifier.fillMaxWidth().testTag(SongRequestTags.MESSAGE),
        )
        OutlinedTextField(
            value = form.displayName,
            onValueChange = onDisplayNameChange,
            enabled = editable,
            singleLine = true,
            label = { Text(stringResource(R.string.request_name_label)) },
            modifier = Modifier.fillMaxWidth().testTag(SongRequestTags.NAME),
        )
        form.submitFailure?.let { Text(failureText(it), color = MaterialTheme.colorScheme.error) }
        if (form.queued) {
            Text(stringResource(R.string.request_queued))
            TextButton(onClick = onShowMyRequests) { Text(stringResource(R.string.action_show_my_requests)) }
        } else {
            Button(onClick = onSubmit, enabled = editable, modifier = Modifier.testTag(SongRequestTags.SUBMIT)) {
                Text(stringResource(R.string.action_submit_request))
            }
        }
    }
}

@Composable
private fun TrackCard(track: Track) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge)
            Text(track.artist)
            track.album?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
