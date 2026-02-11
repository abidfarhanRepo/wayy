package com.wayy.viewmodel

import com.wayy.data.repository.RouteRepository
import com.wayy.data.repository.TrafficReportItem
import com.wayy.data.repository.TrafficReportManager
import com.wayy.fixtures.TestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.geojson.Point
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startNavigation_noLocation_setsError() = runTest {
        val repo = mock<RouteRepository>()
        val viewModel = NavigationViewModel(routeRepository = repo)

        viewModel.startNavigation(Point.fromLngLat(1.0, 1.0), "Dest")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.navigationState is NavigationState.Idle)
        assertEquals("Location not available. Please enable GPS.", state.error)
    }

    @Test
    fun startNavigation_success_setsNavigating() = runTest {
        val repo = mock<RouteRepository>()
        whenever(repo.getRoute(any(), any())).thenReturn(Result.success(TestFixtures.createTestRoute()))
        val viewModel = NavigationViewModel(routeRepository = repo)

        viewModel.updateLocation(TestFixtures.TEST_POINT_1, speed = 0f, bearing = 0f, accuracy = 0f)
        viewModel.startNavigation(TestFixtures.TEST_POINT_2, "Dest")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isNavigating)
        assertTrue(state.navigationState is NavigationState.Navigating)
    }

    @Test
    fun determineActivationReason_exit() {
        val viewModel = NavigationViewModel()
        val method = NavigationViewModel::class.java.getDeclaredMethod(
            "determineActivationReason",
            String::class.java,
            Double::class.javaPrimitiveType
        )
        method.isAccessible = true
        val result = method.invoke(viewModel, "off ramp", 400.0) as ActivationReason?
        assertEquals(ActivationReason.APPROACHING_EXIT, result)
    }

    @Test
    fun determineActivationReason_turn() {
        val viewModel = NavigationViewModel()
        val method = NavigationViewModel::class.java.getDeclaredMethod(
            "determineActivationReason",
            String::class.java,
            Double::class.javaPrimitiveType
        )
        method.isAccessible = true
        val result = method.invoke(viewModel, "turn", 150.0) as ActivationReason?
        assertEquals(ActivationReason.APPROACHING_TURN, result)
    }

    @Test
    fun determineActivationReason_far() {
        val viewModel = NavigationViewModel()
        val method = NavigationViewModel::class.java.getDeclaredMethod(
            "determineActivationReason",
            String::class.java,
            Double::class.javaPrimitiveType
        )
        method.isAccessible = true
        val result = method.invoke(viewModel, "turn", 500.0) as ActivationReason?
        assertEquals(null, result)
    }

    @Test
    fun reportTraffic_autoSeverity() = runTest {
        val trafficManager = mock<TrafficReportManager>()
        whenever(trafficManager.generateReportId(any(), any())).thenReturn("id1")
        val viewModel = NavigationViewModel(routeRepository = mock(), trafficReportManager = trafficManager)

        viewModel.updateLocation(TestFixtures.TEST_POINT_1, speed = 0f, bearing = 0f, accuracy = 0f)
        viewModel.reportTraffic("")

        viewModel.updateLocation(TestFixtures.TEST_POINT_1, speed = 33.6f, bearing = 0f, accuracy = 0f)
        viewModel.reportTraffic("")

        advanceUntilIdle()

        val captor = argumentCaptor<TrafficReportItem>()
        verify(trafficManager, times(2)).addReport(captor.capture())
        assertEquals("heavy", captor.allValues[0].severity)
        assertEquals("light", captor.allValues[1].severity)
    }

    @Test
    fun calculateEta_withTraffic() {
        val viewModel = NavigationViewModel()
        val route = TestFixtures.createTestRoute()
        val method = NavigationViewModel::class.java.getDeclaredMethod(
            "calculateEtaSeconds",
            com.wayy.data.model.Route::class.java,
            Double::class.javaPrimitiveType,
            Float::class.javaPrimitiveType,
            Double::class.javaObjectType
        )
        method.isAccessible = true
        val eta = method.invoke(viewModel, route, 1000.0, 0f, 10.0) as Double
        assertEquals(100.0, eta, 0.01)
    }
}
