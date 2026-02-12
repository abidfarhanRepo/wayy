package com.wayy.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.geojson.Point
import kotlin.math.abs

/**
 * Location manager using Google Play Services
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager: AndroidLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private var lastLocation: Location? = null
    private var lastLocationTimestampNanos: Long? = null

    companion object {
        private const val LOCATION_UPDATE_INTERVAL = 1000L  // 1 second
        private const val FASTEST_UPDATE_INTERVAL = 500L    // 500ms
    }

    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get last known location
     */
    suspend fun getLastKnownLocation(): Point? {
        if (!hasLocationPermission()) return null

        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location == null) {
                Log.w("WayyLocation", "Last known location is null")
                getSystemLastKnownLocation()
            } else {
                Log.d(
                    "WayyLocation",
                    "Last known location lat=${location.latitude}, lon=${location.longitude}"
                )
                Point.fromLngLat(location.longitude, location.latitude)
            }
        } catch (e: Exception) {
            Log.e("WayyLocation", "Last known location error", e)
            null
        }
    }

    /**
     * Request a current high-accuracy location fix.
     */
    suspend fun getCurrentLocation(): Point? {
        if (!hasLocationPermission()) return null

        return try {
            val highAccuracy = withTimeoutOrNull(5000) {
                requestCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)
            }
            if (highAccuracy != null) {
                highAccuracy
            } else {
                Log.w("WayyLocation", "High accuracy location null or timed out, falling back")
                withTimeoutOrNull(5000) {
                    requestCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                }
            }
        } catch (e: Exception) {
            Log.e("WayyLocation", "Current location error", e)
            null
        }
    }

    private suspend fun requestCurrentLocation(priority: Int): Point? {
        val tokenSource = CancellationTokenSource()
        val location = fusedLocationClient.getCurrentLocation(
            priority,
            tokenSource.token
        ).await()
        return if (location == null) {
            Log.w("WayyLocation", "Current location is null for priority=$priority")
            null
        } else {
            Log.d(
                "WayyLocation",
                "Current location lat=${location.latitude}, lon=${location.longitude}"
            )
            Point.fromLngLat(location.longitude, location.latitude)
        }
    }

    /**
     * Start location updates as Flow
     */
    fun startLocationUpdates(): Flow<LocationUpdate> = callbackFlow {
        if (!hasLocationPermission()) {
            Log.w("WayyLocation", "Missing location permission")
            close()
            return@callbackFlow
        }

        Log.d("WayyLocation", "Starting location updates")

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMinUpdateDistanceMeters(1f)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(
                        "WayyLocation",
                        "Location update lat=${location.latitude}, lon=${location.longitude}"
                    )
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    val speedMps = resolveSpeedMps(location)
                    val speed = speedMps * 2.23694f // m/s to mph

                    trySend(
                        LocationUpdate(
                            location = point,
                            speed = speed,
                            bearing = if (location.hasBearing()) location.bearing else 0f,
                            accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                        )
                    )
                }
            }
        }

        val systemListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(
                    "WayyLocation",
                    "System update lat=${location.latitude}, lon=${location.longitude}"
                )
                val point = Point.fromLngLat(location.longitude, location.latitude)
                val speedMps = resolveSpeedMps(location)
                val speed = speedMps * 2.23694f
                trySend(
                    LocationUpdate(
                        location = point,
                        speed = speed,
                        bearing = if (location.hasBearing()) location.bearing else 0f,
                        accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                    )
                )
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        requestSystemLocationUpdates(systemListener)

        awaitClose {
            Log.d("WayyLocation", "Stopping location updates")
            fusedLocationClient.removeLocationUpdates(locationCallback)
            systemLocationManager.removeUpdates(systemListener)
        }
    }

    private fun getSystemLastKnownLocation(): Point? {
        val gpsLocation = runCatching {
            systemLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
        }.getOrNull()
        val networkLocation = runCatching {
            systemLocationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
        }.getOrNull()

        val bestLocation = listOfNotNull(gpsLocation, networkLocation).maxByOrNull { it.time }
        return bestLocation?.let {
            Log.d(
                "WayyLocation",
                "System last known lat=${it.latitude}, lon=${it.longitude}"
            )
            Point.fromLngLat(it.longitude, it.latitude)
        }
    }

    private fun requestSystemLocationUpdates(listener: LocationListener) {
        val providers = systemLocationManager.getProviders(true)
        Log.d("WayyLocation", "System providers enabled: $providers")
        runCatching {
            if (systemLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                Log.d("WayyLocation", "Requesting GPS provider updates")
                systemLocationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            } else {
                Log.w("WayyLocation", "GPS provider disabled")
            }
        }
        runCatching {
            if (systemLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                Log.d("WayyLocation", "Requesting network provider updates")
                systemLocationManager.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            } else {
                Log.w("WayyLocation", "Network provider disabled")
            }
        }
    }

    private fun resolveSpeedMps(location: Location): Float {
        val reported = if (location.hasSpeed()) location.speed else 0f
        val computed = computeSpeedFromDelta(location)
        val useComputed = computed > 0f && (reported <= 0.5f || kotlin.math.abs(reported - computed) > 3f)
        return if (useComputed) computed else reported
    }

    private fun computeSpeedFromDelta(location: Location): Float {
        val previous = lastLocation
        val previousTimestamp = lastLocationTimestampNanos
        val currentTimestamp = resolveTimestampNanos(location)
        var speedMps = 0f

        if (previous != null && previousTimestamp != null && currentTimestamp > previousTimestamp) {
            val deltaSeconds = (currentTimestamp - previousTimestamp) / 1_000_000_000.0
            if (deltaSeconds > 0) {
                val distance = location.distanceTo(previous)
                speedMps = (distance / deltaSeconds).toFloat()
            }
        }

        lastLocation = location
        lastLocationTimestampNanos = currentTimestamp
        return speedMps
    }

    private fun resolveTimestampNanos(location: Location): Long {
        return if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos
        } else {
            location.time * 1_000_000L
        }
    }

    /**
     * Calculate speed from two location points
     */
    fun calculateSpeed(
        from: Point,
        to: Point,
        timeSeconds: Long
    ): Float {
        val distance = haversineDistance(
            from.latitude(), from.longitude(),
            to.latitude(), to.longitude()
        )
        val timeHours = timeSeconds / 3600.0
        return if (timeHours > 0) {
            (distance / timeHours).toFloat()
        } else {
            0f
        }
    }

    /**
     * Haversine distance formula between two coordinates (in miles)
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 3958.8 // Earth's radius in miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

/**
 * Location update data class
 */
data class LocationUpdate(
    val location: Point,
    val speed: Float,        // in mph
    val bearing: Float,      // in degrees
    val accuracy: Float      // in meters
)
