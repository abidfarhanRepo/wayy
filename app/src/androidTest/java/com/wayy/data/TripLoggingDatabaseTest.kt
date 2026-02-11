package com.wayy.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wayy.data.local.TripLoggingDatabase
import com.wayy.data.local.TripLoggingManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.Point

@RunWith(AndroidJUnit4::class)
class TripLoggingDatabaseTest {

    private lateinit var context: Context
    private lateinit var manager: TripLoggingManager
    private lateinit var db: TripLoggingDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("trip_logging.db")
        manager = TripLoggingManager(context)
        db = Room.databaseBuilder(context, TripLoggingDatabase::class.java, "trip_logging.db")
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("trip_logging.db")
    }

    @Test
    fun insertAndRetrieveTrip() = runBlocking {
        val start = Point.fromLngLat(51.5310, 25.2854)
        val end = Point.fromLngLat(51.5400, 25.2900)
        val tripId = manager.generateTripId(start.latitude(), start.longitude(), end.latitude(), end.longitude())
        manager.startTrip(tripId, start, end, "Dest")
        manager.endTrip(tripId, end)

        db.query("SELECT * FROM trip_sessions WHERE tripId = ?", arrayOf(tripId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }

    @Test
    fun insertGpsSamples() = runBlocking {
        val start = Point.fromLngLat(51.5310, 25.2854)
        val tripId = manager.generateTripId(start.latitude(), start.longitude(), null, null)
        manager.startTrip(tripId, start, null, null)

        repeat(3) { index ->
            manager.logGpsSample(
                tripId = tripId,
                location = start,
                timestamp = System.currentTimeMillis() + index,
                speedMps = 4f,
                bearing = 0f,
                accuracy = 5f,
                streetName = "Test Street",
                stepIndex = 0,
                remainingDistanceMeters = 100.0,
                isNavigating = true
            )
        }

        db.query("SELECT COUNT(*) FROM gps_samples WHERE tripId = ?", arrayOf(tripId)).use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }
    }

    @Test
    fun streetSegmentAggregation() = runBlocking {
        val start = Point.fromLngLat(51.5310, 25.2854)
        val end = Point.fromLngLat(51.5400, 25.2900)
        val tripId = manager.generateTripId(start.latitude(), start.longitude(), end.latitude(), end.longitude())
        val now = System.currentTimeMillis()

        manager.logStreetSegment(
            tripId = tripId,
            streetName = "Main St",
            startTime = now,
            endTime = now + 1000,
            durationMs = 1000,
            distanceMeters = 120.0,
            avgSpeedMps = 12.0,
            sampleCount = 3,
            startPoint = start,
            endPoint = end
        )
        manager.logStreetSegment(
            tripId = tripId,
            streetName = "Main St",
            startTime = now + 2000,
            endTime = now + 3000,
            durationMs = 1000,
            distanceMeters = 130.0,
            avgSpeedMps = 13.0,
            sampleCount = 3,
            startPoint = start,
            endPoint = end
        )

        db.query("SELECT COUNT(*) FROM traffic_stats WHERE streetName = ?", arrayOf("Main St")).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun trafficHistoryQuery() = runBlocking {
        val start = Point.fromLngLat(51.5310, 25.2854)
        val end = Point.fromLngLat(51.5400, 25.2900)
        val tripId = manager.generateTripId(start.latitude(), start.longitude(), end.latitude(), end.longitude())
        val bucket = TripLoggingManager.TRAFFIC_BUCKET_MS
        val now = System.currentTimeMillis()

        manager.logStreetSegment(
            tripId = tripId,
            streetName = "History St",
            startTime = now - bucket * 2,
            endTime = now - bucket * 2 + 1000,
            durationMs = 1000,
            distanceMeters = 120.0,
            avgSpeedMps = 12.0,
            sampleCount = 3,
            startPoint = start,
            endPoint = end
        )
        manager.logStreetSegment(
            tripId = tripId,
            streetName = "History St",
            startTime = now - bucket,
            endTime = now - bucket + 1000,
            durationMs = 1000,
            distanceMeters = 130.0,
            avgSpeedMps = 13.0,
            sampleCount = 3,
            startPoint = start,
            endPoint = end
        )

        val history = manager.getTrafficHistory("History St", now - bucket * 3, now)
        assertEquals(2, history.size)
    }
}
