package com.iu.radioapp.interactor

/** Triggers a delivery run; the WorkManager implementation lives outside this layer. */
fun interface DeliveryScheduler {
    fun schedule()
}
