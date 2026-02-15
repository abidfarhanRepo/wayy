package com.wayy.map

import android.content.Context
import android.util.Log
import com.wayy.BuildConfig
import com.wayy.debug.DiagnosticLogger
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.nio.charset.Charset
import java.io.File

class OfflineMapManager(
    private val context: Context,
    private val diagnosticLogger: DiagnosticLogger? = null
) {
    private data class ResolvedStyle(
        val url: String,
        val isEmbedded: Boolean
    )

    private val offlineManager: OfflineManager = OfflineManager.getInstance(context)
    private val offlineDbFile = File(context.filesDir, "mbgl-offline.db")
    private val cacheStyleFile = File(context.cacheDir, "protomaps_style_runtime.json")
    private var isDownloading = false

    fun loadSummary(callback: (OfflineSummary) -> Unit) {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val count = offlineRegions?.size ?: 0
                val size = if (offlineDbFile.exists()) offlineDbFile.length() else 0L
                callback(OfflineSummary(count, size, isDownloading))
            }

            override fun onError(error: String) {
                val size = if (offlineDbFile.exists()) offlineDbFile.length() else 0L
                callback(OfflineSummary(0, size, isDownloading))
            }
        })
    }

    fun ensureRegion(
        center: LatLng,
        radiusKm: Double = 10.0,
        minZoom: Double = 12.0,
        maxZoom: Double = 18.0,
        tilejsonUrlOverride: String? = null,
        mapStyleUrlOverride: String? = null
    ) {
        if (isDownloading) return

        val resolvedStyle = resolveStyleUrl(tilejsonUrlOverride, mapStyleUrlOverride)

        if (resolvedStyle.isEmbedded) {
            diagnosticLogger?.log(tag = TAG, message = "Using embedded PMTiles - offline download not needed")
            return
        }

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (!offlineRegions.isNullOrEmpty()) {
                    diagnosticLogger?.log(tag = TAG, message = "Offline region already exists")
                    return
                }
                createRegion(center, radiusKm, minZoom, maxZoom, tilejsonUrlOverride, mapStyleUrlOverride)
            }

            override fun onError(error: String) {
                diagnosticLogger?.log(tag = TAG, message = "Offline list error", level = "ERROR", data = mapOf("error" to error))
                createRegion(center, radiusKm, minZoom, maxZoom, tilejsonUrlOverride, mapStyleUrlOverride)
            }
        })
    }

    private fun createRegion(
        center: LatLng,
        radiusKm: Double,
        minZoom: Double,
        maxZoom: Double,
        tilejsonUrlOverride: String?,
        mapStyleUrlOverride: String?
    ) {
        val bounds = buildBounds(center, radiusKm)
        val definition = OfflineTilePyramidRegionDefinition(
            resolveStyleUrl(tilejsonUrlOverride, mapStyleUrlOverride).url,
            bounds,
            minZoom,
            maxZoom,
            1f
        )
        val metadata = "WayyOffline".toByteArray(Charset.forName("UTF-8"))

        offlineManager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                isDownloading = true
                diagnosticLogger?.log(tag = TAG, message = "Offline download started")
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val completed = status.isComplete
                        val percentage = if (status.requiredResourceCount > 0) {
                            100.0 * status.completedResourceCount / status.requiredResourceCount
                        } else {
                            0.0
                        }
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "Offline progress",
                            data = mapOf(
                                "completed" to completed,
                                "percentage" to percentage,
                                "completedResources" to status.completedResourceCount,
                                "requiredResources" to status.requiredResourceCount
                            )
                        )
                        if (completed) {
                            isDownloading = false
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        isDownloading = false
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "Offline error",
                            level = "ERROR",
                            data = mapOf("reason" to error.message)
                        )
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        isDownloading = false
                        diagnosticLogger?.log(
                            tag = TAG,
                            message = "Offline limit exceeded",
                            level = "ERROR",
                            data = mapOf("limit" to limit)
                        )
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                isDownloading = false
                diagnosticLogger?.log(tag = TAG, message = "Offline create error", level = "ERROR", data = mapOf("error" to error))
                Log.e(TAG, "Offline create error: $error")
            }
        })
    }

    private fun buildBounds(center: LatLng, radiusKm: Double): LatLngBounds {
        val latOffset = radiusKm / 110.574
        val lngOffset = radiusKm / (111.320 * kotlin.math.cos(Math.toRadians(center.latitude)))
        val northeast = LatLng(center.latitude + latOffset, center.longitude + lngOffset)
        val southwest = LatLng(center.latitude - latOffset, center.longitude - lngOffset)
        return LatLngBounds.from(northeast.latitude, northeast.longitude, southwest.latitude, southwest.longitude)
    }

    private fun resolveStyleUrl(tilejsonUrlOverride: String?, mapStyleUrlOverride: String?): ResolvedStyle {
        val tilejsonUrl = tilejsonUrlOverride?.trim().orEmpty()
            .ifBlank { BuildConfig.PMTILES_TILEJSON_URL }

        if (tilejsonUrl.isNotBlank()) {
            return ResolvedStyle(buildStyleFromTemplate(tilejsonUrl), false)
        }

        val styleUrl = mapStyleUrlOverride?.trim().orEmpty()
            .ifBlank { BuildConfig.MAP_STYLE_URL }
        if (styleUrl.isNotBlank()) {
            return ResolvedStyle(styleUrl, styleUrl.startsWith("asset://") || styleUrl.contains("pmtiles://asset://"))
        }

        return ResolvedStyle(DEFAULT_STYLE_URL, false)
    }

    private fun buildStyleFromTemplate(sourceUrl: String): String {
        cacheStyleFile.parentFile?.mkdirs()
        val rawStyle = context.assets.open(PROTOMAPS_STYLE_ASSET)
            .bufferedReader()
            .use { it.readText() }
        cacheStyleFile.writeText(rawStyle.replace(TILEJSON_PLACEHOLDER, sourceUrl))
        return "file://${cacheStyleFile.absolutePath}"
    }

    companion object {
        private const val TAG = "WayyOffline"
        private const val PROTOMAPS_STYLE_ASSET = "protomaps_style.json"
        private const val TILEJSON_PLACEHOLDER = "__TILEJSON_URL__"
        private const val DEFAULT_STYLE_URL = "asset://osm_raster_style.json"
    }
}
data class OfflineSummary(
    val regionCount: Int,
    val dbSizeBytes: Long,
    val isDownloading: Boolean
)
