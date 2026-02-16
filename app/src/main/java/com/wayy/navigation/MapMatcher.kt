package com.wayy.navigation

import android.util.Log
import com.wayy.data.model.PolylineDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.geojson.Point
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Map matcher using OSRM nearest service to snap GPS coordinates to roads.
 * Prevents showing location "driving through buildings".
 */
class MapMatcher {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "MapMatcher"
        private const val OSRM_NEAREST_URL = "https://router.project-osrm.org/nearest/v1/driving"
        private const val OSRM_MATCH_URL = "https://router.project-osrm.org/match/v1/driving"
        private const val SNAP_THRESHOLD_METERS = 50.0 // Don't snap if further than this
    }
    
    data class MatchedLocation(
        val location: Point,
        val roadName: String?,
        val distance: Double, // Distance from original to snapped point
        val confidence: Float // 0.0 - 1.0
    )
    
    /**
     * Snap a single GPS coordinate to the nearest road.
     * 
     * @param location GPS location
     * @return Matched location on road, or original if no match found
     */
    suspend fun snapToRoad(location: Point): MatchedLocation = withContext(Dispatchers.IO) {
        try {
            val url = "$OSRM_NEAREST_URL/${location.longitude()},${location.latitude()}?number=1"
            Log.d(TAG, "Snapping to road: $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "OSRM nearest failed: ${response.code}")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            // Parse simple JSON response
            val json = org.json.JSONObject(body)
            
            if (json.optString("code") != "Ok") {
                Log.w(TAG, "OSRM nearest error: ${json.optString("message")}")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val waypoints = json.optJSONArray("waypoints")
            if (waypoints == null || waypoints.length() == 0) {
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val waypoint = waypoints.optJSONObject(0)
            val location_array = waypoint?.optJSONArray("location")
            val distance = waypoint?.optDouble("distance", 0.0) ?: 0.0
            val name = waypoint?.optString("name")
            
            if (location_array == null || location_array.length() < 2) {
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            // Don't snap if too far from road
            if (distance > SNAP_THRESHOLD_METERS) {
                Log.d(TAG, "Too far from road (${distance}m), not snapping")
                return@withContext MatchedLocation(location, name, distance, 0.3f)
            }
            
            val snappedLon = location_array.getDouble(0)
            val snappedLat = location_array.getDouble(1)
            val snappedPoint = Point.fromLngLat(snappedLon, snappedLat)
            
            // Calculate confidence based on distance
            val confidence = (1.0 - (distance / SNAP_THRESHOLD_METERS)).toFloat().coerceIn(0.0f, 1.0f)
            
            Log.d(TAG, "Snapped to road: distance=${distance}m, name=$name, confidence=$confidence")
            
            MatchedLocation(snappedPoint, name, distance, confidence)
            
        } catch (e: Exception) {
            Log.e(TAG, "Map matching error", e)
            MatchedLocation(location, null, 0.0, 0.0f)
        }
    }
    
    /**
     * Match a sequence of GPS points to the road network.
     * Useful for smoothing a traveled path.
     * 
     * @param points List of GPS points
     * @return List of matched points on roads
     */
    suspend fun matchPath(points: List<Point>): List<Point> = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext points
        
        try {
            // Build coordinates string for OSRM match
            val coordinates = points.joinToString(";") { 
                "${it.longitude()},${it.latitude()}" 
            }
            
            val url = "$OSRM_MATCH_URL/$coordinates?overview=false&geometries=polyline"
            Log.d(TAG, "Matching path with ${points.size} points")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "OSRM match failed: ${response.code}")
                return@withContext points
            }
            
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext points
            }
            
            val json = org.json.JSONObject(body)
            
            if (json.optString("code") != "Ok") {
                Log.w(TAG, "OSRM match error: ${json.optString("message")}")
                return@withContext points
            }
            
            val tracepoints = json.optJSONArray("tracepoints")
            if (tracepoints == null) {
                return@withContext points
            }
            
            // Extract matched points
            val matchedPoints = mutableListOf<Point>()
            for (i in 0 until tracepoints.length()) {
                val tracepoint = tracepoints.optJSONObject(i)
                if (tracepoint == null || tracepoint.isNull("location")) {
                    // No match for this point, use original
                    matchedPoints.add(points[i])
                } else {
                    val location = tracepoint.optJSONArray("location")
                    if (location != null && location.length() >= 2) {
                        val lon = location.getDouble(0)
                        val lat = location.getDouble(1)
                        matchedPoints.add(Point.fromLngLat(lon, lat))
                    } else {
                        matchedPoints.add(points[i])
                    }
                }
            }
            
            Log.d(TAG, "Path matched: ${matchedPoints.size} points")
            matchedPoints
            
        } catch (e: Exception) {
            Log.e(TAG, "Path matching error", e)
            points
        }
    }
}