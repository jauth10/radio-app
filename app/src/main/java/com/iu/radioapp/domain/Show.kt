@file:OptIn(ExperimentalTime::class)

package com.iu.radioapp.domain

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A broadcast slot, optionally presented by a [Host].
 *
 * The host relationship is 0..1 because unhosted shows are the normal case, not
 * an error. [name], [startsAt] and [endsAt] are nullable because no endpoint of
 * the agreed contract returns them yet - only the show id reaches the client.
 */
data class Show(
    val showId: String,
    val name: String?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val host: Host?,
)
