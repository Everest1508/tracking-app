package com.example.domain.algorithm

import kotlin.math.cos

/**
 * 2D Kalman Filter implementation for smoothing GPS coordinates.
 * Filters out high-variance GPS jitter and erratic coordinate jumps.
 *
 * FLUTTER PORTING NOTE:
 * This algorithm is pure Dart/Kotlin math with zero Android dependencies.
 * Direct 1:1 conversion to Dart class for Flutter path smoothing.
 */
class KalmanFilter2D(private val processNoiseQ: Float = 3f) {

    private var timestampMs: Long = 0L
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var variance: Float = -1f // -1 means uninitialized

    fun reset() {
        variance = -1f
        timestampMs = 0L
    }

    /**
     * Process a new GPS measurement and return the smoothed Lat/Lng pair.
     */
    fun process(
        newLat: Double,
        newLng: Double,
        newAccuracy: Float,
        newTimeMs: Long
    ): Pair<Double, Double> {
        if (variance < 0) {
            // First fix - initialize
            timestampMs = newTimeMs
            latitude = newLat
            longitude = newLng
            variance = newAccuracy * newAccuracy
            return Pair(latitude, longitude)
        }

        val deltaTimeSec = ((newTimeMs - timestampMs) / 1000.0).coerceAtLeast(0.1)
        timestampMs = newTimeMs

        // Estimate process variance based on elapsed time and process noise Q
        variance += (deltaTimeSec * processNoiseQ * processNoiseQ).toFloat()

        // Calculate Kalman Gain K = Variance / (Variance + MeasurementNoiseR)
        val measurementNoiseR = newAccuracy * newAccuracy
        val kalmanGain = variance / (variance + measurementNoiseR)

        // Update state estimate
        latitude += kalmanGain * (newLat - latitude)
        longitude += kalmanGain * (newLng - longitude)

        // Update covariance/variance estimate
        variance *= (1.0f - kalmanGain)

        return Pair(latitude, longitude)
    }
}
