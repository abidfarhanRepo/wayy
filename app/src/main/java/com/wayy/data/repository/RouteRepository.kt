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
import java.util.concurrent.TimeUnit

class RouteRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving"
    }

    suspend fun getRoute(
        start: Point,
        end: Point
    ): Result<Route> = withContext(Dispatchers.IO) {
        try {
            val url = "$OSRM_BASE_URL/${start.longitude()},${start.latitude()};${end.longitude()},${end.latitude()}?overview=full&geometries=polyline&steps=true"
            Log.d("WayyRoute", "Requesting route: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w("WayyRoute", "OSRM request failed: ${response.code}")
                return@withContext Result.failure(
                    IOException("OSRM request failed: ${response.code}")
                )
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                Log.w("WayyRoute", "OSRM empty response")
                return@withContext Result.failure(
                    IOException("Empty response from OSRM")
                )
            }

            val osrmResponse = gson.fromJson(body, OSRMResponse::class.java)

            if (osrmResponse.code != "Ok" || osrmResponse.routes.isEmpty()) {
                Log.w("WayyRoute", "OSRM no route found: ${osrmResponse.code}")
                return@withContext Result.failure(
                    IOException("No route found")
                )
            }

            val osrmRoute = osrmResponse.routes.first()
            val route = osrmRoute.toRoute()

            Result.success(route)
        } catch (e: Exception) {
            Log.e("WayyRoute", "OSRM request error", e)
            Result.failure(e)
        }
    }

    suspend fun searchPlaces(query: String): Result<List<PlaceResult>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=5"
                Log.d("WayySearch", "Searching: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "WayyApp/1.0 (contact@wayy.app)")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w("WayySearch", "Geocoding failed: ${response.code}")
                    return@withContext Result.failure(
                        IOException("Geocoding failed: ${response.code}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    Log.w("WayySearch", "Empty geocoding response")
                    return@withContext Result.failure(
                        IOException("Empty geocoding response")
                    )
                }

                val places = gson.fromJson(body, Array<PlaceResult>::class.java).toList()
                Log.d("WayySearch", "Geocoding results: ${places.size}")
                Result.success(places)
            } catch (e: Exception) {
                Log.e("WayySearch", "Geocoding error", e)
                Result.failure(e)
            }
        }
}

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

data class PlaceResult(
    val place_id: Long,
    val display_name: String,
    val lat: Double,
    val lon: Double
)
