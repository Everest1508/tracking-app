package com.example.domain.model

enum class TrackingMode(val label: String, val intervalMs: Long, val minDistanceMeters: Float, val minAccuracyMeters: Float) {
    HIGH_ACCURACY("High Accuracy (5s / 5m)", 5000L, 5f, 25f),
    BALANCED("Balanced (15s / 15m)", 15000L, 15f, 35f),
    BATTERY_SAVER("Battery Saver (60s / 50m)", 60000L, 50f, 50f),
    CUSTOM("Custom Settings", 15000L, 15f, 30f)
}

data class FilterSettings(
    val trackingMode: TrackingMode = TrackingMode.BALANCED,
    val updateIntervalMs: Long = 15000L,
    val minDistanceMeters: Float = 15f,
    val minAccuracyMeters: Float = 35f,
    val enableKalmanFilter: Boolean = true,
    val enableRoadSnapping: Boolean = true,
    val enableAdaptiveStationaryPause: Boolean = true
)
