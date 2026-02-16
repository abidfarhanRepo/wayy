package com.wayy.navigation

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Kalman filter for smoothing GPS coordinates.
 * Reduces jitter and noise in location updates.
 */
class GpsKalmanFilter {
    
    companion object {
        private const val TAG = "GpsKalmanFilter"
        private const val MIN_DISTANCE_METERS = 3.0 // Minimum distance to report update
        private const val MAX_JUMP_METERS = 50.0 // Reject jumps larger than this
        private const val DEFAULT_PROCESS_NOISE = 0.01 // Process noise (Q)
    }
    
    // Kalman filter state for latitude and longitude (1D filters)
    private var latState = KalmanState()
    private var lonState = KalmanState()
    
    // Last reported location for distance calculation
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    
    data class FilteredLocation(
        val latitude: Double,
        val longitude: Double,
        val isSmoothed: Boolean,
        val confidence: Float // 0.0 - 1.0
    )
    
    private data class KalmanState(
        var value: Double = 0.0,      // Current estimate (x)
        var error: Double = 1.0,      // Estimate error (P)
        var initialized: Boolean = false
    )
    
    /**
     * Process a new GPS coordinate through the Kalman filter.
     * 
     * @param lat Latitude from GPS
     * @param lon Longitude from GPS
     * @param accuracy Accuracy in meters (lower is better)
     * @param speedMps Speed in meters per second
     * @return Filtered location or null if rejected as outlier
     */
    fun process(
        lat: Double,
        lon: Double,
        accuracy: Float,
        speedMps: Float
    ): FilteredLocation? {
        
        // Skip updates with poor accuracy
        if (accuracy > 50f) {
            Log.d(TAG, "Skipping update due to poor accuracy: ${accuracy}m")
            return lastLat?.let { FilteredLocation(it, lastLon!!, false, 0.3f) }
        }
        
        // Check for unreasonable jump
        if (lastLat != null && lastLon != null) {
            val distance = calculateDistance(lastLat!!, lastLon!!, lat, lon)
            
            // Reject large jumps unless moving fast
            if (distance > MAX_JUMP_METERS && speedMps < 15f) {
                Log.w(TAG, "Rejecting large jump: ${distance}m (speed: ${speedMps}m/s)")
                return FilteredLocation(lastLat!!, lastLon!!, false, 0.2f)
            }
            
            // Skip small movements (reduces noise when stationary)
            if (distance < MIN_DISTANCE_METERS && speedMps < 1f) {
                return FilteredLocation(lastLat!!, lastLon!!, true, 0.9f)
            }
        }
        
        // Measurement noise based on accuracy (R)
        val measurementNoise = (accuracy * accuracy).toDouble().coerceAtLeast(1.0)
        
        // Process noise based on speed (faster movement = more process noise)
        val processNoise = DEFAULT_PROCESS_NOISE * (1.0 + speedMps * 0.1)
        
        // Apply Kalman filter to latitude and longitude independently
        val filteredLat = applyKalmanFilter(latState, lat, measurementNoise, processNoise)
        val filteredLon = applyKalmanFilter(lonState, lon, measurementNoise, processNoise)
        
        // Update last location
        lastLat = filteredLat
        lastLon = filteredLon
        
        // Calculate confidence based on filter error
        val confidence = calculateConfidence(latState.error, lonState.error, accuracy)
        
        Log.d(TAG, "Filtered: ($lat, $lon) -> ($filteredLat, $filteredLon), confidence=$confidence")
        
        return FilteredLocation(
            latitude = filteredLat,
            longitude = filteredLon,
            isSmoothed = true,
            confidence = confidence
        )
    }
    
    /**
     * Apply 1D Kalman filter update
     */
    private fun applyKalmanFilter(
        state: KalmanState,
        measurement: Double,
        measurementNoise: Double,
        processNoise: Double
    ): Double {
        if (!state.initialized) {
            // First measurement - initialize state
            state.value = measurement
            state.error = measurementNoise
            state.initialized = true
            return measurement
        }
        
        // Prediction step
        // x_pred = x (state doesn't change without velocity)
        // P_pred = P + Q (error increases by process noise)
        val predictedError = state.error + processNoise
        
        // Update step
        // K = P_pred / (P_pred + R) (Kalman gain)
        val kalmanGain = predictedError / (predictedError + measurementNoise)
        
        // x = x_pred + K * (z - x_pred) (update state)
        state.value = state.value + kalmanGain * (measurement - state.value)
        
        // P = (1 - K) * P_pred (update error)
        state.error = (1 - kalmanGain) * predictedError
        
        return state.value
    }
    
    /**
     * Calculate confidence score based on filter errors
     */
    private fun calculateConfidence(
        latError: Double,
        lonError: Double,
        accuracy: Float
    ): Float {
        val avgError = (latError + lonError) / 2.0
        val normalizedError = avgError / (accuracy.toDouble() + 1.0)
        return (1.0 / (1.0 + normalizedError)).toFloat().coerceIn(0.0f, 1.0f)
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
     * Reset the filter (call when GPS is disabled/re-enabled)
     */
    fun reset() {
        latState = KalmanState()
        lonState = KalmanState()
        lastLat = null
        lastLon = null
        Log.d(TAG, "Kalman filter reset")
    }
}