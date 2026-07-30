package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.LocationEntity
import com.example.data.model.TripEntity
import com.example.data.repository.LocationRepository
import com.example.domain.algorithm.PathProcessor
import com.example.domain.algorithm.PathStats
import com.example.domain.export.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class DateRangePreset(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    CUSTOM("Custom Range")
}

data class DateRange(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val preset: DateRangePreset = DateRangePreset.TODAY
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository = LocationRepository(
        AppDatabase.getDatabase(application).locationDao()
    )

    private val _dateRange = MutableStateFlow(getTodayRange())
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val locationsForRange: StateFlow<List<LocationEntity>> = _dateRange
        .flatMapLatest { range ->
            repository.getLocationsForDateRange(range.startTimeMs, range.endTimeMs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val tripsForRange: StateFlow<List<TripEntity>> = _dateRange
        .flatMapLatest { range ->
            repository.getTripsForDateRange(range.startTimeMs, range.endTimeMs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _snappedPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val snappedPoints: StateFlow<List<Pair<Double, Double>>> = _snappedPoints.asStateFlow()

    private val _isSnappingLoading = MutableStateFlow(false)
    val isSnappingLoading: StateFlow<Boolean> = _isSnappingLoading.asStateFlow()

    private val _showRawPath = MutableStateFlow(true)
    val showRawPath: StateFlow<Boolean> = _showRawPath.asStateFlow()

    private val _showSnappedPath = MutableStateFlow(true)
    val showSnappedPath: StateFlow<Boolean> = _showSnappedPath.asStateFlow()

    private val _pathStats = MutableStateFlow(PathStats())
    val pathStats: StateFlow<PathStats> = _pathStats.asStateFlow()

    init {
        viewModelScope.launch {
            locationsForRange.collect { locs ->
                _pathStats.value = PathProcessor.computePathStats(locs)
                triggerRoadSnapping(locs)
            }
        }
    }

    fun setDatePreset(preset: DateRangePreset) {
        val range = when (preset) {
            DateRangePreset.TODAY -> getTodayRange()
            DateRangePreset.YESTERDAY -> getYesterdayRange()
            DateRangePreset.LAST_7_DAYS -> getLast7DaysRange()
            DateRangePreset.CUSTOM -> _dateRange.value
        }
        _dateRange.value = range
    }

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        _dateRange.value = DateRange(startMs, endMs, DateRangePreset.CUSTOM)
    }

    fun toggleShowRawPath() {
        _showRawPath.value = !_showRawPath.value
    }

    fun toggleShowSnappedPath() {
        _showSnappedPath.value = !_showSnappedPath.value
    }

    private fun triggerRoadSnapping(locations: List<LocationEntity>) {
        if (locations.isEmpty()) {
            _snappedPoints.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSnappingLoading.value = true
            val filtered = PathProcessor.filterRawNoise(locations)
            val snapped = withContext(Dispatchers.IO) {
                repository.snapTripToRoads(filtered.firstOrNull()?.tripId ?: "")
            }
            _snappedPoints.value = snapped
            _isSnappingLoading.value = false
        }
    }

    fun exportCurrentTripGpx(trip: TripEntity, locations: List<LocationEntity>): String {
        return ExportManager.exportToGpx(trip, locations)
    }

    fun exportCurrentTripGeoJson(trip: TripEntity, locations: List<LocationEntity>): String {
        return ExportManager.exportToGeoJson(trip, locations)
    }

    companion object {
        fun getTodayRange(): DateRange {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis
            val endMs = System.currentTimeMillis() + 86400000L
            return DateRange(startMs, endMs, DateRangePreset.TODAY)
        }

        fun getYesterdayRange(): DateRange {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis
            val endMs = startMs + 86399999L
            return DateRange(startMs, endMs, DateRangePreset.YESTERDAY)
        }

        fun getLast7DaysRange(): DateRange {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis
            val endMs = System.currentTimeMillis() + 86400000L
            return DateRange(startMs, endMs, DateRangePreset.LAST_7_DAYS)
        }
    }
}
