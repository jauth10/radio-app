package com.iu.radioapp.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.ConnectionState
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.FeedSource
import com.iu.radioapp.domain.RatingAggregate
import com.iu.radioapp.domain.RatingEvent
import com.iu.radioapp.domain.RatingTarget
import com.iu.radioapp.domain.ReceivedRating
import com.iu.radioapp.ui.common.StateTags
import com.iu.radioapp.ui.common.toClockText
import com.iu.radioapp.ui.host.HostFeedScreen
import com.iu.radioapp.ui.host.HostFeedTags
import com.iu.radioapp.ui.host.HostFeedUiState
import com.iu.radioapp.ui.host.HostFeedView
import com.iu.radioapp.ui.host.LatencyRecorder
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** US5, information in the host view (RAD-19). */
@RunWith(AndroidJUnit4::class)
class HostFeedScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var signOuts = 0

    private val aggregate = RatingAggregate(4.25, 4, 4.8, 5, T0, T0)

    private fun rating(id: String = "rat-9", comment: String? = "Toll moderiert", displayName: String? = "Listener A") =
        ReceivedRating(RatingEvent(id, RatingTarget.HOST, 5, comment, T0, displayName), FeedSource.EVENT_CHANNEL)

    private fun view(
        ratings: List<ReceivedRating> = listOf(rating()),
        connection: ConnectionState = ConnectionState.Connected,
        showKnown: Boolean = true,
        numbers: RatingAggregate? = aggregate,
        talkSegment: Boolean = false,
    ) = HostFeedView(ratings, connection, showKnown, numbers, talkSegment)

    private fun show(state: HostFeedUiState) = compose.setContent {
        RadioAppTheme { HostFeedScreen(state = state, onSignOut = { signOuts++ }) }
    }

    @Test
    fun loading_shows_the_loading_state() {
        show(HostFeedUiState.Loading)

        compose.onNodeWithTag(StateTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun ak_5_5_a_rating_shows_value_comment_name_and_time() {
        show(HostFeedUiState.Content(view()))

        compose.onNodeWithTag(HostFeedTags.rating("rat-9")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.rating_value_description, 5)).assertIsDisplayed()
        compose.onNodeWithText("Toll moderiert").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.host_rating_from, "Listener A")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.host_rating_received, T0.toClockText())).assertIsDisplayed()
    }

    @Test
    fun ak_5_5_a_rating_without_name_shows_no_name_line() {
        show(HostFeedUiState.Content(view(ratings = listOf(rating(displayName = null)))))

        compose.onAllNodesWithText(str(R.string.host_rating_from, "Listener A")).assertCountEquals(0)
    }

    @Test
    fun ak_5_6_average_and_count_for_playlist_and_host() {
        show(HostFeedUiState.Content(view()))

        compose.onNodeWithTag(HostFeedTags.NUMBERS).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.host_numbers_playlist, 4, "4,3", 4)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.host_numbers_host, 5, "4,8", 5)).assertIsDisplayed()
    }

    @Test
    fun ak_5_6_a_single_rating_is_counted_in_the_singular() {
        show(HostFeedUiState.Content(view(numbers = aggregate.copy(playlistRatingCount = 1, averagePlaylistRating = 4.0))))

        compose.onNodeWithText(plural(R.plurals.host_numbers_playlist, 1, "4,0", 1)).assertIsDisplayed()
    }

    @Test
    fun ak_5_7_connected_is_visible() {
        show(HostFeedUiState.Content(view(connection = ConnectionState.Connected)))

        compose.onNodeWithTag(HostFeedTags.CONNECTION).assert(hasAnyDescendant(hasText(str(R.string.host_connection_connected))))
    }

    @Test
    fun ak_5_7_reconnecting_shows_the_attempt_count() {
        show(HostFeedUiState.Content(view(connection = ConnectionState.Reconnecting(3))))

        compose.onNodeWithTag(HostFeedTags.CONNECTION).assert(hasAnyDescendant(hasText(str(R.string.host_connection_reconnecting, 3))))
    }

    @Test
    fun ak_5_8_polling_mode_is_visible() {
        show(HostFeedUiState.Content(view(connection = ConnectionState.Polling)))

        compose.onNodeWithTag(HostFeedTags.CONNECTION).assert(hasAnyDescendant(hasText(str(R.string.host_connection_polling))))
    }

    @Test
    fun polling_without_a_known_show_says_so() {
        show(HostFeedUiState.Content(view(connection = ConnectionState.Polling, showKnown = false)))

        compose.onNodeWithTag(HostFeedTags.CONNECTION).assert(hasAnyDescendant(hasText(str(R.string.host_connection_polling_no_show))))
    }

    @Test
    fun ak_5_9_talk_segment_shows_no_numbers() {
        show(HostFeedUiState.Content(view(numbers = null, talkSegment = true)))

        compose.onAllNodesWithTag(HostFeedTags.NUMBERS).assertCountEquals(0)
        compose.onNodeWithTag(HostFeedTags.NUMBERS_HIDDEN).assertTextContains(str(R.string.host_numbers_talk_segment))
    }

    @Test
    fun ak_5_9_offline_shows_no_numbers_but_keeps_the_ratings() {
        show(HostFeedUiState.Offline(view(numbers = null)))

        compose.onAllNodesWithTag(HostFeedTags.NUMBERS).assertCountEquals(0)
        compose.onNodeWithTag(HostFeedTags.NUMBERS_HIDDEN).assertTextContains(str(R.string.host_numbers_unavailable))
        compose.onNodeWithText(str(R.string.host_offline_hint)).assertIsDisplayed()
        compose.onNodeWithTag(HostFeedTags.rating("rat-9")).assertIsDisplayed()
    }

    @Test
    fun error_names_the_failure_without_numbers() {
        show(HostFeedUiState.Error(Failure.Server, view(numbers = null)))

        compose.onNodeWithText(str(R.string.failure_server)).assertIsDisplayed()
        compose.onAllNodesWithTag(HostFeedTags.NUMBERS).assertCountEquals(0)
    }

    @Test
    fun empty_says_no_rating_has_arrived_yet() {
        show(HostFeedUiState.Empty(view(ratings = emptyList())))

        compose.onNodeWithTag(HostFeedTags.EMPTY).assertIsDisplayed()
        compose.onNodeWithTag(HostFeedTags.NUMBERS).assertIsDisplayed()
    }

    @Test
    fun latency_is_reported_for_drawn_rows_only() {
        val displayed = mutableSetOf<String>()
        val latency = object : LatencyRecorder {
            override fun onListed(ratings: List<ReceivedRating>) = Unit
            override fun onDisplayed(received: ReceivedRating) {
                displayed += received.rating.ratingId
            }
        }
        val ratings = (1..30).map { rating(id = "rat-$it") }
        compose.setContent {
            RadioAppTheme { HostFeedScreen(HostFeedUiState.Content(view(ratings = ratings)), onSignOut = {}, latency = latency) }
        }
        compose.waitForIdle()

        assertTrue("rat-1" in displayed)
        assertFalse("rat-30" in displayed)

        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(HostFeedTags.rating("rat-30")))
        compose.waitForIdle()

        assertTrue("rat-30" in displayed)
    }

    @Test
    fun signing_out_is_possible() {
        show(HostFeedUiState.Content(view()))

        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(HostFeedTags.SIGN_OUT))
        compose.onNodeWithTag(HostFeedTags.SIGN_OUT).performClick()

        assertEquals(1, signOuts)
    }
}
