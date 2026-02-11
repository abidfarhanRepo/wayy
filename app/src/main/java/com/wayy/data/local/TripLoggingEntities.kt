package com.wayy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_sessions")
data class TripSessionEntity(
    @PrimaryKey val tripId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val destinationName: String? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null
)

@Entity(tableName = "gps_samples")
data class GpsSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val bearing: Float,
    val accuracy: Float,
    val streetName: String,
    val stepIndex: Int,
    val remainingDistanceMeters: Double,
    val isNavigating: Boolean
)

@Entity(tableName = "street_segments")
data class StreetSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val streetName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val distanceMeters: Double,
    val averageSpeedMps: Double,
    val sampleCount: Int,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double
)

@Entity(
    tableName = "traffic_stats",
    primaryKeys = ["streetName", "bucketStartMs"]
)
data class TrafficStatEntity(
    val streetName: String,
    val bucketStartMs: Long,
    val totalDistanceMeters: Double,
    val totalDurationMs: Long,
    val totalSampleCount: Int,
    val totalSegmentCount: Int,
    val averageSpeedMps: Double,
    val lastUpdated: Long
)
