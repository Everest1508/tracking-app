package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.LocationEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Interactive Leaflet.js OpenStreetMap Composable WebView.
 * Renders raw GPS polylines and OSRM road-snapped polylines with Leaflet OpenStreetMap.
 *
 * FLUTTER PORTING NOTE:
 * In Flutter, replace this with the native `flutter_map` widget:
 * ```dart
 * FlutterMap(
 *   options: MapOptions(initialCenter: LatLng(lat, lng), initialZoom: 14),
 *   children: [
 *     TileLayer(urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'),
 *     PolylineLayer(polylines: [Polyline(points: points, color: Colors.emerald, strokeWidth: 4)]),
 *   ],
 * )
 * ```
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMapView(
    rawLocations: List<LocationEntity>,
    snappedPoints: List<Pair<Double, Double>>,
    showRawPath: Boolean = true,
    showSnappedPath: Boolean = true,
    onPointClicked: ((LocationEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            addJavascriptInterface(WebAppInterface(onPointClicked), "AndroidBridge")
            loadDataWithBaseURL(
                "https://openstreetmap.org",
                generateLeafletHtml(),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    // Update map markers and polylines whenever data changes
    LaunchedEffect(rawLocations, snappedPoints, showRawPath, showSnappedPath) {
        val rawJsonArray = JSONArray()
        for (loc in rawLocations) {
            val obj = JSONObject().apply {
                put("lat", loc.latitude)
                put("lng", loc.longitude)
                put("speed", (loc.speed * 3.6f).toInt())
                put("acc", loc.accuracy.toInt())
                put("time", loc.timestamp)
            }
            rawJsonArray.put(obj)
        }

        val snappedJsonArray = JSONArray()
        for (pt in snappedPoints) {
            val obj = JSONObject().apply {
                put("lat", pt.first)
                put("lng", pt.second)
            }
            snappedJsonArray.put(obj)
        }

        val script = "updatePaths($rawJsonArray, $snappedJsonArray, $showRawPath, $showSnappedPath);"
        webView.evaluateJavascript(script, null)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

class WebAppInterface(private val onPointClicked: ((LocationEntity) -> Unit)?) {
    @JavascriptInterface
    fun onMarkerClick(lat: Double, lng: Double, speed: Float, acc: Float, time: Long) {
        onPointClicked?.invoke(
            LocationEntity(
                tripId = "",
                latitude = lat,
                longitude = lng,
                speed = speed,
                accuracy = acc,
                timestamp = time
            )
        )
    }
}

private fun generateLeafletHtml(): String {
    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #0f172a; }
            .leaflet-container { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
            .custom-popup { font-size: 13px; line-height: 1.5; color: #0f172a; }
            .start-badge { background: #10b981; color: white; padding: 4px 8px; border-radius: 12px; font-weight: bold; font-size: 10px; }
            .end-badge { background: #f43f5e; color: white; padding: 4px 8px; border-radius: 12px; font-weight: bold; font-size: 10px; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: false }).setView([37.7749, -122.4194], 13);
            L.control.zoom({ position: 'bottomright' }).addTo(map);

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '© OpenStreetMap contributors'
            }).addTo(map);

            var rawPolyline = null;
            var snappedPolyline = null;
            var startMarker = null;
            var endMarker = null;

            function updatePaths(rawPoints, snappedPoints, showRaw, showSnapped) {
                if (rawPolyline) map.removeLayer(rawPolyline);
                if (snappedPolyline) map.removeLayer(snappedPolyline);
                if (startMarker) map.removeLayer(startMarker);
                if (endMarker) map.removeLayer(endMarker);

                var bounds = [];

                // 1. Raw GPS Path (Dashed Coral Line)
                if (showRaw && rawPoints && rawPoints.length > 0) {
                    var rawLatLngs = rawPoints.map(p => [p.lat, p.lng]);
                    rawPolyline = L.polyline(rawLatLngs, {
                        color: '#f43f5e',
                        weight: 3,
                        dashArray: '6, 8',
                        opacity: 0.85
                    }).addTo(map);
                    bounds = bounds.concat(rawLatLngs);
                }

                // 2. Road Snapped / Smoothed Path (Solid Emerald Line)
                if (showSnapped && snappedPoints && snappedPoints.length > 0) {
                    var snappedLatLngs = snappedPoints.map(p => [p.lat, p.lng]);
                    snappedPolyline = L.polyline(snappedLatLngs, {
                        color: '#10b981',
                        weight: 5,
                        opacity: 0.95,
                        lineJoin: 'round'
                    }).addTo(map);
                    bounds = bounds.concat(snappedLatLngs);
                } else if (showSnapped && rawPoints && rawPoints.length > 0) {
                    var rawLatLngs = rawPoints.map(p => [p.lat, p.lng]);
                    snappedPolyline = L.polyline(rawLatLngs, {
                        color: '#10b981',
                        weight: 5,
                        opacity: 0.95
                    }).addTo(map);
                }

                // 3. Start & End Markers
                var displayList = (snappedPoints && snappedPoints.length > 0) ? snappedPoints : rawPoints;
                if (displayList && displayList.length > 0) {
                    var startPt = displayList[0];
                    var endPt = displayList[displayList.length - 1];

                    var startIcon = L.divIcon({
                        className: 'custom-div-icon',
                        html: "<span class='start-badge'>START</span>",
                        iconSize: [40, 20],
                        iconAnchor: [20, 10]
                    });
                    startMarker = L.marker([startPt.lat, startPt.lng], { icon: startIcon }).addTo(map)
                        .bindPopup("<div class='custom-popup'><b>Trip Start Point</b><br>Lat: " + startPt.lat.toFixed(5) + "<br>Lng: " + startPt.lng.toFixed(5) + "</div>");

                    if (displayList.length > 1) {
                        var endIcon = L.divIcon({
                            className: 'custom-div-icon',
                            html: "<span class='end-badge'>END</span>",
                            iconSize: [40, 20],
                            iconAnchor: [20, 10]
                        });
                        endMarker = L.marker([endPt.lat, endPt.lng], { icon: endIcon }).addTo(map)
                            .bindPopup("<div class='custom-popup'><b>Trip Latest Point</b><br>Lat: " + endPt.lat.toFixed(5) + "<br>Lng: " + endPt.lng.toFixed(5) + "</div>");
                    }
                }

                if (bounds.length > 0) {
                    map.fitBounds(bounds, { padding: [40, 40] });
                }
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}
