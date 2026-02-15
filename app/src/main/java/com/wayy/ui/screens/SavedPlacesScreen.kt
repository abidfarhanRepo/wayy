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
import com.wayy.data.repository.LocalPoiItem
import com.wayy.ui.theme.WayyColors
import com.wayy.viewmodel.NavigationViewModel

@Composable
fun SavedPlacesScreen(
    viewModel: NavigationViewModel,
    onBack: () -> Unit,
    onPlaceClick: (LocalPoiItem) -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }
    
    val savedPlaces = remember {
        listOf(
            LocalPoiItem(
                id = "1",
                name = "Home",
                category = "home",
                lat = 25.2854,
                lng = 51.5310,
                timestamp = System.currentTimeMillis()
            ),
            LocalPoiItem(
                id = "2",
                name = "Work",
                category = "work",
                lat = 25.3212,
                lng = 51.5521,
                timestamp = System.currentTimeMillis()
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
                text = "Saved Places",
                color = WayyColors.Primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (savedPlaces.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = WayyColors.PrimaryMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved places",
                        color = WayyColors.PrimaryMuted,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Long press on map to add a place",
                        color = WayyColors.PrimaryMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedPlaces) { place ->
                    SavedPlaceItem(
                        place = place,
                        onClick = { onPlaceClick(place) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPlaceItem(
    place: LocalPoiItem,
    onClick: () -> Unit
) {
    val icon = when (place.category.lowercase()) {
        "home" -> Icons.Default.Home
        "work" -> Icons.Default.Work
        "gas" -> Icons.Default.LocalGasStation
        "food" -> Icons.Default.Restaurant
        "parking" -> Icons.Default.LocalParking
        else -> Icons.Default.Place
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = WayyColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = place.name,
                color = WayyColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = WayyColors.PrimaryMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
