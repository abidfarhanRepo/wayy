package com.wayy.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.maplibre.geojson.Point
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/**
 * Location manager using Google Play Services
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

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
            Point.fromLngLat(location.longitude, location.latitude)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Start location updates as Flow
     */
    fun startLocationUpdates(): Flow<LocationUpdate> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setWaitForAccurateLocation(true)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    val speed = if (location.hasSpeed()) location.speed * 2.23694f else 0f // m/s to mph

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

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
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
