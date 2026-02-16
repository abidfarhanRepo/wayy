package com.wayy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        // Core trip logging entities
        TripSessionEntity::class,
        GpsSampleEntity::class,
        StreetSegmentEntity::class,
        TrafficStatEntity::class,
        // Self-learning entities (Phase 1)
        UserPreferenceEntity::class,
        DestinationPatternEntity::class,
        TrafficModelEntity::class,
        RerouteDecisionEntity::class,
        DetectedAnomalyEntity::class,
        RouteChoiceEntity::class,
        LearnedSessionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TripLoggingDatabase : RoomDatabase() {
    abstract fun tripLoggingDao(): TripLoggingDao
    abstract fun learningDao(): LearningDao
}
