package com.wayy.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            DrawerItem(
                icon = Icons.Default.Search,
                label = "Search",
                onClick = {
                    onClose()
                    onNavigateToRouteOverview()
                }
            )
            
            DrawerItem(
                icon = Icons.Default.History,
                label = "History",
                onClick = {
                    onClose()
                    onNavigateToHistory()
                }
            )
            
            DrawerItem(
                icon = Icons.Default.Star,
                label = "Saved Places",
                onClick = {
                    onClose()
                    onNavigateToSavedPlaces()
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            HorizontalDivider(color = WayyColors.SurfaceVariant)
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                label = "About",
                onClick = {
                    onClose()
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "wayy",
            color = WayyColors.Primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Navigation",
            color = WayyColors.PrimaryMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = WayyColors.PrimaryMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = WayyColors.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WayyColors.Surface)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = WayyColors.Primary
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = title,
            color = WayyColors.Primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (onSettingsClick != null) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = WayyColors.Primary
                )
            }
        }
    }
}
