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

data class LocalPoiItem(
    val id: String,
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long
)

object LocalPoiSerializer : Serializer<List<LocalPoiItem>> {
    override val defaultValue: List<LocalPoiItem> = emptyList()

    override suspend fun readFrom(input: InputStream): List<LocalPoiItem> {
        return try {
            val json = input.readBytes().decodeToString()
            Gson().fromJson(json, object : TypeToken<List<LocalPoiItem>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun writeTo(t: List<LocalPoiItem>, output: OutputStream) {
        val json = Gson().toJson(t)
        output.write(json.encodeToByteArray())
    }
}

val Context.localPoiDataStore: DataStore<List<LocalPoiItem>> by dataStore(
    fileName = "local_pois.json",
    serializer = LocalPoiSerializer
)

class LocalPoiManager(private val context: Context) {

    companion object {
        private const val MAX_POIS = 100
    }

    val pois: Flow<List<LocalPoiItem>> = context.localPoiDataStore.data

    val recentPois: Flow<List<LocalPoiItem>> = pois.map { items ->
        items.sortedByDescending { it.timestamp }.take(10)
    }

    suspend fun addPoi(poi: LocalPoiItem) {
        context.localPoiDataStore.updateData { current ->
            val mutable = current.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == poi.id }
            if (existingIndex >= 0) {
                mutable[existingIndex] = poi
            } else {
                mutable.add(0, poi)
            }
            mutable.sortedByDescending { it.timestamp }.take(MAX_POIS)
        }
    }

    suspend fun removePoi(poiId: String) {
        context.localPoiDataStore.updateData { current ->
            current.filterNot { it.id == poiId }
        }
    }

    suspend fun clearPois() {
        context.localPoiDataStore.updateData { emptyList() }
    }

    suspend fun getPoiById(poiId: String): LocalPoiItem? {
        return context.localPoiDataStore.data.map { items ->
            items.find { it.id == poiId }
        }.first()
    }

    fun generatePoiId(lat: Double, lng: Double): String {
        return "${lat}_${lng}_${System.currentTimeMillis()}"
    }
}
