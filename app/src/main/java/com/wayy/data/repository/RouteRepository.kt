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
        private const val OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving"
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val PHOTON_BASE_URL = "https://photon.komoot.io/api"
        private const val USER_AGENT = "WayyApp/1.0 (contact@wayy.app)"
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
                val url = "$NOMINATIM_BASE_URL?q=${URLEncoder.encode(query, "UTF-8")}&format=json&limit=5&email=contact@wayy.app"
                Log.d("WayySearch", "Searching: $url")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept-Language", Locale.getDefault().toLanguageTag())
                    .build()

                val response = client.newCall(request).execute()

                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("WayySearch", "Geocoding failed: ${resp.code}")
                        if (shouldFallback(resp.code)) {
                            val fallback = searchPlacesPhoton(query)
                            if (fallback.isSuccess) {
                                return@withContext fallback
                            }
                        }
                        val message = if (shouldFallback(resp.code)) {
                            "Geocoding rate limited. Try again later."
                        } else {
                            "Geocoding failed: ${resp.code}"
                        }
                        return@withContext Result.failure(IOException(message))
                    }

                    val body = resp.body?.string()
                    if (body.isNullOrBlank()) {
                        Log.w("WayySearch", "Empty geocoding response")
                        return@withContext Result.failure(
                            IOException("Empty geocoding response")
                        )
                    }

                    val places = gson.fromJson(body, Array<PlaceResult>::class.java).toList()
                    Log.d("WayySearch", "Geocoding results: ${places.size}")
                    Result.success(places)
                }
            } catch (e: Exception) {
                Log.e("WayySearch", "Geocoding error", e)
                Result.failure(e)
            }
        }

    private fun shouldFallback(code: Int): Boolean {
        return code == 429 || code == 503 || code == 509 || code == 403
    }

    private fun searchPlacesPhoton(query: String): Result<List<PlaceResult>> {
        return try {
            val url = "$PHOTON_BASE_URL?q=${URLEncoder.encode(query, "UTF-8")}&limit=5"
            Log.d("WayySearch", "Photon fallback: $url")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", Locale.getDefault().toLanguageTag())
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("WayySearch", "Photon failed: ${resp.code}")
                    return Result.failure(IOException("Geocoding fallback failed: ${resp.code}"))
                }
                val body = resp.body?.string()
                if (body.isNullOrBlank()) {
                    return Result.failure(IOException("Empty fallback response"))
                }
                val photon = gson.fromJson(body, PhotonResponse::class.java)
                val places = photon.features.mapNotNull { it.toPlaceResult() }
                Result.success(places)
            }
        } catch (e: Exception) {
            Log.e("WayySearch", "Photon error", e)
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
