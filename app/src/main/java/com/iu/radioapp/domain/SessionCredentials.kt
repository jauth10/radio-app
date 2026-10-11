package com.iu.radioapp.domain

import kotlin.time.Instant

/** [validUntil] and [startedAt] are null for a session stored before they were kept. */
data class SessionCredentials(
    val token: String,
    val validUntil: Instant?,
    val startedAt: Instant?,
)
