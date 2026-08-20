package com.iu.radioapp.domain

/**
 * The person using the app.
 *
 * [listenerId] is a UUID generated locally on first start and kept in DataStore;
 * there are no user accounts and no real authentication. [displayName] is
 * optional because giving a name is voluntary.
 */
data class Listener(
    val listenerId: String,
    val displayName: String?,
)
