package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.TripEntity
import com.example.data.repository.LocationRepository
import com.example.domain.model.FilterSettings
import com.example.domain.model.TrackingMode
import com.example.service.BackgroundLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository = LocationRepository(
        AppDatabase.getDatabase(application).locationDao()
    )

    val activeTrip: StateFlow<TripEntity?> = repository.activeTripFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _filterSettings = MutableStateFlow(FilterSettings())
    val filterSettings: StateFlow<FilterSettings> = _filterSettings.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    init {
        viewModelScope.launch {
            repository.activeTripFlow.collect { trip ->
                _isTracking.value = (trip?.isActive == true)
            }
        }
    }

    fun startTracking(context: Context, tripTitle: String = "Tracked Route") {
        BackgroundLocationService.startService(
            context,
            title = tripTitle,
            settings = _filterSettings.value
        )
    }

    fun stopTracking(context: Context) {
        BackgroundLocationService.stopService(context)
    }

    fun updateSettings(context: Context, newSettings: FilterSettings) {
        _filterSettings.value = newSettings
        if (_isTracking.value) {
            val intent = android.content.Intent(context, BackgroundLocationService::class.java).apply {
                action = BackgroundLocationService.ACTION_UPDATE_SETTINGS
                putExtra(BackgroundLocationService.EXTRA_INTERVAL_MS, newSettings.updateIntervalMs)
                putExtra(BackgroundLocationService.EXTRA_MIN_DIST, newSettings.minDistanceMeters)
                putExtra(BackgroundLocationService.EXTRA_MIN_ACCURACY, newSettings.minAccuracyMeters)
            }
            context.startService(intent)
        }
    }

    fun setTrackingMode(context: Context, mode: TrackingMode) {
        val updated = _filterSettings.value.copy(
            trackingMode = mode,
            updateIntervalMs = mode.intervalMs,
            minDistanceMeters = mode.minDistanceMeters,
            minAccuracyMeters = mode.minAccuracyMeters
        )
        updateSettings(context, updated)
    }
}
