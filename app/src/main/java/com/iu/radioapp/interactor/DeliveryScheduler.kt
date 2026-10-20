package com.iu.radioapp.interactor

/** Triggers a delivery run; the WorkManager implementation lives outside this layer. */
interface DeliveryScheduler {
    fun schedule()

    // For a manual retry: starts at once instead of waiting behind a run in backoff.
    fun scheduleImmediately()
}
