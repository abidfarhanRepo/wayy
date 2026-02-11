package com.wayy.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wayy.data.repository.TrafficReportItem
import com.wayy.data.repository.TrafficReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
class TrafficReportManagerTest {

    private lateinit var context: Context
    private lateinit var manager: TrafficReportManager

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        manager = TrafficReportManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addReport_new_addsItem() = runTest {
        manager.clearReports()
        val report = TrafficReportItem(
            id = "r1",
            lat = 1.0,
            lng = 2.0,
            speedMps = 3f,
            severity = "heavy",
            timestamp = System.currentTimeMillis()
        )
        manager.addReport(report)
        val items = manager.reports.first()
        assertEquals(1, items.size)
    }

    @Test
    fun addReport_existing_updatesItem() = runTest {
        manager.clearReports()
        val report = TrafficReportItem(
            id = "r1",
            lat = 1.0,
            lng = 2.0,
            speedMps = 3f,
            severity = "heavy",
            timestamp = System.currentTimeMillis()
        )
        manager.addReport(report)
        val updated = report.copy(severity = "light")
        manager.addReport(updated)
        val items = manager.reports.first()
        assertEquals(1, items.size)
        assertEquals("light", items[0].severity)
    }

    @Test
    fun pruneReports_expired_removed() = runTest {
        manager.clearReports()
        val expiredTimestamp = System.currentTimeMillis() - (3 * 60 * 60 * 1000L)
        val report = TrafficReportItem(
            id = "r2",
            lat = 1.0,
            lng = 2.0,
            speedMps = 3f,
            severity = "heavy",
            timestamp = expiredTimestamp
        )
        manager.addReport(report)
        val items = manager.recentReports.first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun pruneReports_recent_kept() = runTest {
        manager.clearReports()
        val recentTimestamp = System.currentTimeMillis() - (30 * 60 * 1000L)
        val report = TrafficReportItem(
            id = "r3",
            lat = 1.0,
            lng = 2.0,
            speedMps = 3f,
            severity = "heavy",
            timestamp = recentTimestamp
        )
        manager.addReport(report)
        val items = manager.recentReports.first()
        assertEquals(1, items.size)
    }
}
