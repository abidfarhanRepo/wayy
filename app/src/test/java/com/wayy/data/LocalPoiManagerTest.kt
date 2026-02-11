package com.wayy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.wayy.data.repository.LocalPoiItem
import com.wayy.data.repository.LocalPoiSerializer
import com.wayy.data.repository.LocalPoiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalPoiManagerTest {

    private lateinit var context: Context
    private lateinit var manager: LocalPoiManager

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        manager = LocalPoiManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addPoi_new_addsItem() = runTest {
        manager.clearPois()
        val poi = LocalPoiItem(
            id = "poi1",
            name = "Test",
            category = "gas",
            lat = 1.0,
            lng = 2.0,
            timestamp = System.currentTimeMillis()
        )
        manager.addPoi(poi)
        val items = manager.pois.first()
        assertEquals(1, items.size)
        assertEquals("poi1", items[0].id)
    }

    @Test
    fun addPoi_existing_updatesItem() = runTest {
        manager.clearPois()
        val poi = LocalPoiItem(
            id = "poi1",
            name = "Test",
            category = "gas",
            lat = 1.0,
            lng = 2.0,
            timestamp = System.currentTimeMillis()
        )
        manager.addPoi(poi)
        val updated = poi.copy(name = "Updated")
        manager.addPoi(updated)
        val items = manager.pois.first()
        assertEquals(1, items.size)
        assertEquals("Updated", items[0].name)
    }
}
