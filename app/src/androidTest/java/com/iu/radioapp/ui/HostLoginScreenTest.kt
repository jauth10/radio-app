package com.iu.radioapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.host.HostLoginScreen
import com.iu.radioapp.ui.host.HostLoginTags
import com.iu.radioapp.ui.host.HostLoginUiState
import com.iu.radioapp.ui.theme.RadioAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** US5, unlocking the host view (RAD-19). */
@RunWith(AndroidJUnit4::class)
class HostLoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var logins = 0

    private fun show(state: HostLoginUiState, sessionExpired: Boolean = false) = compose.setContent {
        RadioAppTheme {
            HostLoginScreen(state = state, sessionExpired = sessionExpired, onCodeChange = {}, onLogin = { logins++ })
        }
    }

    @Test
    fun empty_form_cannot_be_sent() {
        show(HostLoginUiState.Empty)

        compose.onNodeWithTag(HostLoginTags.CODE).assertIsDisplayed()
        compose.onNodeWithTag(HostLoginTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun ak_5_1_a_typed_code_can_be_sent() {
        show(HostLoginUiState.Content("ALEX-2026"))

        compose.onNodeWithTag(HostLoginTags.SUBMIT).assertIsEnabled().performClick()

        assertEquals(1, logins)
    }

    @Test
    fun loading_shows_progress_and_blocks_a_second_login() {
        show(HostLoginUiState.Loading("ALEX-2026"))

        compose.onNodeWithTag(HostLoginTags.MESSAGE).assertTextContains(str(R.string.host_login_running))
        compose.onNodeWithTag(HostLoginTags.SUBMIT).assertIsNotEnabled()
    }

    @Test
    fun ak_5_2_an_invalid_code_is_named_as_such() {
        show(HostLoginUiState.Error("0000", Failure.Unauthorized))

        compose.onNodeWithTag(HostLoginTags.MESSAGE).assertTextContains(str(R.string.host_login_invalid))
    }

    @Test
    fun ak_5_3_the_lockout_shows_the_station_reason() {
        val reason = "too many failed login attempts"
        show(HostLoginUiState.Error("0000", Failure.Rejected(reason, retryable = true)))

        compose.onNodeWithTag(HostLoginTags.MESSAGE).assertTextContains(str(R.string.host_login_rejected, reason))
    }

    @Test
    fun offline_names_the_missing_connection() {
        show(HostLoginUiState.Offline("ALEX-2026"))

        compose.onNodeWithTag(HostLoginTags.MESSAGE).assertTextContains(str(R.string.host_login_offline))
        compose.onNodeWithTag(HostLoginTags.SUBMIT).assertIsEnabled()
    }

    @Test
    fun ak_5_4_an_expired_session_asks_for_a_new_login() {
        show(HostLoginUiState.Empty, sessionExpired = true)

        compose.onNodeWithTag(HostLoginTags.EXPIRED).assert(hasAnyDescendant(hasText(str(R.string.host_session_expired))))
    }

    @Test
    fun a_first_login_shows_no_expiry_hint() {
        show(HostLoginUiState.Empty)

        compose.onAllNodesWithTag(HostLoginTags.EXPIRED).assertCountEquals(0)
    }
}
