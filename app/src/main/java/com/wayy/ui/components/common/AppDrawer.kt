package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors

@Composable
fun AppDrawer(
    onNavigateToRouteOverview: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSavedPlaces: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
        ) {
            DrawerHeader()

            Spacer(modifier = Modifier.height(16.dp))

            DrawerSection(title = "Navigate") {
                DrawerItem(
                    icon = Icons.Default.Search,
                    label = "Search destination",
                    onClick = {
                        onClose()
                        onNavigateToRouteOverview()
                    }
                )

                DrawerItem(
                    icon = Icons.Default.History,
                    label = "Recent routes",
                    onClick = {
                        onClose()
                        onNavigateToHistory()
                    }
                )

                DrawerItem(
                    icon = Icons.Default.Star,
                    label = "Saved places",
                    onClick = {
                        onClose()
                        onNavigateToSavedPlaces()
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            DrawerSection(title = "Settings") {
                DrawerItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = {
                        onClose()
                        onNavigateToSettings()
                    }
                )

                DrawerItem(
                    icon = Icons.Default.Info,
                    label = "About wayy",
                    onClick = {
                        onClose()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WayyColors.Surface)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WayyColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "wayy",
                    color = WayyColors.Primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Navigation",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DrawerSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = WayyColors.PrimaryMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WayyColors.Surface
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = WayyColors.PrimaryMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = WayyColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = WayyColors.PrimaryMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

private val Color = androidx.compose.ui.graphics.Color
