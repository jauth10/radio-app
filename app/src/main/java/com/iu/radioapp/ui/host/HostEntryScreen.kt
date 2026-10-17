package com.iu.radioapp.ui.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.iu.radioapp.R
import com.iu.radioapp.ui.common.LoadingView
import com.iu.radioapp.ui.common.MessageView
import com.iu.radioapp.ui.common.failureText

/** One route for the role switch: login without a session, the host view with one. */
@Composable
fun HostEntryScreen(viewModel: HostFeedViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    when (val current = access) {
        HostAccess.Checking -> LoadingView()
        is HostAccess.SignedOut -> HostLoginRoute(sessionExpired = current.sessionExpired)
        HostAccess.SignedIn -> HostFeedRoute(viewModel)
        is HostAccess.Unavailable -> MessageView(
            title = failureText(current.failure),
            body = null,
            actionLabel = stringResource(R.string.action_reload),
            onAction = viewModel::reload,
        )
    }
}
