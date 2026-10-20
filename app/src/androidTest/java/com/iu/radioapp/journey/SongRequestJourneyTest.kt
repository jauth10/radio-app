package com.iu.radioapp.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.navigation.TopLevel
import com.iu.radioapp.ui.nowplaying.NowPlayingTags
import com.iu.radioapp.ui.request.SongRequestTags
import com.iu.radioapp.ui.requests.MyRequestsTags
import com.iu.radioapp.ui.search.TrackSearchTags
import com.iu.radioapp.ui.str
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class SongRequestJourneyTest : JourneyTest() {

    @Test
    fun us3_a_track_found_in_the_archive_is_requested_and_reaches_the_station() {
        launchApp()
        requestFromSearch("Second", "Second Song", message = "Bitte laut")

        waitForText(str(R.string.delivery_open))
        networkBack()

        waitForText(str(R.string.request_status_pending))
    }

    @Test
    fun us3_error_a_rejection_shows_the_station_reason_and_offers_no_resend() {
        launchApp()
        requestFromSearch("Second", "Second Song")
        waitForText(str(R.string.delivery_open))

        requests.nextFailure = Failure.Rejected("Simulierte Ablehnung", retryable = false)
        networkBack()

        waitForText(str(R.string.delivery_rejected, "Simulierte Ablehnung"))
        composeRule.onNodeWithTag(MyRequestsTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun us3_offline_an_unreachable_archive_still_lets_a_request_be_queued() {
        archive.nextFailure = Failure.Connection
        archive.failureRepeatCount = ALWAYS
        launchApp()
        waitForText("Sample Song")
        composeRule.onNode(
            hasText(str(R.string.action_request_track)) and hasAnyAncestor(hasTestTag(NowPlayingTags.CURRENT)),
            useUnmergedTree = true,
        ).performClick()

        waitForText(str(R.string.request_archive_offline))
        composeRule.onNodeWithTag(SongRequestTags.SUBMIT).performClick()
        waitForText(str(R.string.request_queued))
        composeRule.onNodeWithText(str(R.string.action_show_my_requests)).performClick()

        waitForText(str(R.string.delivery_open))
        networkBack()
        waitForText(str(R.string.request_status_pending))
    }

    private fun requestFromSearch(query: String, title: String, message: String? = null) {
        waitForText("Sample Song")
        openArea(TopLevel.REQUESTS)
        waitForText(str(R.string.action_find_track))
        composeRule.onNodeWithText(str(R.string.action_find_track)).performClick()
        composeRule.onNodeWithTag(TrackSearchTags.FIELD).performTextInput(query)
        composeRule.onNodeWithText(str(R.string.action_search)).performClick()
        waitForText(title)
        composeRule.onNodeWithText(title).performClick()

        waitForTag(SongRequestTags.SUBMIT)
        message?.let { composeRule.onNodeWithTag(SongRequestTags.MESSAGE).performTextInput(it) }
        composeRule.onNodeWithTag(SongRequestTags.SUBMIT).performClick()
        waitForText(str(R.string.request_queued))
        composeRule.onNodeWithText(str(R.string.action_show_my_requests)).performClick()
    }
}
