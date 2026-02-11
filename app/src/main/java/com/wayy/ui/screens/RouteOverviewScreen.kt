package com.wayy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.rememberDismissState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wayy.data.repository.RouteHistoryItem
import com.wayy.data.repository.LocalPoiItem
import com.wayy.data.repository.PlaceResult
import com.wayy.debug.DiagnosticLogger
import com.wayy.debug.ExportBundleManager
import com.wayy.map.OfflineMapManager
import com.wayy.map.OfflineSummary
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors
import com.wayy.navigation.NavigationUtils
import com.wayy.viewmodel.NavigationViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.geojson.Point
import java.util.Locale

/**
 * Route overview screen for searching and selecting destinations
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun RouteOverviewScreen(
    viewModel: NavigationViewModel = viewModel(),
    onDestinationSelected: (PlaceResult) -> Unit = {},
    onRecentRouteClick: (RouteHistoryItem) -> Unit = {},
    onPoiSelected: (LocalPoiItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var poiName by remember { mutableStateOf("") }
    var poiCategory by remember { mutableStateOf("general") }
    var selectedCategory by remember { mutableStateOf("all") }
    var activeTab by remember { mutableStateOf(RouteOverviewTab.SEARCH) }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val localPois = viewModel.localPois?.collectAsState()?.value.orEmpty()
    val recentRoutes = viewModel.recentRoutes?.collectAsState()?.value.orEmpty()
    val currentLocation = viewModel.uiState.collectAsState().value.currentLocation
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportManager = remember { ExportBundleManager(context, DiagnosticLogger(context)) }
    val offlineMapManager = remember { OfflineMapManager(context, DiagnosticLogger(context)) }
    var offlineSummary by remember { mutableStateOf<OfflineSummary?>(null) }
    var offlineRadius by remember { mutableStateOf(12.0) }
    val showSearchResults = searchQuery.trim().length >= 3
    val filteredPois = localPois.filter { poi ->
        selectedCategory == "all" || poi.category.lowercase() == selectedCategory
    }
    val sortedPois = if (currentLocation != null) {
        filteredPois.map { poi ->
            val distanceMeters = NavigationUtils.calculateDistanceMeters(
                currentLocation,
                Point.fromLngLat(poi.lng, poi.lat)
            )
            PoiDistance(poi, distanceMeters)
        }.sortedBy { it.distanceMeters }
    } else {
        filteredPois.map { poi -> PoiDistance(poi, null) }
            .sortedByDescending { it.poi.timestamp }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length < 3) {
            viewModel.clearSearchResults()
            return@LaunchedEffect
        }
        delay(350)
        if (query == searchQuery.trim()) {
            viewModel.searchPlaces(query, currentLocation)
        }
    }

    LaunchedEffect(Unit) {
        offlineMapManager.loadSummary { summary ->
            offlineSummary = summary
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.BgPrimary)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Search header
            Text(
                text = "Where to?",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Search bar
            GlassCard(modifier = Modifier.fillMaxWidth(0.9f)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    placeholder = { Text("Search destination...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WayyColors.PrimaryLime
                        )
                    },
                    singleLine = true
                )
            }

            if (isSearching) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = WayyColors.PrimaryLime)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTab == RouteOverviewTab.SEARCH,
                    onClick = { activeTab = RouteOverviewTab.SEARCH },
                    label = { Text("Search") }
                )
                FilterChip(
                    selected = activeTab == RouteOverviewTab.SETTINGS,
                    onClick = { activeTab = RouteOverviewTab.SETTINGS },
                    label = { Text("Settings") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeTab == RouteOverviewTab.SETTINGS) {
                GlassCard(modifier = Modifier.fillMaxWidth(0.9f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Offline Maps",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        val summary = offlineSummary
                        val summaryText = if (summary == null) {
                            "Checking offline data..."
                        } else {
                            val regions = if (summary.regionCount == 0) {
                                "No areas saved"
                            } else {
                                "${summary.regionCount} area${if (summary.regionCount == 1) "" else "s"} saved"
                            }
                            val status = if (summary.isDownloading) "Downloading" else "Idle"
                            "$regions • ${formatBytes(summary.dbSizeBytes)} • $status"
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = summaryText,
                            color = WayyColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5.0, 12.0, 20.0).forEach { radius ->
                                FilterChip(
                                    selected = offlineRadius == radius,
                                    onClick = { offlineRadius = radius },
                                    label = { Text("${radius.toInt()} km") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val location = currentLocation
                                if (location == null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Location required for offline download")
                                    }
                                } else {
                                    offlineMapManager.ensureRegion(
                                        center = org.maplibre.android.geometry.LatLng(
                                            location.latitude(),
                                            location.longitude()
                                        ),
                                        radiusKm = offlineRadius,
                                        minZoom = 12.0,
                                        maxZoom = 19.0
                                    )
                                    offlineMapManager.loadSummary { summary ->
                                        offlineSummary = summary
                                    }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Offline download started")
                                    }
                                }
                            },
                            enabled = currentLocation != null
                        ) {
                            Text("Download Offline Area")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(0.9f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Export Capture + Logs",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val exportFile = exportManager.createExportBundle()
                                    if (exportFile == null) {
                                        snackbarHostState.showSnackbar("Nothing to export yet")
                                        return@launch
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        exportFile
                                    )
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(shareIntent, "Share Wayy export")
                                    )
                                }
                            }
                        ) {
                            Text("Export Bundle")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (activeTab == RouteOverviewTab.SEARCH) {
                // Recent routes section
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showSearchResults) Icons.Default.Search else Icons.Default.History,
                        contentDescription = null,
                        tint = WayyColors.PrimaryLime
                    )
                    Text(
                        text = if (showSearchResults) "Results" else "Local POIs",
                        color = WayyColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!showSearchResults) {
                    GlassCard(modifier = Modifier.fillMaxWidth(0.9f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = poiName,
                            onValueChange = { poiName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("POI name") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Category",
                            color = WayyColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(poiCategoryOptions) { option ->
                                FilterChip(
                                    selected = poiCategory == option.id,
                                    onClick = { poiCategory = option.id },
                                    label = { Text(option.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = option.icon,
                                            contentDescription = null,
                                            tint = option.color
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.addLocalPoi(poiName.trim(), poiCategory.trim())
                                poiName = ""
                                poiCategory = "general"
                            },
                            enabled = poiName.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save POI")
                        }
                    }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(poiCategoryFilters) { option ->
                            FilterChip(
                                selected = selectedCategory == option.id,
                                onClick = { selectedCategory = option.id },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = option.color
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

            searchError?.let { message ->
                Text(
                    text = message,
                    color = WayyColors.Error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Recent routes list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showSearchResults) {
                    if (!isSearching && searchResults.isEmpty() && searchError == null) {
                        item {
                            Text(
                                text = "No results found",
                                color = WayyColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(searchResults) { place ->
                            val parts = place.display_name.split(",")
                            val name = parts.firstOrNull()?.trim().orEmpty().ifEmpty { place.display_name }
                            val address = parts.drop(1).joinToString(",").trim()
                            RecentRouteCard(
                                name = name,
                                address = address.ifEmpty { place.display_name },
                                distance = "Select",
                                onClick = { onDestinationSelected(place) }
                            )
                        }
                    }
                } else {
                    if (sortedPois.isEmpty()) {
                        item {
                            Text(
                                text = "No local POIs yet",
                                color = WayyColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(sortedPois, key = { it.poi.id }) { entry ->
                            val poi = entry.poi
                            val distanceText = entry.distanceMeters?.let {
                                NavigationUtils.formatDistance(it)
                            } ?: "Select"
                            val dismissState = rememberDismissState(
                                confirmStateChange = { value ->
                                    if (value == DismissValue.DismissedToEnd ||
                                        value == DismissValue.DismissedToStart
                                    ) {
                                        viewModel.removeLocalPoi(poi.id)
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                            SwipeToDismiss(
                                state = dismissState,
                                directions = setOf(
                                    DismissDirection.EndToStart,
                                    DismissDirection.StartToEnd
                                ),
                                background = {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(80.dp)
                                            .background(WayyColors.Error.copy(alpha = 0.2f))
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = WayyColors.Error
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = WayyColors.Error
                                        )
                                    }
                                },
                                dismissContent = {
                                    RecentRouteCard(
                                        name = poi.name,
                                        address = poiCategoryLabel(poi.category),
                                        distance = distanceText,
                                        onClick = { onPoiSelected(poi) },
                                        leadingIcon = poiCategoryIcon(poi.category),
                                        accentColor = poiCategoryColor(poi.category)
                                    )
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = WayyColors.PrimaryLime
                            )
                            Text(
                                text = "Recent Routes",
                                color = WayyColors.TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (recentRoutes.isEmpty()) {
                        item {
                            Text(
                                text = "No recent routes yet",
                                color = WayyColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(recentRoutes) { route ->
                            val parts = route.endName.split(",")
                            val name = parts.firstOrNull()?.trim().orEmpty().ifEmpty { route.endName }
                            val address = parts.drop(1).joinToString(",").trim()
                            RecentRouteCard(
                                name = name,
                                address = address.ifEmpty { route.startName },
                                distance = NavigationUtils.formatDistance(route.distanceMeters),
                                onClick = { onRecentRouteClick(route) }
                            )
                        }
                    }
                }
            }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/**
 * Recent route card
 */
@Composable
fun RecentRouteCard(
    name: String,
    address: String,
    distance: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    accentColor: Color = WayyColors.PrimaryLime
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(80.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = WayyColors.GlassLight
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            WayyColors.GlassBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = address,
                        color = WayyColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
            Text(
                text = distance,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class PoiDistance(
    val poi: LocalPoiItem,
    val distanceMeters: Double?
)

private enum class RouteOverviewTab {
    SEARCH,
    SETTINGS
}

private data class PoiCategoryOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)

private val poiCategoryOptions = listOf(
    PoiCategoryOption("gas", "Gas", Icons.Default.LocalGasStation, WayyColors.PrimaryOrange),
    PoiCategoryOption("food", "Food", Icons.Default.Restaurant, WayyColors.PrimaryLime),
    PoiCategoryOption("parking", "Parking", Icons.Default.LocalParking, WayyColors.PrimaryCyan),
    PoiCategoryOption("lodging", "Lodging", Icons.Default.Hotel, WayyColors.PrimaryPurple),
    PoiCategoryOption("general", "General", Icons.Default.Place, WayyColors.Info)
)

private val poiCategoryFilters = listOf(
    PoiCategoryOption("all", "All", Icons.Default.Place, WayyColors.TextSecondary)
) + poiCategoryOptions

private fun poiCategoryLabel(category: String): String {
    return poiCategoryOptions.firstOrNull { it.id == category.lowercase() }?.label ?: "General"
}

private fun poiCategoryIcon(category: String): ImageVector {
    return poiCategoryOptions.firstOrNull { it.id == category.lowercase() }?.icon
        ?: Icons.Default.Place
}

private fun poiCategoryColor(category: String): Color {
    return poiCategoryOptions.firstOrNull { it.id == category.lowercase() }?.color
        ?: WayyColors.Info
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
