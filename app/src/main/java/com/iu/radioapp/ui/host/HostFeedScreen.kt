package com.iu.radioapp.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.ReceivedRating
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.failureText
import com.iu.radioapp.ui.common.toClockText
import java.util.Locale

object HostFeedTags {
    const val CONNECTION = "host_connection"
    const val NUMBERS = "host_numbers"
    const val NUMBERS_HIDDEN = "host_numbers_hidden"
    const val EMPTY = "host_ratings_empty"
    const val SIGN_OUT = "host_sign_out"
    fun rating(ratingId: String) = "host_rating_$ratingId"
}

@Composable
fun HostFeedRoute(viewModel: HostFeedViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HostFeedScreen(state = state, onSignOut = viewModel::signOut, latency = LocalLatencyRecorder.current)
}

@Composable
fun HostFeedScreen(state: HostFeedUiState, onSignOut: () -> Unit, latency: LatencyRecorder = NoLatencyRecorder) {
    val view = when (state) {
        HostFeedUiState.Loading -> {
            LoadingView()
            return
        }
        is HostFeedUiState.Content -> state.view
        is HostFeedUiState.Empty -> state.view
        is HostFeedUiState.Offline -> state.view
        is HostFeedUiState.Error -> state.view
    }
    SideEffect { latency.onListed(view.ratings) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ConnectionBanner(view.connection, view.showKnown) }
        when (state) {
            is HostFeedUiState.Offline -> item { NoticeBanner(stringResource(R.string.host_offline_hint)) }
            is HostFeedUiState.Error -> item { NoticeBanner(failureText(state.failure), isError = true) }
            else -> Unit
        }
        item { NumbersCard(view) }
        item { Text(stringResource(R.string.host_ratings_title), style = MaterialTheme.typography.titleMedium) }
        if (view.ratings.isEmpty()) {
            item {
                Text(stringResource(R.string.host_ratings_empty), modifier = Modifier.testTag(HostFeedTags.EMPTY))
            }
        }
        items(view.ratings, key = { it.rating.ratingId }) { RatingRow(it, latency) }
        item {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.testTag(HostFeedTags.SIGN_OUT)) {
                Text(stringResource(R.string.action_logout))
            }
        }
    }
}

@Composable
private fun ConnectionBanner(connection: ConnectionState, showKnown: Boolean) {
    val text = when (connection) {
        ConnectionState.Connecting -> stringResource(R.string.host_connection_connecting)
        ConnectionState.Connected -> stringResource(R.string.host_connection_connected)
        is ConnectionState.Reconnecting -> stringResource(R.string.host_connection_reconnecting, connection.failedAttempts)
        ConnectionState.Polling -> stringResource(
            if (showKnown) R.string.host_connection_polling else R.string.host_connection_polling_no_show
        )
    }
    val limited = connection is ConnectionState.Reconnecting || connection == ConnectionState.Polling
    Surface(
        color = if (limited) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag(HostFeedTags.CONNECTION),
    ) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun NumbersCard(view: HostFeedView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.host_numbers_title), style = MaterialTheme.typography.labelLarge)
            val numbers = view.numbers
            if (numbers != null) {
                Column(modifier = Modifier.testTag(HostFeedTags.NUMBERS)) { NumbersText(numbers) }
            } else {
                Text(
                    stringResource(if (view.talkSegment) R.string.host_numbers_talk_segment else R.string.host_numbers_unavailable),
                    modifier = Modifier.testTag(HostFeedTags.NUMBERS_HIDDEN),
                )
            }
        }
    }
}

@Composable
private fun NumbersText(numbers: RatingAggregate) {
    Text(
        pluralStringResource(
            R.plurals.host_numbers_playlist,
            numbers.playlistRatingCount,
            average(numbers.averagePlaylistRating),
            numbers.playlistRatingCount,
        )
    )
    Text(
        pluralStringResource(
            R.plurals.host_numbers_host,
            numbers.hostRatingCount,
            average(numbers.averageHostRating),
            numbers.hostRatingCount,
        )
    )
}

private fun average(value: Double): String = String.format(Locale.GERMANY, "%.1f", value)

@Composable
private fun RatingRow(received: ReceivedRating, latency: LatencyRecorder) {
    val rating = received.rating
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // t1 of the latency measurement: lazy rows are only drawn while on screen (RAD-18).
            .drawWithContent {
                drawContent()
                latency.onDisplayed(received)
            }
            .testTag(HostFeedTags.rating(rating.ratingId))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (rating.target == RatingTarget.PLAYLIST) R.string.rating_tab_playlist else R.string.rating_tab_host),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(stringResource(R.string.rating_value_description, rating.value), style = MaterialTheme.typography.titleMedium)
            }
            rating.comment?.let { Text(it) }
            rating.displayName?.let { Text(stringResource(R.string.host_rating_from, it)) }
            Text(
                stringResource(R.string.host_rating_received, rating.serverReceivedAt.toClockText()),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
