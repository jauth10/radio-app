package com.iu.radioapp.ui.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iu.radioapp.R
import com.iu.radioapp.ui.common.MessageView

/** Entry point of the role switch; RAD-16 replaces this content with the host login. */
@Composable
fun HostEntryScreen() {
    MessageView(title = stringResource(R.string.title_host_entry), body = stringResource(R.string.host_entry_body))
}
