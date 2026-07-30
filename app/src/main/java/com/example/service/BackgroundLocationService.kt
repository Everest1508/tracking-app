package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.LocationEntity
import com.example.data.repository.LocationRepository
import com.example.domain.algorithm.KalmanFilter2D
import com.example.domain.algorithm.PathProcessor
import com.example.domain.model.FilterSettings
import com.example.domain.model.TrackingMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service for persistent background GPS tracking.
 * Runs independently of UI lifecycle, logging coordinates directly to Room database.
 * Supports offline mode and battery optimization settings.
 *
 * FLUTTER PORTING NOTE:
 * In Flutter, use `flutter_background_service` combined with `geolocator`
 * or `background_location` package to achieve identical background execution on Android & iOS.
 */
class BackgroundLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository
    private var kalmanFilter = KalmanFilter2D()

    private var currentTripId: String = ""
    private var totalPointsLogged = 0
    private var totalDistanceTraveledMeters = 0f
    private var lastLocation: Location? = null

    private var currentIntervalMs = 15000L
    private var currentMinDistanceMeters = 15f
    private var currentMinAccuracyMeters = 35f

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            for (location in result.locations) {
                processAndLogLocation(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val db = AppDatabase.getDatabase(applicationContext)
        locationRepository = LocationRepository(db.locationDao())
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING

        when (action) {
            ACTION_START_TRACKING -> {
                val tripTitle = intent?.getStringExtra(EXTRA_TRIP_TITLE) ?: "Background Route"
                currentIntervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, 15000L) ?: 15000L
                currentMinDistanceMeters = intent?.getFloatExtra(EXTRA_MIN_DIST, 15f) ?: 15f
                currentMinAccuracyMeters = intent?.getFloatExtra(EXTRA_MIN_ACCURACY, 35f) ?: 35f

                startForegroundServiceWithNotification()
                startTrackingSession(tripTitle)
            }
            ACTION_STOP_TRACKING -> {
                stopTrackingSession()
            }
            ACTION_UPDATE_SETTINGS -> {
                currentIntervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, currentIntervalMs) ?: currentIntervalMs
                currentMinDistanceMeters = intent?.getFloatExtra(EXTRA_MIN_DIST, currentMinDistanceMeters) ?: currentMinDistanceMeters
                currentMinAccuracyMeters = intent?.getFloatExtra(EXTRA_MIN_ACCURACY, currentMinAccuracyMeters) ?: currentMinAccuracyMeters
                requestLocationUpdates()
            }
        }

        return START_STICKY
    }

    private fun startTrackingSession(title: String) {
        serviceScope.launch {
            val activeTrip = locationRepository.startNewTrip(title)
            currentTripId = activeTrip.id
            totalPointsLogged = 0
            totalDistanceTraveledMeters = 0f
            lastLocation = null
            kalmanFilter.reset()

            requestLocationUpdates()
            updateNotificationText("Recording route...", "0 points logged • 0.00 km")
        }
    }

    private fun stopTrackingSession() {
        serviceScope.launch {
            if (currentTripId.isNotEmpty()) {
                locationRepository.stopActiveTrip(currentTripId)
            }
            fusedLocationClient.removeLocationUpdates(locationCallback)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)

            val priority = if (currentIntervalMs <= 5000L) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            val locationRequest = LocationRequest.Builder(priority, currentIntervalMs)
                .setMinUpdateDistanceMeters(currentMinDistanceMeters)
                .setMinUpdateIntervalMillis(currentIntervalMs / 2)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested with interval=$currentIntervalMs ms, minDistance=$currentMinDistanceMeters m")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permissions for service: ${e.localizedMessage}")
        }
    }

    private fun processAndLogLocation(rawLocation: Location) {
        // Accuracy filter
        if (rawLocation.hasAccuracy() && rawLocation.accuracy > currentMinAccuracyMeters) {
            Log.d(TAG, "Dropped fix due to poor accuracy (${rawLocation.accuracy}m > ${currentMinAccuracyMeters}m)")
            return
        }

        // Distance & speed filter
        lastLocation?.let { last ->
            val dist = rawLocation.distanceTo(last)
            if (dist < currentMinDistanceMeters && totalPointsLogged > 0) {
                return // Stationary point skip for battery optimization
            }

            val timeDeltaSec = (rawLocation.time - last.time) / 1000.0
            if (timeDeltaSec > 0) {
                val speedMs = dist / timeDeltaSec
                if (speedMs > 50) { // > 180 km/h impossible GPS jump
                    Log.d(TAG, "Dropped GPS teleportation fix: $speedMs m/s")
                    return
                }
            }
            totalDistanceTraveledMeters += dist
        }
        lastLocation = rawLocation

        // Apply Kalman smoothing
        val (smoothedLat, smoothedLng) = kalmanFilter.process(
            rawLocation.latitude,
            rawLocation.longitude,
            rawLocation.accuracy.coerceAtLeast(5f),
            rawLocation.time
        )

        totalPointsLogged++

        val locationEntity = LocationEntity(
            tripId = currentTripId,
            latitude = rawLocation.latitude,
            longitude = rawLocation.longitude,
            altitude = rawLocation.altitude,
            accuracy = rawLocation.accuracy,
            speed = rawLocation.speed,
            bearing = rawLocation.bearing,
            timestamp = rawLocation.time,
            isSynced = false,
            isSmoothed = true,
            snappedLat = smoothedLat,
            snappedLng = smoothedLng
        )

        serviceScope.launch {
            locationRepository.logLocation(locationEntity)
            val distKm = totalDistanceTraveledMeters / 1000f
            val formattedDist = String.format(java.util.Locale.US, "%.2f km", distKm)
            updateNotificationText(
                "Active Path Tracking",
                "$totalPointsLogged points logged • $formattedDist"
            )
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildNotification("PathTracker Active", "Initializing background GPS tracker...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background GPS Tracking Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used to display ongoing background location tracking notifications"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, contentText: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BackgroundLocationService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Tracking", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotificationText(title: String, contentText: String) {
        val notification = buildNotification(title, contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "BgLocationService"
        const val CHANNEL_ID = "pathtracker_background_channel"
        const val NOTIFICATION_ID = 8801

        const val ACTION_START_TRACKING = "com.example.pathtracker.START"
        const val ACTION_STOP_TRACKING = "com.example.pathtracker.STOP"
        const val ACTION_UPDATE_SETTINGS = "com.example.pathtracker.UPDATE_SETTINGS"

        const val EXTRA_TRIP_TITLE = "extra_trip_title"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        const val EXTRA_MIN_DIST = "extra_min_dist"
        const val EXTRA_MIN_ACCURACY = "extra_min_accuracy"

        fun startService(context: Context, title: String = "Tracked Path", settings: FilterSettings) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_TRIP_TITLE, title)
                putExtra(EXTRA_INTERVAL_MS, settings.updateIntervalMs)
                putExtra(EXTRA_MIN_DIST, settings.minDistanceMeters)
                putExtra(EXTRA_MIN_ACCURACY, settings.minAccuracyMeters)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundLocationService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            context.startService(intent)
        }
    }
}
