package com.iu.radioapp.domain

import kotlin.time.Instant

/** A read together with its source and fetch time; judging staleness is left to the interactor. */
sealed interface ReadResult<out T> {
    val value: T
    val fetchedAt: Instant

    data class Live<T>(
        override val value: T,
        override val fetchedAt: Instant,
    ) : ReadResult<T>

    data class Cached<T>(
        override val value: T,
        override val fetchedAt: Instant,
        val cause: Failure,
    ) : ReadResult<T>
}
