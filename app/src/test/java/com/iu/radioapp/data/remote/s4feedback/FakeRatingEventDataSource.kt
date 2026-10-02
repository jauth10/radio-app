package com.iu.radioapp.data.remote.s4feedback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory [RatingEventDataSource] fake for RAD-16's switch-to-polling logic:
 * push events with [emit] and the next [events] collector receives them in
 * order. [lastRequestedToken] records which token the repository last asked
 * for, e.g. to check that Unauthorized triggers a fresh token rather than a
 * retry with the same one.
 */
class FakeRatingEventDataSource : RatingEventDataSource {

    private val stream = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = Int.MAX_VALUE)

    var lastRequestedToken: String? = null
        private set

    override fun events(token: String): Flow<ConnectionEvent> {
        lastRequestedToken = token
        return stream
    }

    fun emit(event: ConnectionEvent) {
        check(stream.tryEmit(event)) { "FakeRatingEventDataSource buffer exhausted" }
    }
}
