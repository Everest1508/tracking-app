package com.example.domain.export

import com.example.data.model.LocationEntity
import com.example.data.model.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility for exporting location tracks to standard GPX and GeoJSON files.
 */
object ExportManager {

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Export trip locations to GPX format string.
     */
    fun exportToGpx(trip: TripEntity, locations: List<LocationEntity>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"PathTracker Android App\"\n")
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(trip.title)}</name>\n")
        sb.append("    <time>${iso8601Format.format(Date(trip.startTime))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(trip.title)}</name>\n")
        sb.append("    <trkseg>\n")

        for (loc in locations) {
            val lat = loc.snappedLat ?: loc.latitude
            val lng = loc.snappedLng ?: loc.longitude
            sb.append("      <trkpt lat=\"$lat\" lon=\"$lng\">\n")
            sb.append("        <ele>${loc.altitude}</ele>\n")
            sb.append("        <time>${iso8601Format.format(Date(loc.timestamp))}</time>\n")
            sb.append("        <speed>${loc.speed}</speed>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }

    /**
     * Export trip locations to GeoJSON format string.
     */
    fun exportToGeoJson(trip: TripEntity, locations: List<LocationEntity>): String {
        val coordinates = locations.joinToString(",") { loc ->
            val lat = loc.snappedLat ?: loc.latitude
            val lng = loc.snappedLng ?: loc.longitude
            "[ $lng, $lat, ${loc.altitude} ]"
        }

        return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "tripId": "${trip.id}",
                "title": "${escapeJson(trip.title)}",
                "startTime": ${trip.startTime},
                "totalDistanceMeters": ${trip.totalDistanceMeters},
                "averageSpeedKmh": ${trip.averageSpeedKmh}
              },
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  $coordinates
                ]
              }
            }
          ]
        }
        """.trimIndent()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
