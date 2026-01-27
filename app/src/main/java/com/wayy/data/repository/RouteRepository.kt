package com.wayy.data.repository

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
import java.util.concurrent.TimeUnit

/**
 * Repository for routing operations using OSRM
 */
class RouteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        // Using public OSRM demo server
        // For production, host your own OSRM instance
        private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving"

        // Alternative servers you can use:
        // "https://routing.openstreetmap.de/routed-car/route/v1/driving"
        // "https://osrm.gosolve.nl/route/v1/driving"
    }

    /**
     * Get route from start to end coordinates
     *
     * @param start Starting point (longitude, latitude)
     * @param end Ending point (longitude, latitude)
     * @return Result containing Route or error
     */
    suspend fun getRoute(
        start: Point,
        end: Point
    ): Result<Route> = withContext(Dispatchers.IO) {
        try {
            val url = "$OSRM_BASE_URL/${start.longitude()},${start.latitude()};${end.longitude()},${end.latitude()}?overview=full&geometries=polyline&steps=true"

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("OSRM request failed: ${response.code}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(
                    IOException("Empty response from OSRM")
                )
            }

            val osrmResponse = gson.fromJson(body, OSRMResponse::class.java)

            if (osrmResponse.code != "Ok" || osrmResponse.routes.isEmpty()) {
                return@withContext Result.failure(
                    IOException("No route found")
                )
            }

            val osrmRoute = osrmResponse.routes.first()
            val route = osrmRoute.toRoute()

            Result.success(route)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Search for places (placeholder for future implementation)
     * In a real app, you would use a geocoding API like Nominatim or Mapbox
     */
    suspend fun searchPlaces(query: String): Result<List<PlaceResult>> =
        withContext(Dispatchers.IO) {
            try {
                // Using Nominatim (OpenStreetMap) for geocoding
                // This is a free service, but requires proper user agent
                val url = "https://nominatim.openstreetmap.org/search?q=${query.encode()}&format=json&limit=5"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Wayy/1.0")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Geocoding failed: ${response.code}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException("Empty geocoding response")
                    )
                }

                val places = gson.fromJson(body, Array<PlaceResult>::class.java).toList()
                Result.success(places)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun String.encode(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}

/**
 * Extension function to convert OSRM response to app Route model
 */
private fun com.wayy.data.model.OSRMRoute.toRoute(): Route {
    val geometry = PolylineDecoder.decode(this.geometry)

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

    return Route(
        geometry = geometry,
        duration = this.duration,
        distance = this.distance,
        legs = legs
    )
}

/**
 * Simple place search result from geocoding
 */
data class PlaceResult(
    val place_id: Long,
    val display_name: String,
    val lat: Double,
    val lon: Double
)
