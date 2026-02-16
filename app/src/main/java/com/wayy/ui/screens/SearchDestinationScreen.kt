package com.wayy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.data.repository.PlaceResult
import com.wayy.ui.theme.WayyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDestinationScreen(
    onBack: () -> Unit,
    onDestinationSelected: (PlaceResult) -> Unit,
    onRecentRouteClick: (PlaceResult) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    val recentSearches = remember {
        listOf(
            PlaceResult(place_id = 1, lat = 25.2854, lon = 51.5310, display_name = "Doha, Qatar"),
            PlaceResult(place_id = 2, lat = 25.3212, lon = 51.5521, display_name = "Villaggio Mall"),
            PlaceResult(place_id = 3, lat = 25.3531, lon = 51.5318, display_name = "Katara Cultural Village")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.Background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WayyColors.Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WayyColors.Primary
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Where to?", color = WayyColors.PrimaryMuted) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WayyColors.Primary,
                    unfocusedTextColor = WayyColors.Primary,
                    focusedBorderColor = WayyColors.Accent,
                    unfocusedBorderColor = WayyColors.SurfaceVariant,
                    cursorColor = WayyColors.Accent
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WayyColors.Accent)
            }
        } else if (searchQuery.isBlank()) {
            // Show recent searches
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Recent",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                recentSearches.forEach { place ->
                    RecentSearchItem(
                        place = place,
                        onClick = { onDestinationSelected(place) }
                    )
                }
            }
        } else {
            // Show search results placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = WayyColors.PrimaryMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search for \"$searchQuery\"",
                        color = WayyColors.PrimaryMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchItem(
    place: PlaceResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = WayyColors.PrimaryMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = place.display_name,
            color = WayyColors.Primary,
            fontSize = 14.sp,
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
