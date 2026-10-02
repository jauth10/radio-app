package com.iu.radioapp.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkManagerDeliverySchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerDeliveryScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerDeliveryScheduler(context)
    }

    private fun deliveryWork(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WorkManagerDeliveryScheduler.UNIQUE_WORK_NAME).get()

    @Test
    fun scheduleEnqueuesUniqueWorkThatWaitsForTheNetwork() {
        scheduler.schedule()

        val work = deliveryWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun scheduleWhileWorkIsPendingAppendsASecondRun() {
        scheduler.schedule()
        scheduler.schedule()

        val states = deliveryWork().map { it.state }.sorted()
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED), states)
    }

    @Test
    fun ensureScheduledEnqueuesWhenNothingIsPending() {
        scheduler.ensureScheduled()

        assertEquals(WorkInfo.State.ENQUEUED, deliveryWork().single().state)
    }

    @Test
    fun ensureScheduledKeepsPendingWork() {
        scheduler.schedule()
        scheduler.ensureScheduled()

        assertEquals(1, deliveryWork().size)
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun deliveryBacksOffExponentiallyFromThirtySeconds() {
        val spec = WorkManagerDeliveryScheduler.deliveryRequest().workSpec

        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(30_000L, spec.backoffDelayDuration)
    }
}
