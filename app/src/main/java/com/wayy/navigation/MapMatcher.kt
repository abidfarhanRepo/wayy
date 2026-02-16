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
        val inputLat = location.latitude()
        val inputLon = location.longitude()
        
        Log.d(TAG, "[MATCHER_START] Input location: lat=$inputLat, lon=$inputLon")
        
        try {
            val url = "$OSRM_NEAREST_URL/$inputLon,$inputLat?number=1"
            Log.v(TAG, "[MATCHER_REQUEST] URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "[MATCHER_ERROR] HTTP error: ${response.code} - ${response.message}")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Log.w(TAG, "[MATCHER_ERROR] Empty response body")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            Log.v(TAG, "[MATCHER_RESPONSE] Body: ${body.take(200)}...")
            
            // Parse simple JSON response
            val json = org.json.JSONObject(body)
            
            val code = json.optString("code")
            if (code != "Ok") {
                val message = json.optString("message", "Unknown error")
                Log.w(TAG, "[MATCHER_ERROR] OSRM error: code=$code, message=$message")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val waypoints = json.optJSONArray("waypoints")
            if (waypoints == null || waypoints.length() == 0) {
                Log.w(TAG, "[MATCHER_ERROR] No waypoints in response")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val waypoint = waypoints.optJSONObject(0)
            if (waypoint == null) {
                Log.w(TAG, "[MATCHER_ERROR] First waypoint is null")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val locationArray = waypoint.optJSONArray("location")
            val distance = waypoint.optDouble("distance", -1.0)
            val name = waypoint.optString("name", "").takeIf { it.isNotEmpty() }
            
            Log.v(TAG, "[MATCHER_PARSE] waypoint={distance=$distance, name=$name, hasLocation=${locationArray != null}}")
            
            if (locationArray == null || locationArray.length() < 2) {
                Log.w(TAG, "[MATCHER_ERROR] Invalid location array")
                return@withContext MatchedLocation(location, null, 0.0, 0.0f)
            }
            
            val snappedLon = locationArray.getDouble(0)
            val snappedLat = locationArray.getDouble(1)
            
            // Don't snap if too far from road
            if (distance > SNAP_THRESHOLD_METERS) {
                Log.d(TAG, "[MATCHER_SKIP] Distance too far: ${distance}m > threshold ${SNAP_THRESHOLD_METERS}m")
                Log.d(TAG, "[MATCHER_SKIP] Keeping original location (road=$name)")
                return@withContext MatchedLocation(location, name, distance, 0.3f)
            }
            
            val snappedPoint = Point.fromLngLat(snappedLon, snappedLat)
            
            // Calculate shift distance
            val shiftDistance = calculateDistance(inputLat, inputLon, snappedLat, snappedLon)
            
            // Calculate confidence based on distance
            val confidence = (1.0 - (distance / SNAP_THRESHOLD_METERS)).toFloat().coerceIn(0.0f, 1.0f)
            
            Log.d(TAG, "[MATCHER_SUCCESS] Snapped to road: '$name'")
            Log.d(TAG, "[MATCHER_SUCCESS] Original: ($inputLat, $inputLon) -> Snapped: ($snappedLat, $snappedLon)")
            Log.d(TAG, "[MATCHER_SUCCESS] Shift: ${shiftDistance}m, Distance from road: ${distance}m, Confidence: $confidence")
            
            MatchedLocation(snappedPoint, name, distance, confidence)
            
        } catch (e: Exception) {
            Log.e(TAG, "[MATCHER_EXCEPTION] Error snapping to road: ${e.javaClass.simpleName} - ${e.message}")
            Log.e(TAG, "[MATCHER_EXCEPTION] Stack trace:", e)
            MatchedLocation(location, null, 0.0, 0.0f)
        }
    }
    
    /**
     * Calculate distance between two coordinates in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth's radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }
    
    /**
     * Match a sequence of GPS points to the road network.
     * Useful for smoothing a traveled path.
     * 
     * @param points List of GPS points
     * @return List of matched points on roads
     */
    suspend fun matchPath(points: List<Point>): List<Point> = withContext(Dispatchers.IO) {
        if (points.size < 2) {
            Log.w(TAG, "[MATCHPATH_SKIP] Not enough points: ${points.size} (need at least 2)")
            return@withContext points
        }
        
        Log.d(TAG, "[MATCHPATH_START] Matching path with ${points.size} points")
        Log.v(TAG, "[MATCHPATH_INPUT] First: (${points.first().latitude()}, ${points.first().longitude()}), " +
                "Last: (${points.last().latitude()}, ${points.last().longitude()})")
        
        try {
            // Build coordinates string for OSRM match
            val coordinates = points.joinToString(";") { 
                "${it.longitude()},${it.latitude()}" 
            }
            
            val url = "$OSRM_MATCH_URL/$coordinates?overview=false&geometries=polyline"
            Log.v(TAG, "[MATCHPATH_REQUEST] URL length: ${url.length} chars")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "[MATCHPATH_ERROR] HTTP error: ${response.code} - ${response.message}")
                return@withContext points
            }
            
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Log.w(TAG, "[MATCHPATH_ERROR] Empty response body")
                return@withContext points
            }
            
            Log.v(TAG, "[MATCHPATH_RESPONSE] Body size: ${body.length} chars")
            
            val json = org.json.JSONObject(body)
            
            val code = json.optString("code")
            if (code != "Ok") {
                val message = json.optString("message", "Unknown error")
                Log.w(TAG, "[MATCHPATH_ERROR] OSRM error: code=$code, message=$message")
                return@withContext points
            }
            
            val tracepoints = json.optJSONArray("tracepoints")
            if (tracepoints == null) {
                Log.w(TAG, "[MATCHPATH_ERROR] No tracepoints in response")
                return@withContext points
            }
            
            // Extract matched points
            val matchedPoints = mutableListOf<Point>()
            var matchedCount = 0
            var unmatchedCount = 0
            
            for (i in 0 until tracepoints.length()) {
                val tracepoint = tracepoints.optJSONObject(i)
                if (tracepoint == null || tracepoint.isNull("location")) {
                    // No match for this point, use original
                    matchedPoints.add(points[i])
                    unmatchedCount++
                    Log.v(TAG, "[MATCHPATH_POINT] #$i: No match, using original")
                } else {
                    val location = tracepoint.optJSONArray("location")
                    if (location != null && location.length() >= 2) {
                        val lon = location.getDouble(0)
                        val lat = location.getDouble(1)
                        matchedPoints.add(Point.fromLngLat(lon, lat))
                        matchedCount++
                        
                        val original = points[i]
                        val distance = calculateDistance(
                            original.latitude(), original.longitude(),
                            lat, lon
                        )
                        Log.v(TAG, "[MATCHPATH_POINT] #$i: Matched (shift=${distance}m)")
                    } else {
                        matchedPoints.add(points[i])
                        unmatchedCount++
                        Log.w(TAG, "[MATCHPATH_POINT] #$i: Invalid location array")
                    }
                }
            }
            
            val matchRate = if (points.isNotEmpty()) (matchedCount * 100 / points.size) else 0
            Log.d(TAG, "[MATCHPATH_SUCCESS] Matched: $matchedCount/${points.size} points ($matchRate%)")
            Log.d(TAG, "[MATCHPATH_SUCCESS] Unmatched: $unmatchedCount, Total output: ${matchedPoints.size}")
            
            matchedPoints
            
        } catch (e: Exception) {
            Log.e(TAG, "[MATCHPATH_EXCEPTION] Error matching path: ${e.javaClass.simpleName} - ${e.message}")
            Log.e(TAG, "[MATCHPATH_EXCEPTION] Stack trace:", e)
            points
        }
    }
}