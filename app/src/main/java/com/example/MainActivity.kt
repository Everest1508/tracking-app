package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.PathTrackerTheme
import com.example.ui.viewmodel.LocationTrackerViewModel
import com.example.ui.viewmodel.MapViewModel

class MainActivity : ComponentActivity() {

    private val trackerViewModel: LocationTrackerViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PathTrackerTheme {
                AppNavigation(
                    trackerViewModel = trackerViewModel,
                    mapViewModel = mapViewModel
                )
            }
        }
    }
}

