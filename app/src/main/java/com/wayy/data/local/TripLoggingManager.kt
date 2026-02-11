package com.wayy.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import org.maplibre.geojson.Point
import java.util.Locale

class TripLoggingManager(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        TripLoggingDatabase::class.java,
        "trip_logging.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.tripLoggingDao()

    fun generateTripId(
        startLat: Double,
        startLng: Double,
        endLat: Double?,
        endLng: Double?
    ): String {
        val timestamp = System.currentTimeMillis()
        val formattedStartLat = String.format(Locale.US, "%.5f", startLat)
        val formattedStartLng = String.format(Locale.US, "%.5f", startLng)
        val formattedEndLat = endLat?.let { String.format(Locale.US, "%.5f", it) } ?: "na"
        val formattedEndLng = endLng?.let { String.format(Locale.US, "%.5f", it) } ?: "na"
        return "trip_${timestamp}_${formattedStartLat}_${formattedStartLng}_${formattedEndLat}_${formattedEndLng}"
    }

    suspend fun startTrip(
        tripId: String,
        startLocation: Point,
        destination: Point?,
        destinationName: String?
    ) {
        val session = TripSessionEntity(
            tripId = tripId,
            startTime = System.currentTimeMillis(),
            startLat = startLocation.latitude(),
            startLng = startLocation.longitude(),
            destinationName = destinationName,
            destinationLat = destination?.latitude(),
            destinationLng = destination?.longitude()
        )
        dao.insertTrip(session)
        Log.d("WayyTrip", "Trip started id=$tripId")
    }

    suspend fun endTrip(tripId: String, endLocation: Point?) {
        dao.endTrip(
            tripId = tripId,
            endTime = System.currentTimeMillis(),
            endLat = endLocation?.latitude(),
            endLng = endLocation?.longitude()
        )
        Log.d("WayyTrip", "Trip ended id=$tripId")
    }

    suspend fun logGpsSample(
        tripId: String,
        location: Point,
        timestamp: Long,
        speedMps: Float,
        bearing: Float,
        accuracy: Float,
        streetName: String,
        stepIndex: Int,
        remainingDistanceMeters: Double,
        isNavigating: Boolean
    ) {
        dao.insertSample(
            GpsSampleEntity(
                tripId = tripId,
                timestamp = timestamp,
                lat = location.latitude(),
                lng = location.longitude(),
                speedMps = speedMps,
                bearing = bearing,
                accuracy = accuracy,
                streetName = streetName,
                stepIndex = stepIndex,
                remainingDistanceMeters = remainingDistanceMeters,
                isNavigating = isNavigating
            )
        )
    }

    suspend fun logStreetSegment(
        tripId: String,
        streetName: String,
        startTime: Long,
        endTime: Long,
        durationMs: Long,
        distanceMeters: Double,
        avgSpeedMps: Double,
        sampleCount: Int,
        startPoint: Point,
        endPoint: Point
    ) {
        dao.insertStreetSegment(
            StreetSegmentEntity(
                tripId = tripId,
                streetName = streetName,
                startTime = startTime,
                endTime = endTime,
                durationMs = durationMs,
                distanceMeters = distanceMeters,
                averageSpeedMps = avgSpeedMps,
                sampleCount = sampleCount,
                startLat = startPoint.latitude(),
                startLng = startPoint.longitude(),
                endLat = endPoint.latitude(),
                endLng = endPoint.longitude()
            )
        )
    }
}
