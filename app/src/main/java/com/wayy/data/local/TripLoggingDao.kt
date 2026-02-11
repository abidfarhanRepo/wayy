package com.wayy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TripLoggingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(session: TripSessionEntity)

    @Query(
        "UPDATE trip_sessions SET endTime = :endTime, endLat = :endLat, endLng = :endLng " +
            "WHERE tripId = :tripId"
    )
    suspend fun endTrip(tripId: String, endTime: Long, endLat: Double?, endLng: Double?)

    @Insert
    suspend fun insertSample(sample: GpsSampleEntity)

    @Insert
    suspend fun insertStreetSegment(segment: StreetSegmentEntity)

    @Query(
        "SELECT * FROM street_segments WHERE startTime >= :startMs AND startTime <= :endMs " +
            "ORDER BY endTime DESC LIMIT :limit"
    )
    suspend fun getStreetSegments(
        startMs: Long,
        endMs: Long,
        limit: Int
    ): List<StreetSegmentEntity>

    @Query(
        "SELECT * FROM traffic_stats WHERE streetName = :streetName " +
            "AND bucketStartMs = :bucketStartMs LIMIT 1"
    )
    suspend fun getTrafficStat(streetName: String, bucketStartMs: Long): TrafficStatEntity?

    @Query(
        "SELECT * FROM traffic_stats WHERE streetName = :streetName " +
            "AND bucketStartMs >= :startMs AND bucketStartMs <= :endMs " +
            "ORDER BY bucketStartMs ASC"
    )
    suspend fun getTrafficHistory(
        streetName: String,
        startMs: Long,
        endMs: Long
    ): List<TrafficStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrafficStat(stat: TrafficStatEntity)
}
