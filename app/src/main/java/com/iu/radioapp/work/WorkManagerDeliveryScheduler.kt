package com.iu.radioapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.iu.radioapp.interactor.DeliveryScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerDeliveryScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeliveryScheduler {

    // Appended, not kept: a run that already read the outbox would miss the new entry.
    override fun schedule() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    // Kept, not appended: every cold start by WorkManager itself would otherwise add another run.
    fun ensureScheduled() {
        enqueue(ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, deliveryRequest())
    }

    companion object {
        const val UNIQUE_WORK_NAME = "outbox-delivery"
        const val BACKOFF_SECONDS = 30L

        fun deliveryRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DeliveryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}
