package com.iu.radioapp.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iu.radioapp.R
import com.iu.radioapp.domain.Failure
import com.iu.radioapp.ui.common.NoticeBanner
import com.iu.radioapp.ui.common.failureText

object HostLoginTags {
    const val CODE = "host_login_code"
    const val SUBMIT = "host_login_submit"
    const val EXPIRED = "host_login_expired"
    const val MESSAGE = "host_login_message"
}

@Composable
fun HostLoginRoute(sessionExpired: Boolean, viewModel: HostLoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HostLoginScreen(
        state = state,
        sessionExpired = sessionExpired,
        onCodeChange = viewModel::onCodeChange,
        onLogin = viewModel::login,
    )
}

@Composable
fun HostLoginScreen(
    state: HostLoginUiState,
    sessionExpired: Boolean,
    onCodeChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val code = when (state) {
        HostLoginUiState.Empty -> ""
        is HostLoginUiState.Content -> state.code
        is HostLoginUiState.Loading -> state.code
        is HostLoginUiState.Offline -> state.code
        is HostLoginUiState.Error -> state.code
    }
    val loading = state is HostLoginUiState.Loading
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sessionExpired) {
            NoticeBanner(
                stringResource(R.string.host_session_expired),
                modifier = Modifier.testTag(HostLoginTags.EXPIRED),
            )
        }
        Text(stringResource(R.string.host_login_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.host_login_intro))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            enabled = !loading,
            singleLine = true,
            label = { Text(stringResource(R.string.host_code_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLogin() }),
            modifier = Modifier.fillMaxWidth().testTag(HostLoginTags.CODE),
        )
        when (state) {
            is HostLoginUiState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.host_login_running), modifier = Modifier.testTag(HostLoginTags.MESSAGE))
            }
            is HostLoginUiState.Offline -> Text(
                stringResource(R.string.host_login_offline),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(HostLoginTags.MESSAGE),
            )
            is HostLoginUiState.Error -> Text(
                loginFailureText(state.failure),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(HostLoginTags.MESSAGE),
            )
            HostLoginUiState.Empty, is HostLoginUiState.Content -> Unit
        }
        Button(
            onClick = onLogin,
            enabled = state !is HostLoginUiState.Empty && !loading,
            modifier = Modifier.testTag(HostLoginTags.SUBMIT),
        ) { Text(stringResource(R.string.action_login)) }
    }
}

// The contract answers an invalid code with 401 and the lockout after too many attempts with 429.
@Composable
private fun loginFailureText(failure: Failure): String = when (failure) {
    Failure.Unauthorized -> stringResource(R.string.host_login_invalid)
    is Failure.Rejected -> stringResource(R.string.host_login_rejected, failure.reason)
    else -> failureText(failure)
}
