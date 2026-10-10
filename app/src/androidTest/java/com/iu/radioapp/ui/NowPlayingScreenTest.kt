package com.iu.radioapp.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.Track
import com.iu.radioapp.ui.common.StateTags
import com.iu.radioapp.ui.common.toClockText
import com.iu.radioapp.ui.nowplaying.CachedTrack
import com.iu.radioapp.ui.nowplaying.NowPlayingScreen
import com.iu.radioapp.ui.nowplaying.NowPlayingTags
import com.iu.radioapp.ui.nowplaying.NowPlayingUiState
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/** One test per acceptance criterion of US1 (RAD-19). */
@RunWith(AndroidJUnit4::class)
class NowPlayingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var reloads = 0
    private val requested = mutableListOf<Track>()

    private fun show(state: NowPlayingUiState) = compose.setContent {
        RadioAppTheme { NowPlayingScreen(state, onReload = { reloads++ }, onRequestTrack = { requested += it }) }
    }

    private val history = listOf(
        playback(track("trk-2", "Newest Song"), T0 + 4.minutes),
        playback(track("trk-1", "Older Song"), T0),
    )

    @Test
    fun ak_1_1_shows_artist_title_album_and_host() {
        show(NowPlayingUiState.Content(nowPlaying(), history))

        compose.onNodeWithText("Sample Song").assertIsDisplayed()
        compose.onNodeWithText("The Fake Band").assertIsDisplayed()
        compose.onNodeWithText("Fake Album").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.now_playing_host, "Alex Host")).assertIsDisplayed()
    }

    @Test
    fun ak_1_2_talk_segment_is_a_hint_not_an_error() {
        show(NowPlayingUiState.Empty(history))

        compose.onNodeWithText(str(R.string.talk_segment_title)).assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.error_title)).assertCountEquals(0)
    }

    @Test
    fun ak_1_3_offline_shows_last_track_with_fetch_time() {
        show(NowPlayingUiState.Offline(CachedTrack(nowPlaying(), fetchedAt = T0, isStale = false), history))

        compose.onNodeWithText("Sample Song").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.stand_at, T0.toClockText())).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.failure_connection)).assertIsDisplayed()
        compose.onAllNodesWithTag(StateTags.STALE_MARKER).assertCountEquals(0)
    }

    @Test
    fun ak_1_4_cache_older_than_five_minutes_is_marked_stale() {
        show(NowPlayingUiState.Offline(CachedTrack(nowPlaying(), fetchedAt = T0, isStale = true), history))

        compose.onNodeWithTag(StateTags.STALE_MARKER).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.stale_hint)).assertIsDisplayed()
    }

    @Test
    fun ak_1_5_no_connection_and_no_cache_names_the_cause_and_offers_reload() {
        show(NowPlayingUiState.Offline(cached = null, history = emptyList()))

        compose.onNodeWithText(str(R.string.offline_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_reload)).performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun ak_1_6_history_is_shown_newest_first_also_offline() {
        show(NowPlayingUiState.Offline(cached = null, history = history))

        val rows = compose.onAllNodesWithTag(NowPlayingTags.HISTORY)
        rows.assertCountEquals(2)
        // A list row merges its texts into one node.
        rows[0].assert(hasText("Newest Song"))
        rows[1].assert(hasText("Older Song"))
    }

    @Test
    fun error_state_shows_the_cause_and_keeps_the_cache() {
        show(NowPlayingUiState.Error(Failure.Server, CachedTrack(nowPlaying(), T0, isStale = false), history))

        compose.onNodeWithText(str(R.string.failure_server)).assertIsDisplayed()
        compose.onNodeWithText("Sample Song").assertIsDisplayed()
    }

    @Test
    fun loading_state_is_shown() {
        show(NowPlayingUiState.Loading)

        compose.onNodeWithTag(StateTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun ak_3_4_a_track_from_the_history_can_be_requested_without_the_archive() {
        show(NowPlayingUiState.Offline(cached = null, history = history))

        compose.onNodeWithTag(NowPlayingTags.CURRENT).assertExists()
        compose.onAllNodesWithText(str(R.string.action_request_track)).onFirst().performClick()
        assertEquals("trk-2", requested.single().trackId)
    }
}
