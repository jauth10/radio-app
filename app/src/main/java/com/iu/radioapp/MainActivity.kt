package com.iu.radioapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.iu.radioapp.ui.host.LatencyRecorder
import com.iu.radioapp.ui.host.LocalLatencyRecorder
import com.iu.radioapp.ui.navigation.RadioApp
import com.iu.radioapp.ui.theme.RadioAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var latencyRecorder: LatencyRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLatencyRecorder provides latencyRecorder) {
                RadioAppTheme {
                    RadioApp()
                }
            }
        }
    }
}
