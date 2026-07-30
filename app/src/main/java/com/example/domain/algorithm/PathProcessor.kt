package com.example.domain.algorithm

import com.example.data.model.LocationEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Path processing utility for cleaning, filtering noise, and calculating statistics
 * on recorded location logs.
 *
 * FLUTTER PORTING NOTE:
 * Haversine and Douglas-Peucker math can be directly reused in Dart/Flutter
 * with package `latlong2`.
 */
object PathProcessor {

    /**
     * Calculate distance between two lat/lng coordinates in meters using Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Filters out noisy locations based on:
     * 1. Accuracy cutoff (drop points where accuracy > minAccuracy)
     * 2. Speed anomaly cutoff (drop points with impossible teleportation speeds > 180 km/h)
     */
    fun filterRawNoise(
        locations: List<LocationEntity>,
        maxAccuracyMeters: Float = 40f,
        maxSpeedKmh: Float = 180f
    ): List<LocationEntity> {
        if (locations.isEmpty()) return emptyList()

        val filtered = mutableListOf<LocationEntity>()
        val maxSpeedMs = maxSpeedKmh / 3.6f

        for (loc in locations) {
            // Check accuracy
            if (loc.accuracy > maxAccuracyMeters && loc.accuracy > 0f) continue

            // Check speed jump against previous accepted location
            if (filtered.isNotEmpty()) {
                val prev = filtered.last()
                val distMeters = calculateDistanceMeters(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
                val timeDeltaSec = (loc.timestamp - prev.timestamp) / 1000.0
                if (timeDeltaSec > 0) {
                    val calculatedSpeedMs = distMeters / timeDeltaSec
                    if (calculatedSpeedMs > maxSpeedMs) {
                        // Skip impossible GPS jump
                        continue
                    }
                }
            }

            filtered.add(loc)
        }

        return filtered
    }

    /**
     * Smooths location list using 2D Kalman Filter.
     */
    fun applyKalmanSmoothing(locations: List<LocationEntity>): List<LocationEntity> {
        if (locations.size < 2) return locations

        val kalman = KalmanFilter2D()
        return locations.map { loc ->
            val (smoothedLat, smoothedLng) = kalman.process(
                loc.latitude,
                loc.longitude,
                loc.accuracy.coerceAtLeast(5f),
                loc.timestamp
            )
            loc.copy(
                snappedLat = smoothedLat,
                snappedLng = smoothedLng,
                isSmoothed = true
            )
        }
    }

    /**
     * Ramer-Douglas-Peucker algorithm to simplify path geometry and remove noise.
     */
    fun simplifyPath(
        points: List<Pair<Double, Double>>,
        epsilonMeters: Double = 3.0
    ): List<Pair<Double, Double>> {
        if (points.size <= 2) return points

        var dmax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistanceMeters(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilonMeters) {
            val recResults1 = simplifyPath(points.subList(0, index + 1), epsilonMeters)
            val recResults2 = simplifyPath(points.subList(index, points.size), epsilonMeters)
            recResults1.dropLast(1) + recResults2
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistanceMeters(
        point: Pair<Double, Double>,
        lineStart: Pair<Double, Double>,
        lineEnd: Pair<Double, Double>
    ): Double {
        val distToStart = calculateDistanceMeters(point.first, point.second, lineStart.first, lineStart.second)
        val distToEnd = calculateDistanceMeters(point.first, point.second, lineEnd.first, lineEnd.second)
        val lineLen = calculateDistanceMeters(lineStart.first, lineStart.second, lineEnd.first, lineEnd.second)

        if (lineLen < 0.1) return distToStart

        // Projection
        val d = (distToStart + distToEnd + lineLen) / 2.0
        val area = sqrt((d * (d - distToStart) * (d - distToEnd) * (d - lineLen)).coerceAtLeast(0.0))
        return (2.0 * area) / lineLen
    }

    /**
     * Compute summary statistics for a given list of location points.
     */
    fun computePathStats(locations: List<LocationEntity>): PathStats {
        if (locations.isEmpty()) return PathStats()

        var totalDistMeters = 0.0
        var maxSpeedMs = 0f
        var totalSpeedMs = 0.0

        for (i in 0 until locations.size - 1) {
            val p1 = locations[i]
            val p2 = locations[i + 1]
            totalDistMeters += calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            if (p2.speed > maxSpeedMs) {
                maxSpeedMs = p2.speed
            }
            totalSpeedMs += p2.speed
        }

        val startTime = locations.first().timestamp
        val endTime = locations.last().timestamp
        val durationSec = (endTime - startTime) / 1000.0
        val avgSpeedKmh = if (durationSec > 0) (totalDistMeters / durationSec * 3.6).toFloat() else 0f

        return PathStats(
            totalDistanceKm = (totalDistMeters / 1000.0).toFloat(),
            durationSeconds = durationSec.toLong(),
            averageSpeedKmh = avgSpeedKmh,
            maxSpeedKmh = (maxSpeedMs * 3.6f),
            pointCount = locations.size
        )
    }
}

data class PathStats(
    val totalDistanceKm: Float = 0f,
    val durationSeconds: Long = 0L,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val pointCount: Int = 0
)
