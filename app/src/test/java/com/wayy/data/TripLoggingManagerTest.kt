package com.wayy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wayy.data.local.TripLoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TripLoggingManagerTest {

    private lateinit var context: Context
    private lateinit var manager: TripLoggingManager

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        manager = TripLoggingManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bucketStart_roundsDown() {
        val bucket = TripLoggingManager.bucketStart(TripLoggingManager.TRAFFIC_BUCKET_MS + 1234)
        assertEquals(TripLoggingManager.TRAFFIC_BUCKET_MS, bucket)
    }

    @Test
    fun generateTripId_containsCoordinates() {
        val id = manager.generateTripId(1.23456, 2.34567, 3.45678, 4.56789)
        assertTrue(id.contains("1.23456"))
    }
}
