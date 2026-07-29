package com.timeflux.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.timeflux.android.ui.TimeFluxNavHost
import com.timeflux.android.ui.theme.TimeFluxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeFluxTheme {
                TimeFluxNavHost()
            }
        }
    }
}
