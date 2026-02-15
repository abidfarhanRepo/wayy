package com.wayy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

data class RouteHistoryItem(
    val id: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val startName: String,
    val endName: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val timestamp: Long,
    val routeGeometry: String? = null
)

object RouteHistorySerializer : Serializer<List<RouteHistoryItem>> {
    override val defaultValue: List<RouteHistoryItem> = emptyList()

    override suspend fun readFrom(input: InputStream): List<RouteHistoryItem> {
        return try {
            val json = input.readBytes().decodeToString()
            Gson().fromJson(json, object : TypeToken<List<RouteHistoryItem>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun writeTo(t: List<RouteHistoryItem>, output: OutputStream) {
        val json = Gson().toJson(t)
        output.write(json.encodeToByteArray())
    }
}

val Context.routeHistoryDataStore: DataStore<List<RouteHistoryItem>> by dataStore(
    fileName = "route_history.json",
    serializer = RouteHistorySerializer
)

class RouteHistoryManager(private val context: Context) {

    companion object {
        private const val MAX_HISTORY_SIZE = 10
    }

    val routeHistory: Flow<List<RouteHistoryItem>> = context.routeHistoryDataStore.data

    val recentRoutes: Flow<List<RouteHistoryItem>> = routeHistory.map { routes ->
        routes.sortedByDescending { it.timestamp }.take(5)
    }

    suspend fun addRoute(route: RouteHistoryItem) {
        context.routeHistoryDataStore.updateData { currentHistory ->
            val mutableHistory = currentHistory.toMutableList()
            
            // Deduplicate by start/end coordinates (rounded to 4 decimals = ~11m accuracy)
            val existingIndex = mutableHistory.indexOfFirst { existing ->
                kotlin.math.abs(existing.startLat - route.startLat) < 0.0001 &&
                kotlin.math.abs(existing.startLng - route.startLng) < 0.0001 &&
                kotlin.math.abs(existing.endLat - route.endLat) < 0.0001 &&
                kotlin.math.abs(existing.endLng - route.endLng) < 0.0001
            }
            
            if (existingIndex >= 0) {
                // Update existing route with new timestamp
                mutableHistory[existingIndex] = route.copy(id = mutableHistory[existingIndex].id)
            } else {
                mutableHistory.add(0, route)
            }
            
            mutableHistory.sortedByDescending { it.timestamp }.take(MAX_HISTORY_SIZE)
        }
    }

    suspend fun removeRoute(routeId: String) {
        context.routeHistoryDataStore.updateData { currentHistory ->
            currentHistory.filterNot { it.id == routeId }
        }
    }

    suspend fun clearHistory() {
        context.routeHistoryDataStore.updateData { emptyList() }
    }

    suspend fun getRouteById(routeId: String): RouteHistoryItem? {
        return context.routeHistoryDataStore.data.map { routes ->
            routes.find { it.id == routeId }
        }.first()
    }

    fun generateRouteId(startLat: Double, startLng: Double, endLat: Double, endLng: Double): String {
        return "${startLat}_${startLng}_${endLat}_${endLng}_${System.currentTimeMillis()}"
    }
}
