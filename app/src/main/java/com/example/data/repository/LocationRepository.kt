package com.example.data.repository

import com.example.data.dao.LocationDao
import com.example.data.model.LocationEntity
import com.example.data.model.TripEntity
import com.example.domain.algorithm.PathProcessor
import com.example.domain.model.FilterSettings
import com.example.domain.network.OSRMService
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth Repository managing location logs, active trip sessions,
 * offline queue syncing, and road snapping calculations.
 *
 * FLUTTER PORTING NOTE:
 * In Flutter, implement this as a `LocationRepository` or `Provider` / `Bloc` repository
 * referencing `sqflite` DAO.
 */
class LocationRepository(
    private val locationDao: LocationDao,
    private val osrmService: OSRMService = OSRMService()
) {

    val activeTripFlow: Flow<TripEntity?> = locationDao.getActiveTripFlow()
    val allTripsFlow: Flow<List<TripEntity>> = locationDao.getAllTrips()

    suspend fun getActiveTrip(): TripEntity? = locationDao.getActiveTripSync()

    suspend fun startNewTrip(title: String = "Tracked Route"): TripEntity {
        // End any previous active trip
        val existing = locationDao.getActiveTripSync()
        if (existing != null) {
            stopActiveTrip(existing.id)
        }

        val newTrip = TripEntity(
            title = title,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        locationDao.insertTrip(newTrip)
        return newTrip
    }

    suspend fun stopActiveTrip(tripId: String) {
        val locations = locationDao.getLocationsForTripSync(tripId)
        val stats = PathProcessor.computePathStats(locations)

        val activeTrip = locationDao.getActiveTripSync()
        if (activeTrip != null && activeTrip.id == tripId) {
            val updated = activeTrip.copy(
                endTime = System.currentTimeMillis(),
                totalDistanceMeters = stats.totalDistanceKm * 1000f,
                averageSpeedKmh = stats.averageSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                pointCount = locations.size,
                isActive = false
            )
            locationDao.updateTrip(updated)
        }
    }

    suspend fun logLocation(location: LocationEntity) {
        locationDao.insertLocation(location)

        // Update active trip stats
        val activeTrip = locationDao.getActiveTripSync()
        if (activeTrip != null && activeTrip.id == location.tripId) {
            val locations = locationDao.getLocationsForTripSync(activeTrip.id)
            val stats = PathProcessor.computePathStats(locations)
            val updated = activeTrip.copy(
                totalDistanceMeters = stats.totalDistanceKm * 1000f,
                averageSpeedKmh = stats.averageSpeedKmh,
                maxSpeedKmh = stats.maxSpeedKmh,
                pointCount = locations.size
            )
            locationDao.updateTrip(updated)
        }
    }

    fun getLocationsForTrip(tripId: String): Flow<List<LocationEntity>> {
        return locationDao.getLocationsForTrip(tripId)
    }

    fun getLocationsForDateRange(startTime: Long, endTime: Long): Flow<List<LocationEntity>> {
        return locationDao.getLocationsForDateRange(startTime, endTime)
    }

    fun getTripsForDateRange(startTime: Long, endTime: Long): Flow<List<TripEntity>> {
        return locationDao.getTripsForDateRange(startTime, endTime)
    }

    suspend fun snapTripToRoads(tripId: String): List<Pair<Double, Double>> {
        val locations = locationDao.getLocationsForTripSync(tripId)
        val filtered = PathProcessor.filterRawNoise(locations)
        return osrmService.snapToRoads(filtered)
    }

    suspend fun deleteTrip(tripId: String) {
        locationDao.deleteLocationsForTrip(tripId)
        locationDao.deleteTrip(tripId)
    }

    suspend fun clearAllData() {
        locationDao.clearAllLocations()
    }
}
