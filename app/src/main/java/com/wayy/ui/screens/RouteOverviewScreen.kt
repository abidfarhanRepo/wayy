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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wayy.data.repository.RouteHistoryItem
import com.wayy.data.repository.PlaceResult
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors
import com.wayy.navigation.NavigationUtils
import com.wayy.viewmodel.NavigationViewModel
import kotlinx.coroutines.delay

/**
 * Route overview screen for searching and selecting destinations
 */
@Composable
fun RouteOverviewScreen(
    viewModel: NavigationViewModel = viewModel(),
    onDestinationSelected: (PlaceResult) -> Unit = {},
    onRecentRouteClick: (RouteHistoryItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val recentRoutes = viewModel.recentRoutes?.collectAsState()?.value.orEmpty()
    val showSearchResults = searchQuery.trim().length >= 3

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length < 3) {
            viewModel.clearSearchResults()
            return@LaunchedEffect
        }
        delay(350)
        if (query == searchQuery.trim()) {
            viewModel.searchPlaces(query)
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

            Spacer(modifier = Modifier.height(32.dp))

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
                    text = if (showSearchResults) "Results" else "Recent Routes",
                    color = WayyColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

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
}

/**
 * Recent route card
 */
@Composable
fun RecentRouteCard(
    name: String,
    address: String,
    distance: String,
    onClick: () -> Unit
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
            Text(
                text = distance,
                color = WayyColors.PrimaryLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
