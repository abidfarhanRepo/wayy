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
}
