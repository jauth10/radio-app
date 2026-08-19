package com.iu.radioapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and root of the Hilt dependency graph.
 * Registered as android:name in AndroidManifest.xml.
 */
@HiltAndroidApp
class RadioApplication : Application()
