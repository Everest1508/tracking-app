package com.example.domain.network

import android.util.Log
import com.example.data.model.LocationEntity
import com.example.domain.algorithm.KalmanFilter2D
import com.example.domain.algorithm.PathProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Open Source Routing Machine (OSRM) Road Snapping Service.
 * Uses public OSRM map-matching endpoints to snap noisy GPS coordinates
 * to the nearest real road network.
 *
 * FLUTTER PORTING NOTE:
 * In Flutter, call OSRM Match API using `http` package:
 * `https://router.project-osrm.org/match/v1/driving/{coordinates}?overview=full&geometries=geojson`
 */
class OSRMService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Snaps a list of raw GPS points to nearest road network using OSRM Match API.
     * Fallback to local Kalman filter smoothing if offline or API unavailable.
     */
    suspend fun snapToRoads(locations: List<LocationEntity>): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        if (locations.isEmpty()) return@withContext emptyList()
        if (locations.size < 2) return@withContext locations.map { Pair(it.latitude, it.longitude) }

        // Sample points if too dense (OSRM max url length)
        val sampledLocations = if (locations.size > 80) {
            val step = locations.size / 75
            locations.filterIndexed { index, _ -> index % step == 0 || index == locations.lastIndex }
        } else {
            locations
        }

        val coordString = sampledLocations.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/match/v1/driving/$coordString?overview=full&geometries=geojson"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PathTracker-AndroidApp")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val code = json.optString("code")
                        if (code == "Ok") {
                            val matchings = json.getJSONArray("matchings")
                            if (matchings.length() > 0) {
                                val geometry = matchings.getJSONObject(0).getJSONObject("geometry")
                                val coordinates = geometry.getJSONArray("coordinates")
                                val snappedPoints = mutableListOf<Pair<Double, Double>>()

                                for (i in 0 until coordinates.length()) {
                                    val pointArr = coordinates.getJSONArray(i)
                                    val lng = pointArr.getDouble(0)
                                    val lat = pointArr.getDouble(1)
                                    snappedPoints.add(Pair(lat, lng))
                                }

                                Log.d("OSRMService", "Successfully snapped ${locations.size} points to ${snappedPoints.size} road nodes")
                                return@withContext snappedPoints
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OSRMService", "OSRM Road snapping offline or failed: ${e.localizedMessage}")
        }

        // --- OFFLINE / FALLBACK LOCAL SNAPPING ---
        Log.d("OSRMService", "Using local Kalman & Douglas-Peucker path smoothing fallback")
        val smoothed = PathProcessor.applyKalmanSmoothing(sampledLocations)
        return@withContext smoothed.map { Pair(it.snappedLat ?: it.latitude, it.snappedLng ?: it.longitude) }
    }
}
