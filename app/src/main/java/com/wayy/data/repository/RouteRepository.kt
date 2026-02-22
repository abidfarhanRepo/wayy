package com.wayy.data.repository

import android.util.Log
import com.google.gson.Gson
import com.wayy.data.model.OSRMResponse
import com.wayy.data.model.PolylineDecoder
import com.wayy.data.model.Route
import com.wayy.data.model.RouteLeg
import com.wayy.data.model.RouteStep
import com.wayy.data.model.Maneuver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.geojson.Point
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class RouteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "RouteRepository"
        private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving"
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val PHOTON_BASE_URL = "https://photon.komoot.io/api"
        private const val USER_AGENT = "WayyApp/1.0 (contact@wayy.app)"
        
        // Performance tracking
        private var totalRouteRequests = 0
        private var successfulRouteRequests = 0
        private var failedRouteRequests = 0
        private var totalSearchRequests = 0
        private var successfulSearchRequests = 0
    }

    suspend fun getRoute(
        start: Point,
        end: Point
    ): Result<Route> = withContext(Dispatchers.IO) {
        val result = getRouteWithAlternatives(start, end, alternatives = false)
        return@withContext result.map { it.first() }
    }
    
    suspend fun getRouteWithAlternatives(
        start: Point,
        end: Point,
        alternatives: Boolean = true
    ): Result<List<Route>> = withContext(Dispatchers.IO) {
        totalRouteRequests++
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "[ROUTE_START] Request #$totalRouteRequests (alternatives=$alternatives)")
        Log.d(TAG, "[ROUTE_INPUT] Start: (${start.latitude()}, ${start.longitude()})")
        Log.d(TAG, "[ROUTE_INPUT] End: (${end.latitude()}, ${end.longitude()})")
        
        try {
            val alternativesParam = if (alternatives) "&alternatives=true" else ""
            val url = "$OSRM_BASE_URL/${start.longitude()},${start.latitude()};${end.longitude()},${end.latitude()}?overview=full&geometries=polyline&steps=true$alternativesParam"
            Log.v(TAG, "[ROUTE_REQUEST] URL: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            Log.d(TAG, "[ROUTE_NETWORK] Sending request to OSRM...")
            val response = client.newCall(request).execute()
            val networkTime = System.currentTimeMillis() - startTime
            
            Log.v(TAG, "[ROUTE_RESPONSE] HTTP ${response.code}, took ${networkTime}ms")

            if (!response.isSuccessful) {
                failedRouteRequests++
                Log.e(TAG, "[ROUTE_ERROR] HTTP error ${response.code}: ${response.message}")
                logRouteStats()
                return@withContext Result.failure(
                    IOException("OSRM request failed: ${response.code}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                failedRouteRequests++
                Log.e(TAG, "[ROUTE_ERROR] Empty response body")
                logRouteStats()
                return@withContext Result.failure(
                    IOException("Empty response from OSRM")
                )
            }

            Log.v(TAG, "[ROUTE_PARSE] Response size: ${body.length} chars")
            
            val osrmResponse = gson.fromJson(body, OSRMResponse::class.java)
            
            Log.v(TAG, "[ROUTE_PARSE] OSRM code: ${osrmResponse.code}, routes: ${osrmResponse.routes.size}")

            if (osrmResponse.code != "Ok" || osrmResponse.routes.isEmpty()) {
                failedRouteRequests++
                Log.e(TAG, "[ROUTE_ERROR] OSRM error: code=${osrmResponse.code}, routes=${osrmResponse.routes.size}")
                logRouteStats()
                return@withContext Result.failure(
                    IOException("No route found: ${osrmResponse.code}")
                )
            }

            val routes = osrmResponse.routes.map { it.toRoute() }
            Log.d(TAG, "[ROUTE_SUCCESS] Found ${routes.size} routes")
            routes.firstOrNull()?.let { firstRoute ->
                Log.d(TAG, "[ROUTE_SUCCESS] Primary route: distance=${firstRoute.distance}m, duration=${firstRoute.duration}s")
                Log.v(TAG, "[ROUTE_SUCCESS] Legs: ${firstRoute.legs.size}, Steps: ${firstRoute.legs.sumOf { it.steps.size }}")
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            successfulRouteRequests++
            Log.d(TAG, "[ROUTE_SUCCESS] Total time: ${totalTime}ms (network: ${networkTime}ms)")
            logRouteStats()

            Result.success(routes)
        } catch (e: Exception) {
            failedRouteRequests++
            val totalTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "[ROUTE_EXCEPTION] Error after ${totalTime}ms: ${e.javaClass.simpleName} - ${e.message}")
            Log.e(TAG, "[ROUTE_EXCEPTION] Stack trace:", e)
            logRouteStats()
            Result.failure(e)
        }
    }
    
    private fun logRouteStats() {
        val successRate = if (totalRouteRequests > 0) (successfulRouteRequests * 100 / totalRouteRequests) else 0
        Log.d(TAG, "[ROUTE_STATS] Total: $totalRouteRequests, Success: $successfulRouteRequests, Failed: $failedRouteRequests ($successRate%)")
    }

    suspend fun searchPlaces(query: String, location: Point? = null): Result<List<PlaceResult>> =
        withContext(Dispatchers.IO) {
            totalSearchRequests++
            val startTime = System.currentTimeMillis()
            
            Log.d(TAG, "[SEARCH_START] Query: '$query', Location: ${location?.let { "(${it.latitude()}, ${it.longitude()})" } ?: "none"}")
            
            try {
                val attempts = buildSearchAttempts(location)
                Log.v(TAG, "[SEARCH_STRATEGY] Will try ${attempts.size} search attempts")
                
                for ((index, attempt) in attempts.withIndex()) {
                    Log.d(TAG, "[SEARCH_ATTEMPT_${index + 1}] radius=${attempt.radiusKm}km, bounded=${attempt.bounded}, country=${attempt.countryCode ?: "any"}")
                    
                    val result = searchPlacesNominatim(query, attempt, index + 1)
                    if (result.isSuccess) {
                        val places = result.getOrNull().orEmpty()
                        if (places.isNotEmpty()) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d(TAG, "[SEARCH_SUCCESS] Found ${places.size} results in attempt #${index + 1}, took ${totalTime}ms")
                            
                            val sorted = sortByDistance(places, location)
                            successfulSearchRequests++
                            logSearchStats()
                            return@withContext Result.success(sorted)
                        } else {
                            Log.v(TAG, "[SEARCH_ATTEMPT_${index + 1}] No results")
                        }
                    } else {
                        Log.v(TAG, "[SEARCH_ATTEMPT_${index + 1}] Failed: ${result.exceptionOrNull()?.message}")
                    }
                }
                
                Log.d(TAG, "[SEARCH_FALLBACK] Trying Photon fallback...")
                val fallback = searchPlacesPhoton(query, location)
                if (fallback.isSuccess) {
                    val places = fallback.getOrNull().orEmpty()
                    val totalTime = System.currentTimeMillis() - startTime
                    
                    if (places.isNotEmpty()) {
                        Log.d(TAG, "[SEARCH_FALLBACK_SUCCESS] Found ${places.size} results via Photon, took ${totalTime}ms")
                        successfulSearchRequests++
                    } else {
                        Log.w(TAG, "[SEARCH_FALLBACK_EMPTY] No results from Photon either")
                    }
                    
                    logSearchStats()
                    return@withContext Result.success(places)
                }
                
                val totalTime = System.currentTimeMillis() - startTime
                Log.w(TAG, "[SEARCH_FAILED] All attempts failed after ${totalTime}ms")
                logSearchStats()
                fallback
            } catch (e: Exception) {
                val totalTime = System.currentTimeMillis() - startTime
                Log.e(TAG, "[SEARCH_EXCEPTION] Error after ${totalTime}ms: ${e.javaClass.simpleName} - ${e.message}")
                Log.e(TAG, "[SEARCH_EXCEPTION] Stack trace:", e)
                logSearchStats()
                Result.failure(e)
            }
        }
    
    private fun logSearchStats() {
        val successRate = if (totalSearchRequests > 0) (successfulSearchRequests * 100 / totalSearchRequests) else 0
        Log.d(TAG, "[SEARCH_STATS] Total: $totalSearchRequests, Success: $successfulSearchRequests ($successRate%)")
    }

    private fun shouldFallback(code: Int): Boolean {
        return code == 429 || code == 503 || code == 509 || code == 403
    }

    private fun searchPlacesPhoton(query: String, location: Point?): Result<List<PlaceResult>> {
        Log.d(TAG, "[SEARCH_PHOTON] Starting Photon fallback search")
        
        return try {
            val locationBias = location?.let {
                "&lat=${it.latitude()}&lon=${it.longitude()}"
            }.orEmpty()
            val url = "$PHOTON_BASE_URL?q=${URLEncoder.encode(query, "UTF-8")}&limit=10$locationBias"
            Log.v(TAG, "[SEARCH_PHOTON] URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", Locale.getDefault().toLanguageTag())
                .build()

            Log.v(TAG, "[SEARCH_PHOTON] Sending request...")
            val response = client.newCall(request).execute()
            
            response.use { resp ->
                Log.v(TAG, "[SEARCH_PHOTON] HTTP ${resp.code}")
                
                if (!resp.isSuccessful) {
                    Log.w(TAG, "[SEARCH_PHOTON] HTTP error ${resp.code}: ${resp.message}")
                    return Result.failure(IOException("Geocoding fallback failed: ${resp.code}"))
                }
                
                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w(TAG, "[SEARCH_PHOTON] Empty response body")
                    return Result.failure(IOException("Empty fallback response"))
                }
                
                Log.v(TAG, "[SEARCH_PHOTON] Response size: ${body.length} chars")
                
                val photon = gson.fromJson(body, PhotonResponse::class.java)
                Log.v(TAG, "[SEARCH_PHOTON] Parsed ${photon.features.size} features")
                
                val places = photon.features.mapNotNull { it.toPlaceResult() }
                Log.d(TAG, "[SEARCH_PHOTON] Successfully converted ${places.size} results")
                
                // Log first few results for debugging
                places.take(3).forEachIndexed { index, place ->
                    Log.v(TAG, "[SEARCH_PHOTON] Result ${index + 1}: ${place.display_name}")
                }
                
                Result.success(places)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[SEARCH_PHOTON_EXCEPTION] ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "[SEARCH_PHOTON_EXCEPTION] Stack trace:", e)
            Result.failure(e)
        }
    }

    private fun searchPlacesNominatim(query: String, attempt: SearchAttempt, attemptNumber: Int): Result<List<PlaceResult>> {
        val location = attempt.location
        val encoded = URLEncoder.encode(query, "UTF-8")
        val base = StringBuilder("$NOMINATIM_BASE_URL?q=$encoded&format=json&limit=10&addressdetails=1")
        if (!attempt.countryCode.isNullOrBlank()) {
            base.append("&countrycodes=${attempt.countryCode}")
        }
        if (location != null && attempt.radiusKm != null) {
            val bounds = computeViewbox(location, attempt.radiusKm)
            base.append("&viewbox=${bounds.viewbox}")
            if (attempt.bounded) {
                base.append("&bounded=1")
            }
        }
        base.append("&email=contact@wayy.app")
        val url = base.toString()
        Log.v(TAG, "[SEARCH_NOMINATIM_$attemptNumber] URL: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Accept-Language", Locale.getDefault().toLanguageTag())
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            Log.v(TAG, "[SEARCH_NOMINATIM_$attemptNumber] HTTP ${resp.code}")
            
            if (!resp.isSuccessful) {
                Log.w(TAG, "[SEARCH_NOMINATIM_$attemptNumber] HTTP error ${resp.code}: ${resp.message}")
                if (shouldFallback(resp.code)) {
                    return Result.failure(IOException("Geocoding rate limited: ${resp.code}"))
                }
                return Result.failure(IOException("Geocoding failed: ${resp.code}"))
            }
            val body = resp.body?.string()
            if (body.isNullOrBlank()) {
                Log.w(TAG, "[SEARCH_NOMINATIM_$attemptNumber] Empty response")
                return Result.failure(IOException("Empty geocoding response"))
            }
            
            Log.v(TAG, "[SEARCH_NOMINATIM_$attemptNumber] Response size: ${body.length} chars")
            
            val places = gson.fromJson(body, Array<PlaceResult>::class.java).toList()
            Log.d(TAG, "[SEARCH_NOMINATIM_$attemptNumber] Found ${places.size} results")
            
            // Log first few results for debugging
            places.take(3).forEachIndexed { index, place ->
                Log.v(TAG, "[SEARCH_NOMINATIM_$attemptNumber] Result ${index + 1}: ${place.display_name}")
            }
            
            return Result.success(places)
        }
    }

    private fun buildSearchAttempts(location: Point?): List<SearchAttempt> {
        return if (location != null) {
            listOf(
                // Search near current location (most relevant)
                SearchAttempt(location, radiusKm = 10.0, bounded = true, countryCode = null),
                SearchAttempt(location, radiusKm = 50.0, bounded = false, countryCode = null),
                SearchAttempt(location, radiusKm = 150.0, bounded = false, countryCode = null),
                // Global search as fallback
                SearchAttempt(location, radiusKm = null, bounded = false, countryCode = null)
            )
        } else {
            listOf(
                // Global search when no location available
                SearchAttempt(null, radiusKm = null, bounded = false, countryCode = null)
            )
        }
    }

    private fun sortByDistance(places: List<PlaceResult>, location: Point?): List<PlaceResult> {
        if (location == null) return places
        return places.sortedBy { place ->
            haversineMeters(location.latitude(), location.longitude(), place.lat, place.lon)
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    private fun computeViewbox(location: Point, radiusKm: Double): Viewbox {
        val latOffset = radiusKm / 110.574
        val lngOffset = radiusKm / (111.320 * kotlin.math.cos(Math.toRadians(location.latitude())))
        val west = location.longitude() - lngOffset
        val east = location.longitude() + lngOffset
        val south = location.latitude() - latOffset
        val north = location.latitude() + latOffset
        return Viewbox("$west,$south,$east,$north")
    }
}

private fun com.wayy.data.model.OSRMRoute.toRoute(): Route {
    val decodedGeometry = PolylineDecoder.decode(this.geometry)

    val legs = this.legs.map { osrmLeg ->
        RouteLeg(
            steps = osrmLeg.steps.map { osrmStep ->
                RouteStep(
                    instruction = osrmStep.name.takeIf { it.isNotEmpty() }
                        ?: osrmStep.maneuver.type.replace("_", " ")
                            .split(" ")
                            .joinToString(" ") { word ->
                                word.replaceFirstChar { char -> char.uppercase() }
                            },
                    distance = osrmStep.distance,
                    duration = osrmStep.duration,
                    maneuver = Maneuver(
                        type = osrmStep.maneuver.type,
                        modifier = osrmStep.maneuver.modifier,
                        location = Point.fromLngLat(
                            osrmStep.maneuver.location[0],
                            osrmStep.maneuver.location[1]
                        ),
                        bearingBefore = osrmStep.maneuver.bearing_before,
                        bearingAfter = osrmStep.maneuver.bearing_after
                    ),
                    geometry = PolylineDecoder.decode(osrmStep.geometry)
                )
            },
            distance = osrmLeg.distance,
            duration = osrmLeg.duration
        )
    }

    val fallback = legs.flatMap { leg -> leg.steps.flatMap { it.geometry } }
    val mergedGeometry = if (fallback.size > 2 &&
        (decodedGeometry.size <= 2 || fallback.size > decodedGeometry.size)
    ) {
        Log.w(
            "WayyRoute",
            "Using step geometry (route=${decodedGeometry.size}, steps=${fallback.size})"
        )
        dedupeGeometry(fallback)
    } else {
        decodedGeometry
    }

    return Route(
        geometry = mergedGeometry,
        duration = this.duration,
        distance = this.distance,
        legs = legs
    )
}

private data class SearchAttempt(
    val location: Point?,
    val radiusKm: Double?,
    val bounded: Boolean,
    val countryCode: String?
)

private data class Viewbox(val viewbox: String)

private fun dedupeGeometry(points: List<Point>): List<Point> {
    if (points.isEmpty()) return points
    val unique = mutableListOf<Point>()
    points.forEach { point ->
        if (unique.isEmpty()) {
            unique.add(point)
        } else {
            val last = unique.last()
            if (last.latitude() != point.latitude() || last.longitude() != point.longitude()) {
                unique.add(point)
            }
        }
    }
    return unique
}

data class PlaceResult(
    val place_id: Long,
    val display_name: String,
    val lat: Double,
    val lon: Double
)

private data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList()
)

private data class PhotonFeature(
    val geometry: PhotonGeometry,
    val properties: PhotonProperties
) {
    fun toPlaceResult(): PlaceResult? {
        val coords = geometry.coordinates
        if (coords.size < 2) return null
        val lon = coords[0]
        val lat = coords[1]
        val displayName = listOfNotNull(
            properties.name,
            properties.city,
            properties.state,
            properties.country
        ).joinToString(", ").ifBlank { "Unknown" }
        val placeId = properties.osm_id ?: "$lat,$lon,$displayName".hashCode().toLong()
        return PlaceResult(
            place_id = placeId,
            display_name = displayName,
            lat = lat,
            lon = lon
        )
    }
}

private data class PhotonGeometry(
    val coordinates: List<Double> = emptyList()
)

private data class PhotonProperties(
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val osm_id: Long? = null
)
