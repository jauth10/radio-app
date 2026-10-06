package com.iu.radioapp.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iu.radioapp.repository.RatingRepository
import com.iu.radioapp.repository.SongRequestRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val songRequests: SongRequestRepository,
    private val ratings: RatingRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val results = listOf(
            captureDelivery { songRequests.deliverOpen() },
            captureDelivery { ratings.deliverOpen() },
        )
        results.mapNotNull { it.exceptionOrNull() }.forEach { Log.e(TAG, "delivery run failed locally", it) }
        return when (nextRunAfter(results)) {
            NextRun.DONE -> Result.success()
            NextRun.RETRY -> Result.retry()
        }
    }

    private companion object {
        const val TAG = "DeliveryWorker"
    }
}
