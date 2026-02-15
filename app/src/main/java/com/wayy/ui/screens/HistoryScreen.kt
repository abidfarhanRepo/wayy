package com.wayy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.wayy.data.repository.RouteHistoryItem
import com.wayy.ui.theme.WayyColors
import com.wayy.viewmodel.NavigationViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: NavigationViewModel,
    onBack: () -> Unit,
    onRouteClick: (RouteHistoryItem) -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }
    
    val recentRoutes by remember {
        mutableStateOf(
            listOf(
                RouteHistoryItem(
                    id = "1",
                    startLat = 25.2854,
                    startLng = 51.5310,
                    endLat = 25.3212,
                    endLng = 51.5521,
                    startName = "Current Location",
                    endName = "Doha Mall",
                    distanceMeters = 5200.0,
                    durationSeconds = 900.0,
                    timestamp = System.currentTimeMillis()
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WayyColors.Surface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = WayyColors.Primary
                )
            }
            Text(
                text = "History",
                color = WayyColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (recentRoutes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = WayyColors.PrimaryMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No recent routes",
                        color = WayyColors.PrimaryMuted,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentRoutes) { route ->
                    HistoryItem(
                        route = route,
                        onClick = { onRouteClick(route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    route: RouteHistoryItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val date = remember(route.timestamp) { dateFormat.format(Date(route.timestamp)) }
    val distance = remember(route.distanceMeters) {
        if (route.distanceMeters >= 1000) {
            String.format("%.1f km", route.distanceMeters / 1000)
        } else {
            "${route.distanceMeters.toInt()} m"
        }
    }
    val duration = remember(route.durationSeconds) {
        val mins = route.durationSeconds / 60
        "$mins min"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = WayyColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(WayyColors.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = WayyColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.endName,
                    color = WayyColors.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = route.startName,
                    color = WayyColors.PrimaryMuted,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$distance • $duration",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = date,
                    color = WayyColors.PrimaryMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
