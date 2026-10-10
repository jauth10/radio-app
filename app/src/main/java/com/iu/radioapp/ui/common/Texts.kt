package com.iu.radioapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iu.radioapp.R
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.domain.RequestStatus

/** A rejection shows the station's own reason, never just "failed". */
@Composable
fun failureText(failure: Failure): String = when (failure) {
    Failure.Connection -> stringResource(R.string.failure_connection)
    Failure.Server -> stringResource(R.string.failure_server)
    Failure.Unauthorized -> stringResource(R.string.failure_unauthorized)
    is Failure.Rejected -> stringResource(R.string.failure_rejected, failure.reason)
}

@Composable
fun refusalText(reason: RefusalReason): String = stringResource(
    when (reason) {
        RefusalReason.TRACK_NOT_BROADCASTABLE -> R.string.refusal_track_not_broadcastable
        RefusalReason.NO_SHOW_ON_AIR -> R.string.refusal_no_show_on_air
        RefusalReason.HOST_UNKNOWN -> R.string.refusal_host_unknown
        RefusalReason.CONTEXT_STALE -> R.string.refusal_context_stale
    }
)

@Composable
fun deliveryText(status: DeliveryStatus, rejectionReason: String?): String = when (status) {
    DeliveryStatus.OPEN -> stringResource(R.string.delivery_open)
    DeliveryStatus.DELIVERED -> stringResource(R.string.delivery_delivered)
    DeliveryStatus.REJECTED -> stringResource(R.string.delivery_rejected, rejectionReason.orEmpty())
    DeliveryStatus.FAILED -> stringResource(R.string.delivery_failed)
}

@Composable
fun requestStatusText(status: RequestStatus): String = stringResource(
    when (status) {
        RequestStatus.PENDING -> R.string.request_status_pending
        RequestStatus.IN_REVIEW -> R.string.request_status_in_review
        RequestStatus.ACCEPTED -> R.string.request_status_accepted
        RequestStatus.REJECTED -> R.string.request_status_rejected
    }
)
