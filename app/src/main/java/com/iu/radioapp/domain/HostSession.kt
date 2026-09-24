package com.iu.radioapp.domain

import kotlin.time.Instant

/** The session token itself stays in the data layer. */
data class HostSession(
    val host: Host,
    val validUntil: Instant,
)
