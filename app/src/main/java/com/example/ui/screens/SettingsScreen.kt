package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.TrackingMode
import com.example.ui.viewmodel.LocationTrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    trackerViewModel: LocationTrackerViewModel
) {
    val context = LocalContext.current
    val filterSettings by trackerViewModel.filterSettings.collectAsStateWithLifecycle()
    var showFlutterCodeGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery & GPS Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Battery Optimization Modes
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery Profiles", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    TrackingMode.entries.forEach { mode ->
                        val isSelected = (filterSettings.trackingMode == mode)
                        OutlinedCard(
                            onClick = { trackerViewModel.setTrackingMode(context, mode) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("profile_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = mode.label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Interval: ${mode.intervalMs / 1000}s • Min Dist: ${mode.minDistanceMeters.toInt()}m",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.FlashOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Custom Fine-Tuning Sliders
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GPS Sampling Customization", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    // Update Interval
                    Column {
                        Text(
                            text = "GPS Polling Interval: ${filterSettings.updateIntervalMs / 1000} seconds",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = (filterSettings.updateIntervalMs / 1000).toFloat(),
                            onValueChange = { seconds ->
                                val updated = filterSettings.copy(
                                    trackingMode = TrackingMode.CUSTOM,
                                    updateIntervalMs = seconds.toLong() * 1000L
                                )
                                trackerViewModel.updateSettings(context, updated)
                            },
                            valueRange = 5f..120f,
                            steps = 22
                        )
                    }

                    // Distance Filter
                    Column {
                        Text(
                            text = "Minimum Displacement Filter: ${filterSettings.minDistanceMeters.toInt()} meters",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = filterSettings.minDistanceMeters,
                            onValueChange = { dist ->
                                val updated = filterSettings.copy(
                                    trackingMode = TrackingMode.CUSTOM,
                                    minDistanceMeters = dist
                                )
                                trackerViewModel.updateSettings(context, updated)
                            },
                            valueRange = 0f..100f,
                            steps = 19
                        )
                    }
                }
            }

            // 3. Noise Filter Toggles
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Noise Filtering & Road Snapping", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    SettingToggleRow(
                        title = "2D Kalman Filter Smoothing",
                        subtitle = "Eliminates high-variance GPS jitter using state estimation",
                        checked = filterSettings.enableKalmanFilter,
                        onCheckedChange = { checked ->
                            trackerViewModel.updateSettings(context, filterSettings.copy(enableKalmanFilter = checked))
                        }
                    )

                    SettingToggleRow(
                        title = "OSRM Road Network Snapping",
                        subtitle = "Snaps coordinate points to actual street geometry",
                        checked = filterSettings.enableRoadSnapping,
                        onCheckedChange = { checked ->
                            trackerViewModel.updateSettings(context, filterSettings.copy(enableRoadSnapping = checked))
                        }
                    )

                    SettingToggleRow(
                        title = "Adaptive Stationary Pause",
                        subtitle = "Slows polling rate automatically when device is still",
                        checked = filterSettings.enableAdaptiveStationaryPause,
                        onCheckedChange = { checked ->
                            trackerViewModel.updateSettings(context, filterSettings.copy(enableAdaptiveStationaryPause = checked))
                        }
                    )
                }
            }

            // 4. System Battery Exemption Action
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unrestricted Background Activity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "To prevent Android Doze Mode from killing continuous background GPS logging, request battery optimization exemption.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Battery Optimization Settings")
                    }
                }
            }

            // 5. Flutter Porting & Cross-Platform Architecture Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFlutterCodeGuide = !showFlutterCodeGuide },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Flutter Cross-Platform Guide", fontWeight = FontWeight.Bold)
                        }
                        Text(if (showFlutterCodeGuide) "Hide" else "Show", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    if (showFlutterCodeGuide) {
                        Text(
                            text = """
                            This app was built specifically with Flutter porting in mind:
                            • Background Tracking: flutter_background_service + geolocator
                            • Interactive OSM Map: flutter_map + latlong2
                            • Road Snapping: OSRM REST Match API
                            • Offline Database: sqflite or isar
                            • Noise Filter: 1:1 KalmanFilter2D algorithm port to Dart
                            """.trimIndent(),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
