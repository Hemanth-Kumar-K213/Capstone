package com.womensafety.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.womensafety.system.ui.navigation.AppNavGraph
import com.womensafety.system.ui.theme.WomenSafetyTheme

/**
 * Single Activity architecture.
 * All navigation is handled via Compose Navigation within AppNavGraph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WomenSafetyTheme {
                AppNavGraph()
            }
        }
    }
}
