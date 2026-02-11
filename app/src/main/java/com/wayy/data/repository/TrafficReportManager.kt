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

data class TrafficReportItem(
    val id: String,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val severity: String,
    val timestamp: Long
)

object TrafficReportSerializer : Serializer<List<TrafficReportItem>> {
    override val defaultValue: List<TrafficReportItem> = emptyList()

    override suspend fun readFrom(input: InputStream): List<TrafficReportItem> {
        return try {
            val json = input.readBytes().decodeToString()
            Gson().fromJson(json, object : TypeToken<List<TrafficReportItem>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun writeTo(t: List<TrafficReportItem>, output: OutputStream) {
        val json = Gson().toJson(t)
        output.write(json.encodeToByteArray())
    }
}

val Context.trafficReportDataStore: DataStore<List<TrafficReportItem>> by dataStore(
    fileName = "traffic_reports.json",
    serializer = TrafficReportSerializer
)

class TrafficReportManager(private val context: Context) {

    companion object {
        private const val MAX_REPORTS = 200
    }

    val reports: Flow<List<TrafficReportItem>> = context.trafficReportDataStore.data

    val recentReports: Flow<List<TrafficReportItem>> = reports.map { items ->
        items.sortedByDescending { it.timestamp }.take(50)
    }

    suspend fun addReport(report: TrafficReportItem) {
        context.trafficReportDataStore.updateData { current ->
            val mutable = current.toMutableList()
            val existingIndex = mutable.indexOfFirst { it.id == report.id }
            if (existingIndex >= 0) {
                mutable[existingIndex] = report
            } else {
                mutable.add(0, report)
            }
            mutable.sortedByDescending { it.timestamp }.take(MAX_REPORTS)
        }
    }

    suspend fun clearReports() {
        context.trafficReportDataStore.updateData { emptyList() }
    }

    suspend fun getReportById(reportId: String): TrafficReportItem? {
        return context.trafficReportDataStore.data.map { items ->
            items.find { it.id == reportId }
        }.first()
    }

    fun generateReportId(lat: Double, lng: Double): String {
        return "${lat}_${lng}_${System.currentTimeMillis()}"
    }
}
