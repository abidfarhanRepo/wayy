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
import com.wayy.navigation.GpsKalmanFilter
import com.wayy.navigation.MapMatcher
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
    
    // GPS smoothing and map matching
    private val kalmanFilter = GpsKalmanFilter()
    private val mapMatcher = MapMatcher()
    private var enableKalmanFilter = true
    private var enableMapMatching = true
    
    // Logging stats
    private var totalUpdates = 0
    private var filteredUpdates = 0
    private var rejectedUpdates = 0

    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_UPDATE_INTERVAL = 1000L  // 1 second
        private const val FASTEST_UPDATE_INTERVAL = 500L    // 500ms
        private const val SPEED_ACCURACY_METERS = 15f
    }
    
    /**
     * Enable/disable GPS smoothing
     */
    fun setKalmanFilterEnabled(enabled: Boolean) {
        enableKalmanFilter = enabled
        if (!enabled) kalmanFilter.reset()
        Log.d(TAG, "[SETTINGS] Kalman filter ${if (enabled) "enabled" else "disabled"}")
        logStats()
    }
    
    /**
     * Enable/disable map matching
     */
    fun setMapMatchingEnabled(enabled: Boolean) {
        enableMapMatching = enabled
        Log.d(TAG, "[SETTINGS] Map matching ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Log current statistics
     */
    private fun logStats() {
        val filterRate = if (totalUpdates > 0) (filteredUpdates * 100 / totalUpdates) else 0
        val rejectRate = if (totalUpdates > 0) (rejectedUpdates * 100 / totalUpdates) else 0
        Log.d(TAG, "[STATS] Total: $totalUpdates, Filtered: $filteredUpdates ($filterRate%), Rejected: $rejectedUpdates ($rejectRate%)")
    }

    /**
     * Process a location update through Kalman filter and optional map matching
     */
    private fun processLocation(location: Location): LocationUpdate? {
        totalUpdates++
        
        val rawLat = location.latitude
        val rawLon = location.longitude
        val accuracy = if (location.hasAccuracy()) location.accuracy else -1f
        val hasBearing = location.hasBearing()
        val bearing = if (hasBearing) location.bearing else 0f
        val provider = location.provider ?: "unknown"
        val timestamp = location.time
        
        Log.v(TAG, "[LOCATION_RAW] lat=$rawLat, lon=$rawLon, accuracy=${accuracy}m, " +
                "provider=$provider, bearing=$bearing, hasBearing=$hasBearing")

        val speedMps = resolveSpeedMps(location)
        val speedMph = speedMps * 2.23694f
        
        Log.v(TAG, "[LOCATION_SPEED] reported=${location.speed}m/s, resolved=${speedMps}m/s, " +
                "converted=${speedMph}mph")

        // Apply Kalman filter if enabled
        val filteredLocation = if (enableKalmanFilter) {
            Log.v(TAG, "[FILTER_APPLY] Applying Kalman filter")
            val result = kalmanFilter.process(
                lat = rawLat,
                lon = rawLon,
                accuracy = if (accuracy > 0) accuracy else 50f,
                speedMps = speedMps
            )
            if (result != null) {
                filteredUpdates++
                Log.d(TAG, "[FILTER_RESULT] Filter applied: (${result.latitude}, ${result.longitude}), " +
                        "confidence=${result.confidence}")
            } else {
                rejectedUpdates++
                Log.w(TAG, "[FILTER_REJECT] Location rejected by Kalman filter")
                if (totalUpdates % 50 == 0) logStats()
            }
            result
        } else {
            Log.v(TAG, "[FILTER_SKIP] Kalman filter disabled, using raw location")
            GpsKalmanFilter.FilteredLocation(
                latitude = rawLat,
                longitude = rawLon,
                isSmoothed = false,
                confidence = 1.0f
            )
        }

        if (filteredLocation == null) {
            return null
        }

        // Create point from filtered coordinates
        val point = Point.fromLngLat(filteredLocation.longitude, filteredLocation.latitude)
        
        // Log shift from raw to filtered
        val shiftDistance = calculateDistanceMeters(rawLat, rawLon, filteredLocation.latitude, filteredLocation.longitude)
        if (shiftDistance > 1.0) {
            Log.v(TAG, "[LOCATION_SHIFT] Shift distance: ${shiftDistance}m from raw to filtered")
        }

        Log.d(TAG, "[LOCATION_PROCESSED] lat=${filteredLocation.latitude}, lon=${filteredLocation.longitude}, " +
                "smoothed=${filteredLocation.isSmoothed}, confidence=${filteredLocation.confidence}, " +
                "speed=${speedMph}mph")

        return LocationUpdate(
            location = point,
            speed = speedMph,
            bearing = bearing,
            accuracy = if (accuracy > 0) accuracy else 0f,
            isSmoothed = filteredLocation.isSmoothed,
            confidence = filteredLocation.confidence
        )
    }
    
    /**
     * Calculate distance between two coordinates in meters
     */
    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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
        if (!hasLocationPermission()) {
            Log.w(TAG, "[LAST_KNOWN] Permission not granted")
            return null
        }

        Log.d(TAG, "[LAST_KNOWN] Requesting last known location from Fused provider")

        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location == null) {
                Log.w(TAG, "[LAST_KNOWN] Fused provider returned null, trying system providers")
                getSystemLastKnownLocation()
            } else {
                val age = System.currentTimeMillis() - location.time
                Log.d(TAG, "[LAST_KNOWN] Fused provider returned location: " +
                        "lat=${location.latitude}, lon=${location.longitude}, " +
                        "accuracy=${location.accuracy}m, age=${age}ms")
                Point.fromLngLat(location.longitude, location.latitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[LAST_KNOWN_ERROR] Error getting last known location: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    /**
     * Request a current high-accuracy location fix.
     */
    suspend fun getCurrentLocation(): Point? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "[CURRENT_LOC] Permission not granted")
            return null
        }

        Log.d(TAG, "[CURRENT_LOC] Requesting current location (timeout=5000ms)")

        return try {
            val highAccuracy = withTimeoutOrNull(5000) {
                requestCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)
            }
            if (highAccuracy != null) {
                Log.d(TAG, "[CURRENT_LOC] High accuracy location obtained")
                highAccuracy
            } else {
                Log.w(TAG, "[CURRENT_LOC] High accuracy timed out, trying balanced power mode")
                withTimeoutOrNull(5000) {
                    requestCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                }?.also {
                    Log.d(TAG, "[CURRENT_LOC] Balanced power location obtained")
                } ?: run {
                    Log.w(TAG, "[CURRENT_LOC] Balanced power mode also timed out")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[CURRENT_LOC_ERROR] Error getting current location: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    private suspend fun requestCurrentLocation(priority: Int): Point? {
        val priorityName = when (priority) {
            Priority.PRIORITY_HIGH_ACCURACY -> "HIGH_ACCURACY"
            Priority.PRIORITY_BALANCED_POWER_ACCURACY -> "BALANCED_POWER"
            Priority.PRIORITY_LOW_POWER -> "LOW_POWER"
            else -> "UNKNOWN($priority)"
        }
        
        Log.v(TAG, "[CURRENT_LOC_REQUEST] Requesting with priority=$priorityName")
        
        val tokenSource = CancellationTokenSource()
        val location = fusedLocationClient.getCurrentLocation(
            priority,
            tokenSource.token
        ).await()
        
        return if (location == null) {
            Log.w(TAG, "[CURRENT_LOC_REQUEST] Null location received for priority=$priorityName")
            null
        } else {
            Log.d(TAG, "[CURRENT_LOC_REQUEST] Success with priority=$priorityName: " +
                    "lat=${location.latitude}, lon=${location.longitude}, " +
                    "accuracy=${location.accuracy}m, provider=${location.provider}")
            Point.fromLngLat(location.longitude, location.latitude)
        }
    }

    /**
     * Start location updates as Flow
     */
    fun startLocationUpdates(): Flow<LocationUpdate> = callbackFlow {
        if (!hasLocationPermission()) {
            Log.e(TAG, "[PERMISSION_DENIED] Cannot start location updates - permission not granted")
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        Log.d(TAG, "[UPDATES_START] Starting location updates")
        Log.d(TAG, "[UPDATES_CONFIG] interval=${LOCATION_UPDATE_INTERVAL}ms, fastest=${FASTEST_UPDATE_INTERVAL}ms, priority=HIGH_ACCURACY")
        
        // Reset stats when starting new session
        totalUpdates = 0
        filteredUpdates = 0
        rejectedUpdates = 0

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMinUpdateDistanceMeters(0f)
            setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(true)
        }.build()

        var fusedUpdateCount = 0
        var systemUpdateCount = 0

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedUpdateCount++
                result.lastLocation?.let { location ->
                    Log.v(TAG, "[FUSED_UPDATE] #${fusedUpdateCount} received from provider=${location.provider}")
                    processLocation(location)?.let { update ->
                        Log.v(TAG, "[FUSED_UPDATE] Sending update #${fusedUpdateCount} to channel")
                        trySend(update)
                    }
                } ?: run {
                    Log.w(TAG, "[FUSED_UPDATE] #${fusedUpdateCount} received null location")
                }
            }
        }

        val systemListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                systemUpdateCount++
                Log.v(TAG, "[SYSTEM_UPDATE] #${systemUpdateCount} received from provider=${location.provider}")
                processLocation(location)?.let { update ->
                    Log.v(TAG, "[SYSTEM_UPDATE] Sending update #${systemUpdateCount} to channel")
                    trySend(update)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        Log.d(TAG, "[UPDATES_START] Fused location provider registered")

        requestSystemLocationUpdates(systemListener)

        awaitClose {
            Log.d(TAG, "[UPDATES_STOP] Stopping location updates")
            Log.d(TAG, "[UPDATES_STATS] Fused: $fusedUpdateCount, System: $systemUpdateCount, " +
                    "Processed: $totalUpdates, Filtered: $filteredUpdates, Rejected: $rejectedUpdates")
            fusedLocationClient.removeLocationUpdates(locationCallback)
            systemLocationManager.removeUpdates(systemListener)
            kalmanFilter.reset()
            Log.d(TAG, "[UPDATES_STOP] All location listeners removed")
        }
    }

    private fun getSystemLastKnownLocation(): Point? {
        Log.d(TAG, "[SYSTEM_LAST_KNOWN] Trying system providers")
        
        val gpsLocation = runCatching {
            systemLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
        }.onFailure { e ->
            Log.w(TAG, "[SYSTEM_LAST_KNOWN] Failed to get GPS location: ${e.message}")
        }.getOrNull()
        
        val networkLocation = runCatching {
            systemLocationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
        }.onFailure { e ->
            Log.w(TAG, "[SYSTEM_LAST_KNOWN] Failed to get NETWORK location: ${e.message}")
        }.getOrNull()
        
        gpsLocation?.let {
            val age = System.currentTimeMillis() - it.time
            Log.d(TAG, "[SYSTEM_LAST_KNOWN] GPS location available: lat=${it.latitude}, " +
                    "lon=${it.longitude}, accuracy=${it.accuracy}m, age=${age}ms")
        } ?: run {
            Log.w(TAG, "[SYSTEM_LAST_KNOWN] GPS location not available")
        }
        
        networkLocation?.let {
            val age = System.currentTimeMillis() - it.time
            Log.d(TAG, "[SYSTEM_LAST_KNOWN] NETWORK location available: lat=${it.latitude}, " +
                    "lon=${it.longitude}, accuracy=${it.accuracy}m, age=${age}ms")
        } ?: run {
            Log.w(TAG, "[SYSTEM_LAST_KNOWN] NETWORK location not available")
        }

        val bestLocation = listOfNotNull(gpsLocation, networkLocation).maxByOrNull { it.time }
        return bestLocation?.let {
            val age = System.currentTimeMillis() - it.time
            Log.d(TAG, "[SYSTEM_LAST_KNOWN] Selected best location from ${if (it === gpsLocation) "GPS" else "NETWORK"}: " +
                    "lat=${it.latitude}, lon=${it.longitude}, age=${age}ms")
            Point.fromLngLat(it.longitude, it.latitude)
        } ?: run {
            Log.w(TAG, "[SYSTEM_LAST_KNOWN] No location available from any system provider")
            null
        }
    }

    private fun requestSystemLocationUpdates(listener: LocationListener) {
        val providers = systemLocationManager.getProviders(true)
        Log.d(TAG, "[SYSTEM_PROVIDERS] Available: $providers")
        
        var registeredCount = 0
        
        runCatching {
            if (systemLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                Log.d(TAG, "[SYSTEM_REGISTER] Registering GPS provider")
                systemLocationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
                Log.d(TAG, "[SYSTEM_REGISTER] GPS provider registered successfully")
            } else {
                Log.w(TAG, "[SYSTEM_REGISTER] GPS provider is disabled")
            }
        }.onFailure { e ->
            Log.e(TAG, "[SYSTEM_REGISTER] Failed to register GPS provider: ${e.message}")
        }
        
        runCatching {
            if (systemLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                Log.d(TAG, "[SYSTEM_REGISTER] Registering NETWORK provider")
                systemLocationManager.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
                Log.d(TAG, "[SYSTEM_REGISTER] NETWORK provider registered successfully")
            } else {
                Log.w(TAG, "[SYSTEM_REGISTER] NETWORK provider is disabled")
            }
        }.onFailure { e ->
            Log.e(TAG, "[SYSTEM_REGISTER] Failed to register NETWORK provider: ${e.message}")
        }
        
        Log.d(TAG, "[SYSTEM_REGISTER] Total providers registered: $registeredCount/${providers.size}")
    }

    private fun resolveSpeedMps(location: Location): Float {
        val reported = if (location.hasSpeed()) location.speed else 0f
        val computed = computeSpeedFromDelta(location)
        val goodAccuracy = !location.hasAccuracy() || location.accuracy <= SPEED_ACCURACY_METERS
        val useComputed = goodAccuracy &&
                computed > 0f &&
                (reported <= 0.5f || kotlin.math.abs(reported - computed) > 3f)
        
        val resolved = if (useComputed) computed else reported
        
        if (useComputed) {
            Log.v(TAG, "[SPEED_RESOLVE] Using computed speed: ${computed}m/s (reported=${reported}m/s, diff=${kotlin.math.abs(reported - computed)}m/s)")
        } else {
            Log.v(TAG, "[SPEED_RESOLVE] Using reported speed: ${reported}m/s (computed=${computed}m/s)")
        }
        
        return resolved
    }

    private fun computeSpeedFromDelta(location: Location): Float {
        val previous = lastLocation
        val previousTimestamp = lastLocationTimestampNanos
        val currentTimestamp = resolveTimestampNanos(location)
        var speedMps = 0f

        if (previous != null && previousTimestamp != null && currentTimestamp > previousTimestamp) {
            val previousAccuracyOk = !previous.hasAccuracy() || previous.accuracy <= SPEED_ACCURACY_METERS
            val currentAccuracyOk = !location.hasAccuracy() || location.accuracy <= SPEED_ACCURACY_METERS
            
            if (!previousAccuracyOk || !currentAccuracyOk) {
                Log.v(TAG, "[SPEED_COMPUTE] Poor accuracy, cannot compute speed")
                lastLocation = location
                lastLocationTimestampNanos = currentTimestamp
                return 0f
            }
            
            val deltaSeconds = (currentTimestamp - previousTimestamp) / 1_000_000_000.0
            if (deltaSeconds > 0) {
                val distance = location.distanceTo(previous)
                speedMps = (distance / deltaSeconds).toFloat()
                Log.v(TAG, "[SPEED_COMPUTE] distance=${distance}m, time=${deltaSeconds}s, speed=${speedMps}m/s")
            }
        } else {
            Log.v(TAG, "[SPEED_COMPUTE] No previous location, cannot compute speed")
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
    val accuracy: Float,     // in meters
    val isSmoothed: Boolean = false,  // Whether Kalman filter was applied
    val confidence: Float = 1.0f       // Confidence in location (0.0 - 1.0)
)
