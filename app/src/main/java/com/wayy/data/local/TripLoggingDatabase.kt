package com.wayy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripSessionEntity::class,
        GpsSampleEntity::class,
        StreetSegmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TripLoggingDatabase : RoomDatabase() {
    abstract fun tripLoggingDao(): TripLoggingDao
}
