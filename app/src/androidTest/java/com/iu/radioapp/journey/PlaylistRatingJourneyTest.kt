package com.iu.radioapp.journey

import androidx.compose.ui.test.onNodeWithTag
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.rating.RatingTags
import com.iu.radioapp.ui.str
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class PlaylistRatingJourneyTest : JourneyTest() {

    @Test
    fun us2_a_playlist_rating_is_pending_first_and_delivered_once_the_network_is_there() {
        launchApp()
        openRating(str(R.string.rating_tab_playlist))
        waitForText(str(R.string.rating_subject_playlist))

        submitRating(4)
        waitForText(str(R.string.delivery_open))
        networkBack()

        waitForText(str(R.string.delivery_delivered))
    }

    @Test
    fun us2_error_a_rejection_shows_the_station_reason_and_offers_no_resend() {
        feedback.nextFailure = Failure.Rejected("Du hast dafür in dieser Stunde bereits eine Bewertung abgegeben.", retryable = true)
        launchApp()
        openRating(str(R.string.rating_tab_playlist))

        submitRating(2)
        networkBack()

        waitForText(str(R.string.delivery_rejected, "Du hast dafür in dieser Stunde bereits eine Bewertung abgegeben."))
        composeRule.onNodeWithTag(RatingTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun us2_offline_a_rating_is_queued_from_the_stored_show_and_delivered_later() {
        launchApp()
        waitForText("Sample Song")
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = ALWAYS
        openRating(str(R.string.rating_tab_playlist))

        waitForText(str(R.string.rating_offline_hint))
        submitRating(5)
        waitForText(str(R.string.delivery_open))
        assertTrue(hasNoText(str(R.string.delivery_delivered)))

        networkBack()
        waitForText(str(R.string.delivery_delivered))
    }
}
