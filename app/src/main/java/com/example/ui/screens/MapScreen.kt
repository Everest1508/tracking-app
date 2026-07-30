package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LeafletMapView
import com.example.ui.viewmodel.DateRangePreset
import com.example.ui.viewmodel.LocationTrackerViewModel
import com.example.ui.viewmodel.MapViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    trackerViewModel: LocationTrackerViewModel,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current

    // Location permissions state
    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    val isTracking by trackerViewModel.isTracking.collectAsStateWithLifecycle()
    val activeTrip by trackerViewModel.activeTrip.collectAsStateWithLifecycle()
    val dateRange by mapViewModel.dateRange.collectAsStateWithLifecycle()
    val rawLocations by mapViewModel.locationsForRange.collectAsStateWithLifecycle()
    val snappedPoints by mapViewModel.snappedPoints.collectAsStateWithLifecycle()
    val isSnappingLoading by mapViewModel.isSnappingLoading.collectAsStateWithLifecycle()
    val showRawPath by mapViewModel.showRawPath.collectAsStateWithLifecycle()
    val showSnappedPath by mapViewModel.showSnappedPath.collectAsStateWithLifecycle()
    val stats by mapViewModel.pathStats.collectAsStateWithLifecycle()

    var showLayerMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Interactive Leaflet OpenStreetMap View
        LeafletMapView(
            rawLocations = rawLocations,
            snappedPoints = snappedPoints,
            showRawPath = showRawPath,
            showSnappedPath = showSnappedPath,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Bar - Date Filter Presets & Stats Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date Filter Pills
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DateRangePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = (dateRange.preset == preset),
                            onClick = { mapViewModel.setDatePreset(preset) },
                            label = { Text(preset.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            // Stats Quick Summary Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        label = "Distance",
                        value = String.format(Locale.US, "%.2f km", stats.totalDistanceKm)
                    )
                    StatItem(
                        label = "Avg Speed",
                        value = String.format(Locale.US, "%.1f km/h", stats.averageSpeedKmh)
                    )
                    StatItem(
                        label = "Points Logged",
                        value = "${stats.pointCount}"
                    )
                }
            }
        }

        // 3. Layer Toggle Floating Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Column {
                IconButton(
                    onClick = { showLayerMenu = !showLayerMenu },
                    modifier = Modifier.testTag("layer_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Toggle Map Layers",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Layer Controls Dropdown Card
        AnimatedVisibility(
            visible = showLayerMenu,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 72.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Map Layer Controls",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    FilterChip(
                        selected = showSnappedPath,
                        onClick = { mapViewModel.toggleShowSnappedPath() },
                        label = { Text("Snapped Road Path (Emerald)") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                        }
                    )
                    FilterChip(
                        selected = showRawPath,
                        onClick = { mapViewModel.toggleShowRawPath() },
                        label = { Text("Raw GPS Path (Dashed Coral)") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFF43F5E), CircleShape)
                            )
                        }
                    )
                }
            }
        }

        // 4. Bottom Controls / Tracking Action Button
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!permissionState.allPermissionsGranted) {
                    // Permission Banner
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Location & Notification Permissions Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            Button(
                                onClick = { permissionState.launchMultiplePermissionRequest() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isTracking) Color(0xFF10B981) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTracking) "Tracking Active" else "Tracking Paused",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (isSnappingLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Snapping road network...", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            if (!permissionState.allPermissionsGranted) {
                                permissionState.launchMultiplePermissionRequest()
                            } else {
                                if (isTracking) {
                                    trackerViewModel.stopTracking(context)
                                } else {
                                    trackerViewModel.startTracking(context, "Background Trip")
                                }
                            }
                        },
                        containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("start_stop_tracking_fab")
                    ) {
                        Icon(
                            imageVector = if (isTracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTracking) "Stop Tracking" else "Start Tracking"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}
