package com.iu.radioapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.Track
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.failureText

object TrackSearchTags {
    const val FIELD = "search_field"
    const val HIT = "search_hit"
}

@Composable
fun TrackSearchRoute(onTrackSelected: (Track) -> Unit, viewModel: TrackSearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TrackSearchScreen(state, viewModel::onQueryChange, viewModel::search, onTrackSelected)
}

@Composable
fun TrackSearchScreen(
    state: TrackSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTrackSelected: (Track) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.search_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f).testTag(TrackSearchTags.FIELD),
            )
            Button(onClick = onSearch, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.action_search)) }
        }
        when (state) {
            is TrackSearchUiState.Loading -> LoadingView()
            is TrackSearchUiState.Content -> LazyColumn {
                items(state.hits, key = { it.trackId }) { track ->
                    HitRow(track, onTrackSelected)
                    HorizontalDivider()
                }
            }
            is TrackSearchUiState.Empty -> MessageView(
                title = state.searchedFor?.let { stringResource(R.string.search_no_hits, it) }
                    ?: stringResource(R.string.search_prompt),
                body = null,
            )
            is TrackSearchUiState.Offline -> MessageView(
                title = stringResource(R.string.offline_title),
                body = stringResource(R.string.search_offline_body),
                actionLabel = stringResource(R.string.action_reload),
                onAction = onSearch,
            )
            is TrackSearchUiState.Error -> MessageView(
                title = stringResource(R.string.error_title),
                body = failureText(state.failure),
                actionLabel = stringResource(R.string.action_reload),
                onAction = onSearch,
            )
        }
    }
}

@Composable
private fun HitRow(track: Track, onTrackSelected: (Track) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onTrackSelected(track) }.testTag(TrackSearchTags.HIT),
        headlineContent = { Text(track.title) },
        supportingContent = { Text(listOfNotNull(track.artist, track.album).joinToString(" · ")) },
        trailingContent = if (track.broadcastable == false) {
            {
                Text(
                    stringResource(R.string.not_broadcastable_label),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            null
        },
    )
}
