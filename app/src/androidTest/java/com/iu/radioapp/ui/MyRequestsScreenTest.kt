package com.iu.radioapp.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import com.iu.radioapp.domain.SongRequestWithDelivery
import com.iu.radioapp.ui.common.StateTags
import com.iu.radioapp.ui.common.toClockText
import com.iu.radioapp.ui.requests.MyRequestsScreen
import com.iu.radioapp.ui.requests.MyRequestsTags
import com.iu.radioapp.ui.requests.MyRequestsUiState
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours

/** US3, request status (RAD-19). */
@RunWith(AndroidJUnit4::class)
class MyRequestsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val retried = mutableListOf<String>()

    private fun show(state: MyRequestsUiState) = compose.setContent {
        RadioAppTheme { MyRequestsScreen(state, onFindTrack = {}, onRefresh = {}, onRetry = { retried += it }) }
    }

    private fun row(
        key: String = "key-1",
        delivery: DeliveryStatus?,
        status: RequestStatus = RequestStatus.PENDING,
        reason: String? = null,
        scheduled: kotlin.time.Instant? = null,
    ) = SongRequestWithDelivery(
        SongRequest(
            idempotencyKey = key,
            requestId = if (delivery == DeliveryStatus.DELIVERED) "req-1" else null,
            trackId = "trk-1",
            trackTitle = "Sample Song",
            listenerId = "listener-1",
            message = null,
            createdAt = T0,
            status = status,
            rejectionReason = reason,
            scheduledBroadcast = scheduled,
        ),
        delivery,
    )

    @Test
    fun ak_3_5_queued_request_shows_as_open() {
        show(MyRequestsUiState.Content(listOf(row(delivery = DeliveryStatus.OPEN))))

        compose.onNodeWithText(str(R.string.delivery_open)).assertIsDisplayed()
    }

    @Test
    fun ak_3_6_station_status_with_scheduled_time_is_shown() {
        val scheduled = T0 + 1.hours
        show(MyRequestsUiState.Content(listOf(row(delivery = DeliveryStatus.DELIVERED, status = RequestStatus.ACCEPTED, scheduled = scheduled))))

        compose.onNodeWithText(str(R.string.request_status_accepted)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.request_scheduled, scheduled.toClockText())).assertIsDisplayed()
    }

    @Test
    fun ak_3_7_rejection_shows_reason_and_offers_no_resend() {
        show(MyRequestsUiState.Content(listOf(row(delivery = DeliveryStatus.REJECTED, status = RequestStatus.REJECTED, reason = "Wunschlimit erreicht"))))

        compose.onNodeWithText(str(R.string.delivery_rejected, "Wunschlimit erreicht")).assertIsDisplayed()
        compose.onAllNodesWithTag(MyRequestsTags.RETRY).assertCountEquals(0)
    }

    @Test
    fun ak_3_7_failed_request_can_be_resent_by_hand() {
        show(MyRequestsUiState.Content(listOf(row(delivery = DeliveryStatus.FAILED))))

        compose.onNodeWithText(str(R.string.delivery_failed)).assertIsDisplayed()
        compose.onNodeWithTag(MyRequestsTags.RETRY).performClick()
        assertEquals(listOf("key-1"), retried)
    }

    @Test
    fun empty_state_is_shown() {
        show(MyRequestsUiState.Empty)

        compose.onNodeWithText(str(R.string.my_requests_empty)).assertIsDisplayed()
    }

    @Test
    fun offline_keeps_the_list_under_a_notice() {
        show(MyRequestsUiState.Offline(listOf(row(delivery = DeliveryStatus.DELIVERED))))

        compose.onNodeWithTag(StateTags.OFFLINE_BANNER).assertIsDisplayed()
        compose.onNodeWithText("Sample Song").assertIsDisplayed()
    }

    @Test
    fun created_time_is_shown() {
        show(MyRequestsUiState.Content(listOf(row(delivery = DeliveryStatus.OPEN))))

        compose.onNodeWithText(str(R.string.request_created_at, T0.toClockText())).assertIsDisplayed()
    }
}
