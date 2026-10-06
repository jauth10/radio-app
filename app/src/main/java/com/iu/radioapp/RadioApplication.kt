package com.iu.radioapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.iu.radioapp.work.WorkManagerDeliveryScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point and root of the Hilt dependency graph.
 * Registered as android:name in AndroidManifest.xml.
 */
@HiltAndroidApp
class RadioApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var deliveryScheduler: WorkManagerDeliveryScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Picks up entries whose scheduling was lost, e.g. when the process died right after enqueueing.
        deliveryScheduler.ensureScheduled()
    }
}
