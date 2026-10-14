package com.iu.radioapp.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.DeliveryStatus
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.RefusalReason
import com.iu.radioapp.ui.common.StateTags
import com.iu.radioapp.ui.rating.RatingPanel
import com.iu.radioapp.ui.rating.RatingScreen
import com.iu.radioapp.ui.rating.RatingTags
import com.iu.radioapp.ui.rating.RatingUiState
import com.iu.radioapp.ui.rating.SubmittedRating
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** US2 and US4 (RAD-19); US4 shares the screen with the host as target. */
@RunWith(AndroidJUnit4::class)
class RatingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val values = mutableListOf<Int>()
    private var submits = 0
    private var retries = 0

    private fun show(state: RatingUiState) = compose.setContent {
        RadioAppTheme {
            RatingScreen(
                state = state,
                onValueSelected = { values += it },
                onCommentChange = {},
                onSubmit = { submits++ },
                onReload = {},
                onRetryDelivery = { retries++ },
            )
        }
    }

    private fun panel(
        target: RatingTarget = RatingTarget.PLAYLIST,
        refusal: RefusalReason? = null,
        locked: Boolean = false,
        value: Int? = 4,
        isStale: Boolean = false,
        withHost: Boolean = true,
    ) = RatingPanel(
        target = target,
        context = ratingContext(host = if (withHost) host else null, isStale = isStale),
        refusal = refusal,
        locked = locked,
        value = value,
        comment = "",
        submitting = false,
        submitRefusal = null,
        submitFailure = null,
    )

    private fun submitted(status: DeliveryStatus, reason: String? = null) = SubmittedRating("key-1", status, reason)

    @Test
    fun ak_2_1_only_values_one_to_five_and_an_optional_comment() {
        show(RatingUiState.Content(panel(value = null), submitted = null))

        (1..5).forEach { compose.onNodeWithTag(RatingTags.value(it)).assertIsDisplayed() }
        compose.onAllNodesWithTag(RatingTags.value(0)).assertCountEquals(0)
        compose.onAllNodesWithTag(RatingTags.value(6)).assertCountEquals(0)
        compose.onNodeWithTag(RatingTags.COMMENT).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsNotEnabled()
        compose.onNodeWithTag(RatingTags.value(3)).performClick()
        assertEquals(listOf(3), values)
    }

    @Test
    fun ak_2_2_rating_refers_to_the_running_show() {
        show(RatingUiState.Content(panel(), submitted = null))

        compose.onNodeWithText(str(R.string.rating_subject_playlist)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsEnabled().performClick()
        assertEquals(1, submits)
    }

    @Test
    fun ak_2_3_talk_segment_allows_no_rating_and_says_why() {
        show(RatingUiState.Empty(submitted = null))

        compose.onNodeWithText(str(R.string.refusal_no_show_on_air)).assertIsDisplayed()
        compose.onAllNodesWithTag(RatingTags.SUBMIT).assertCountEquals(0)
    }

    @Test
    fun ak_2_4_stale_context_is_refused() {
        show(RatingUiState.Offline(panel(refusal = RefusalReason.CONTEXT_STALE, isStale = true), submitted = null))

        compose.onNodeWithTag(StateTags.STALE_MARKER).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.refusal_context_stale)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun ak_2_5_offline_rating_is_still_possible() {
        show(RatingUiState.Offline(panel(), submitted = null))

        compose.onNodeWithText(str(R.string.rating_offline_hint)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun ak_2_6_delivery_status_is_visible() {
        show(RatingUiState.Content(panel(locked = true), submitted(DeliveryStatus.DELIVERED)))

        compose.onNodeWithText(str(R.string.delivery_delivered)).assertIsDisplayed()
    }

    @Test
    fun ak_2_7_rejection_shows_the_station_reason_and_no_resend() {
        show(RatingUiState.Content(panel(locked = true), submitted(DeliveryStatus.REJECTED, "Bereits bewertet")))

        compose.onNodeWithText(str(R.string.delivery_rejected, "Bereits bewertet")).assertIsDisplayed()
        compose.onAllNodesWithTag(RatingTags.RETRY).assertCountEquals(0)
    }

    @Test
    fun ak_2_8_failed_rating_can_be_resent_by_hand() {
        show(RatingUiState.Content(panel(locked = true), submitted(DeliveryStatus.FAILED)))

        compose.onNodeWithText(str(R.string.delivery_failed)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.RETRY).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun session_lock_disables_the_button() {
        show(RatingUiState.Content(panel(locked = true), submitted(DeliveryStatus.OPEN)))

        compose.onNodeWithText(str(R.string.rating_locked)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun ak_4_2_host_rating_names_the_host_of_the_running_show() {
        show(RatingUiState.Content(panel(target = RatingTarget.HOST), submitted = null))

        compose.onNodeWithText(str(R.string.rating_subject_host, "Alex Host")).assertIsDisplayed()
    }

    @Test
    fun ak_4_3_unknown_host_allows_no_rating_and_says_why() {
        show(RatingUiState.Content(panel(target = RatingTarget.HOST, refusal = RefusalReason.HOST_UNKNOWN, withHost = false), submitted = null))

        compose.onNodeWithText(str(R.string.refusal_host_unknown)).assertIsDisplayed()
        compose.onNodeWithTag(RatingTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun offline_without_context_offers_reload() {
        show(RatingUiState.Offline(panel = null, submitted = null))

        compose.onNodeWithText(str(R.string.offline_no_data)).assertIsDisplayed()
    }
}
