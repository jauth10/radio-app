package com.iu.radioapp.work

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

// Two runs reading the same open entries would send them twice and book from a stale count.
@Singleton
class DeliveryRunLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
}
