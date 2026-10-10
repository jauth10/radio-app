package com.iu.radioapp.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.NowPlaying
import com.iu.radioapp.domain.Playback
import com.iu.radioapp.domain.Track
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.StaleMarker
import com.iu.radioapp.ui.common.failureText
import com.iu.radioapp.ui.common.toClockText

object NowPlayingTags {
    const val CURRENT = "now_playing_current"
    const val HISTORY = "now_playing_history"
}

@Composable
fun NowPlayingRoute(onRequestTrack: (Track) -> Unit, viewModel: NowPlayingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NowPlayingScreen(state = state, onReload = viewModel::reload, onRequestTrack = onRequestTrack)
}

@Composable
fun NowPlayingScreen(state: NowPlayingUiState, onReload: () -> Unit, onRequestTrack: (Track) -> Unit) {
    if (state is NowPlayingUiState.Loading) {
        LoadingView()
        return
    }
    val history = when (state) {
        is NowPlayingUiState.Content -> state.history
        is NowPlayingUiState.Empty -> state.history
        is NowPlayingUiState.Offline -> state.history
        is NowPlayingUiState.Error -> state.history
        NowPlayingUiState.Loading -> emptyList()
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { CurrentSection(state, onReload, onRequestTrack) }
        item {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        if (history.isEmpty()) {
            item { Text(stringResource(R.string.history_empty), modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        items(history, key = { it.playbackId }) { playback ->
            HistoryRow(playback, onRequestTrack)
            HorizontalDivider()
        }
    }
}

@Composable
private fun CurrentSection(state: NowPlayingUiState, onReload: () -> Unit, onRequestTrack: (Track) -> Unit) {
    Column(modifier = Modifier.padding(16.dp).testTag(NowPlayingTags.CURRENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            is NowPlayingUiState.Content -> CurrentCard(state.nowPlaying, label = stringResource(R.string.now_playing_label), onRequestTrack = onRequestTrack)
            is NowPlayingUiState.Empty -> MessageView(
                title = stringResource(R.string.talk_segment_title),
                body = stringResource(R.string.talk_segment_body),
            )
            is NowPlayingUiState.Offline -> if (state.cached == null) {
                MessageView(
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.offline_no_data),
                    actionLabel = stringResource(R.string.action_reload),
                    onAction = onReload,
                )
            } else {
                NoticeBanner(stringResource(R.string.failure_connection))
                CachedCard(state.cached, onReload, onRequestTrack)
            }
            is NowPlayingUiState.Error -> if (state.cached == null) {
                MessageView(
                    title = stringResource(R.string.error_title),
                    body = failureText(state.failure),
                    actionLabel = stringResource(R.string.action_reload),
                    onAction = onReload,
                )
            } else {
                NoticeBanner(failureText(state.failure), isError = true)
                CachedCard(state.cached, onReload, onRequestTrack)
            }
            NowPlayingUiState.Loading -> Unit
        }
    }
}

@Composable
private fun CachedCard(cached: CachedTrack, onReload: () -> Unit, onRequestTrack: (Track) -> Unit) {
    CurrentCard(cached.nowPlaying, label = stringResource(R.string.now_playing_last_known), onRequestTrack = onRequestTrack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stand_at, cached.fetchedAt.toClockText()), style = MaterialTheme.typography.bodySmall)
            if (cached.isStale) StaleMarker()
        }
        if (cached.isStale) Text(stringResource(R.string.stale_hint), style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = onReload) { Text(stringResource(R.string.action_reload)) }
}

@Composable
private fun CurrentCard(
    nowPlaying: NowPlaying,
    label: String,
    onRequestTrack: (Track) -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val track = nowPlaying.playback.track
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(track.title, style = MaterialTheme.typography.headlineSmall)
            Text(track.artist, style = MaterialTheme.typography.titleMedium)
            track.album?.let { Text(it) }
            Text(stringResource(R.string.now_playing_started, nowPlaying.playback.startedAt.toClockText()), style = MaterialTheme.typography.bodySmall)
            nowPlaying.show?.host?.let { Text(stringResource(R.string.now_playing_host, it.displayName)) }
            extra()
            TextButton(onClick = { onRequestTrack(track) }) { Text(stringResource(R.string.action_request_track)) }
        }
    }
}

@Composable
private fun HistoryRow(playback: Playback, onRequestTrack: (Track) -> Unit) {
    val track = playback.track
    ListItem(
        modifier = Modifier.testTag(NowPlayingTags.HISTORY),
        headlineContent = { Text(track.title) },
        supportingContent = { Text(listOfNotNull(track.artist, track.album).joinToString(" · ")) },
        overlineContent = { Text(playback.startedAt.toClockText()) },
        trailingContent = { TextButton(onClick = { onRequestTrack(track) }) { Text(stringResource(R.string.action_request_track)) } },
    )
}
