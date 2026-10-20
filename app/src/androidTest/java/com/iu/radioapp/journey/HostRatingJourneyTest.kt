package com.iu.radioapp.journey

import androidx.compose.ui.test.onNodeWithTag
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.rating.RatingTags
import com.iu.radioapp.ui.str
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class HostRatingJourneyTest : JourneyTest() {

    @Test
    fun us4_a_host_rating_refers_to_the_running_host_and_is_delivered() {
        launchApp()
        openRating(str(R.string.rating_tab_host))
        waitForText(str(R.string.rating_subject_host, "Alex Host"))

        submitRating(5)
        waitForText(str(R.string.delivery_open))
        networkBack()

        waitForText(str(R.string.delivery_delivered))
    }

    @Test
    fun us4_error_a_rejection_shows_the_station_reason_and_offers_no_resend() {
        feedback.nextFailure = Failure.Rejected("Simulierter Konflikt", retryable = false)
        launchApp()
        openRating(str(R.string.rating_tab_host))

        submitRating(3)
        networkBack()

        waitForText(str(R.string.delivery_rejected, "Simulierter Konflikt"))
        composeRule.onNodeWithTag(RatingTags.RETRY).assertDoesNotExist()
    }

    @Test
    fun us4_offline_the_host_is_unknown_and_the_rating_is_refused_with_the_reason() {
        launchApp()
        waitForText("Sample Song")
        playout.nextFailure = Failure.Connection
        playout.failureRepeatCount = ALWAYS
        openRating(str(R.string.rating_tab_host))

        waitForText(str(R.string.refusal_host_unknown))
    }
}
