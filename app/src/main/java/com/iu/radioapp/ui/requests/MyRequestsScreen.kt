package com.iu.radioapp.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequestWithDelivery
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.deliveryText
import com.iu.radioapp.ui.common.failureText
import com.iu.radioapp.ui.common.requestStatusText
import com.iu.radioapp.ui.common.toClockText

object MyRequestsTags {
    const val ROW = "my_request_row"
    const val RETRY = "my_request_retry"
}

@Composable
fun MyRequestsRoute(onFindTrack: () -> Unit, viewModel: MyRequestsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MyRequestsScreen(state, onFindTrack, viewModel::refresh, viewModel::retry)
}

@Composable
fun MyRequestsScreen(
    state: MyRequestsUiState,
    onFindTrack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFindTrack) { Text(stringResource(R.string.action_find_track)) }
            OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
        }
        when (state) {
            MyRequestsUiState.Loading -> LoadingView()
            MyRequestsUiState.Empty -> MessageView(title = stringResource(R.string.my_requests_empty), body = null)
            is MyRequestsUiState.Content -> RequestList(state.requests, onRetry)
            is MyRequestsUiState.Offline -> {
                NoticeBanner(stringResource(R.string.requests_offline_hint))
                RequestList(state.requests, onRetry)
            }
            is MyRequestsUiState.Error -> {
                NoticeBanner(failureText(state.failure), isError = true)
                RequestList(state.requests, onRetry)
            }
        }
    }
}

@Composable
private fun RequestList(requests: List<SongRequestWithDelivery>, onRetry: (String) -> Unit) {
    LazyColumn {
        items(requests, key = { it.request.idempotencyKey }) { row ->
            RequestRow(row, onRetry)
            HorizontalDivider()
        }
    }
}

@Composable
private fun RequestRow(row: SongRequestWithDelivery, onRetry: (String) -> Unit) {
    val request = row.request
    ListItem(
        modifier = Modifier.testTag(MyRequestsTags.ROW),
        headlineContent = { Text(request.trackTitle ?: stringResource(R.string.request_unknown_title, request.trackId)) },
        overlineContent = { Text(stringResource(R.string.request_created_at, request.createdAt.toClockText())) },
        supportingContent = {
            Column {
                request.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(statusText(row))
                request.scheduledBroadcast?.let { Text(stringResource(R.string.request_scheduled, it.toClockText())) }
            }
        },
        trailingContent = if (row.deliveryStatus == DeliveryStatus.FAILED) {
            {
                TextButton(onClick = { onRetry(request.idempotencyKey) }, modifier = Modifier.testTag(MyRequestsTags.RETRY)) {
                    Text(stringResource(R.string.action_retry_delivery))
                }
            }
        } else {
            null
        },
    )
}

// Until delivery the device's status matters; afterwards the station's.
@Composable
private fun statusText(row: SongRequestWithDelivery): String {
    val request = row.request
    return when (row.deliveryStatus) {
        DeliveryStatus.OPEN, DeliveryStatus.FAILED, DeliveryStatus.REJECTED ->
            deliveryText(row.deliveryStatus, request.rejectionReason)
        DeliveryStatus.DELIVERED, null -> if (request.status == RequestStatus.REJECTED && request.rejectionReason != null) {
            stringResource(R.string.delivery_rejected, request.rejectionReason)
        } else {
            requestStatusText(request.status)
        }
    }
}
