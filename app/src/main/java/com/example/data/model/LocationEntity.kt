package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing an individual recorded GPS coordinate fix.
 * Included fields support offline logging, noise filter metadata, and road snapping.
 *
 * FLUTTER PORTING NOTE:
 * In Flutter, map this model to a SQLite database model using `sqflite` or `isar`.
 */
@Entity(tableName = "location_logs")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f, // in m/s
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isSmoothed: Boolean = false,
    val snappedLat: Double? = null,
    val snappedLng: Double? = null
)

/**
 * Entity representing a trip session recorded by the background service.
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val pointCount: Int = 0,
    val isActive: Boolean = true
)
