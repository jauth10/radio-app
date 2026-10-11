package com.iu.radioapp.domain

import kotlin.time.Instant

/** [validUntil] is null for a session stored before its expiry was kept. */
data class SessionCredentials(
    val token: String,
    val validUntil: Instant?,
)
