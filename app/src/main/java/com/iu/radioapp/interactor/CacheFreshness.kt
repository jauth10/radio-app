package com.iu.radioapp.interactor

import com.iu.radioapp.domain.Failure
import com.iu.radioapp.domain.ReadResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal val STALE_AFTER: Duration = 5.minutes

/** The failure behind the cache fallback, if that cache is now too old to trust. */
internal fun ReadResult<*>.staleCause(now: Instant): Failure? =
    (this as? ReadResult.Cached<*>)?.takeIf { now - it.fetchedAt > STALE_AFTER }?.cause

internal fun ReadResult<*>.isStale(now: Instant): Boolean = staleCause(now) != null
