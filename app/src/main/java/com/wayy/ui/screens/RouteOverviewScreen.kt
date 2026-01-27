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
import com.wayy.ui.components.glass.GlassButton
import com.wayy.ui.components.glass.GlassCard
import com.wayy.ui.theme.WayyColors

/**
 * Route overview screen for searching and selecting destinations
 */
@Composable
fun RouteOverviewScreen(
    onDestinationSelected: (String) -> Unit = {},
    onRecentRouteClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

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

            Spacer(modifier = Modifier.height(16.dp))

            // Recent routes list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(getDemoRecentRoutes()) { route ->
                    RecentRouteCard(
                        name = route.name,
                        address = route.address,
                        distance = route.distance,
                        onClick = { onDestinationSelected(route.name) }
                    )
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

/**
 * Demo recent routes data
 */
private data class RecentRoute(
    val name: String,
    val address: String,
    val distance: String
)

private fun getDemoRecentRoutes(): List<RecentRoute> = listOf(
    RecentRoute("Home", "123 Main Street, San Francisco", "3.2 mi"),
    RecentRoute("Work", "456 Market Street, San Francisco", "8.5 mi"),
    RecentRoute("Gym", "789 Fitness Ave, San Francisco", "2.1 mi"),
    RecentRoute("Coffee Shop", "321 Brew Lane, San Francisco", "1.4 mi"),
    RecentRoute("Grocery Store", "654 Food Road, San Francisco", "4.7 mi")
)
