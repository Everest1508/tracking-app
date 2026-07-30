package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LocationEntity
import com.example.data.model.TripEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for location logs and trips.
 * Supports reactive Flow queries for daily & date range filtering.
 */
@Dao
interface LocationDao {

    // --- Locations Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<LocationEntity>)

    @Query("SELECT * FROM location_logs WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getLocationsForTrip(tripId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location_logs WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getLocationsForTripSync(tripId: String): List<LocationEntity>

    @Query("SELECT * FROM location_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getLocationsForDateRange(startTime: Long, endTime: Long): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getLocationsForDateRangeSync(startTime: Long, endTime: Long): List<LocationEntity>

    @Query("SELECT * FROM location_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLocations(): List<LocationEntity>

    @Query("UPDATE location_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markLocationsSynced(ids: List<Long>)

    @Query("UPDATE location_logs SET snappedLat = :snappedLat, snappedLng = :snappedLng, isSmoothed = 1 WHERE id = :id")
    suspend fun updateSnappedCoordinates(id: Long, snappedLat: Double, snappedLng: Double)

    @Query("DELETE FROM location_logs WHERE tripId = :tripId")
    suspend fun deleteLocationsForTrip(tripId: String)

    @Query("DELETE FROM location_logs")
    suspend fun clearAllLocations()

    // --- Trips Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTripSync(): TripEntity?

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    fun getActiveTripFlow(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getTripsForDateRange(startTime: Long, endTime: Long): Flow<List<TripEntity>>

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: String)
}
