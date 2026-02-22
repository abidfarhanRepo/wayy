package com.wayy.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.data.learning.PreferenceLearningEngine
import com.wayy.data.learning.PreferenceLearningStats
import com.wayy.data.learning.PreferenceType
import com.wayy.data.learning.UserPreferenceProfile
import com.wayy.data.local.LearningSystemInitializer
import com.wayy.data.settings.LearningSettingsRepository
import com.wayy.ui.theme.WayyColors
import kotlinx.coroutines.launch

@Composable
fun PreferenceSettingsScreen(
    onBack: () -> Unit,
    onCalibrationClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize dependencies
    val database = remember { LearningSystemInitializer.getDatabase(context) }
    val learningDao = remember { database.learningDao() }
    val preferenceEngine = remember { PreferenceLearningEngine(learningDao) }
    val learningSettingsRepo = remember { LearningSettingsRepository(context) }
    
    // State
    var preferenceStats by remember { mutableStateOf<PreferenceLearningStats?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    
    // Load data
    LaunchedEffect(Unit) {
        preferenceStats = preferenceEngine.getLearningStatistics()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.Background)
    ) {
        PreferenceSettingsTopBar(
            onBack = onBack,
            onReset = { showResetDialog = true }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Learning Status Card
            preferenceStats?.let { stats ->
                LearningStatusCard(stats = stats)
                
                // Preference Visualization
                PreferenceVisualizationCard(profile = stats.profile)
                
                // Road Type Preferences
                RoadTypePreferencesCard(profile = stats.profile)
                
                // Learning Statistics
                LearningStatisticsCard(stats = stats)
            }
            
            // Actions
            PreferenceActionsCard(
                onCalibrationClick = { showCalibrationDialog = true },
                onResetClick = { showResetDialog = true }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Preferences?", color = WayyColors.Primary) },
            text = { 
                Text(
                    "This will reset all learned preferences to default values. " +
                    "Your route history will be preserved, but the app will need to learn your preferences again.",
                    color = WayyColors.PrimaryMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            preferenceEngine.resetToDefaults()
                            preferenceStats = preferenceEngine.getLearningStatistics()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = WayyColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = WayyColors.PrimaryMuted)
                }
            },
            containerColor = WayyColors.Surface
        )
    }
    
    // Quick Calibration Dialog
    if (showCalibrationDialog) {
        QuickCalibrationDialog(
            onDismiss = { showCalibrationDialog = false },
            onProfileSelected = { profile ->
                scope.launch {
                    preferenceEngine.setExplicitPreferences(profile)
                    preferenceStats = preferenceEngine.getLearningStatistics()
                    showCalibrationDialog = false
                }
            }
        )
    }
}

@Composable
private fun PreferenceSettingsTopBar(
    onBack: () -> Unit,
    onReset: () -> Unit
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
            text = "Route Preferences",
            color = WayyColors.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onReset) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                tint = WayyColors.PrimaryMuted
            )
        }
    }
}

@Composable
private fun LearningStatusCard(stats: PreferenceLearningStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WayyColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Learning Status",
                    color = WayyColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Confidence Badge
                val badgeColor = when {
                    stats.confidenceScore > 0.7f -> WayyColors.Success
                    stats.confidenceScore > 0.4f -> WayyColors.Warning
                    else -> WayyColors.Error
                }
                
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (stats.isReliable) "Reliable" else "Learning",
                        color = badgeColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            
            // Confidence Bar
            LinearProgressIndicator(
                progress = { stats.confidenceScore },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = WayyColors.Accent,
                trackColor = WayyColors.SurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${(stats.confidenceScore * 100).toInt()}%",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = "${stats.totalRoutesAnalyzed} routes analyzed",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 13.sp
                )
            }
            
            if (stats.isReliable) {
                Text(
                    text = "Dominant preference: ${stats.dominantPreference.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    color = WayyColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "Keep using the app to improve route suggestions. " +
                           "${10 - stats.totalRoutesAnalyzed.coerceAtMost(10)} more routes needed for reliable predictions.",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PreferenceVisualizationCard(profile: UserPreferenceProfile) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WayyColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Route Preferences",
                color = WayyColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            // Time Preference
            PreferenceBar(
                label = "Speed",
                value = profile.timeWeight,
                color = WayyColors.Accent,
                icon = Icons.Default.Schedule
            )
            
            // Distance Preference
            PreferenceBar(
                label = "Short Distance",
                value = profile.distanceWeight,
                color = WayyColors.Success,
                icon = Icons.Default.Straighten
            )
            
            // Simplicity Preference
            PreferenceBar(
                label = "Simplicity",
                value = profile.simplicityWeight,
                color = WayyColors.Warning,
                icon = Icons.Default.Navigation
            )
            
            // Scenic Preference
            PreferenceBar(
                label = "Scenic",
                value = profile.scenicWeight,
                color = Color(0xFF8B5CF6),
                icon = Icons.Default.Landscape
            )
        }
    }
}

@Composable
private fun PreferenceBar(
    label: String,
    value: Float,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 1000),
        label = "preference_animation"
    )
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    color = WayyColors.Primary,
                    fontSize = 14.sp
                )
            }
            Text(
                text = "${(animatedValue * 100).toInt()}%",
                color = WayyColors.PrimaryMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        LinearProgressIndicator(
            progress = { animatedValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = WayyColors.SurfaceVariant
        )
    }
}

@Composable
private fun RoadTypePreferencesCard(profile: UserPreferenceProfile) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WayyColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Road Type Preferences",
                color = WayyColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            // Highway preference
            RoadTypePreferenceRow(
                label = "Highways",
                value = profile.highwayPreference,
                icon = Icons.Default.LocalFireDepartment
            )
            
            // Arterial preference
            RoadTypePreferenceRow(
                label = "Main Roads",
                value = profile.arterialPreference,
                icon = Icons.Default.DriveEta
            )
            
            // Residential preference
            RoadTypePreferenceRow(
                label = "Residential",
                value = profile.residentialPreference,
                icon = Icons.Default.Home
            )
        }
    }
}

@Composable
private fun RoadTypePreferenceRow(
    label: String,
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val preferenceText = when {
        value > 0.3f -> "Prefer"
        value < -0.3f -> "Avoid"
        else -> "Neutral"
    }
    
    val preferenceColor = when {
        value > 0.3f -> WayyColors.Success
        value < -0.3f -> WayyColors.Error
        else -> WayyColors.PrimaryMuted
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WayyColors.PrimaryMuted,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = WayyColors.Primary,
                fontSize = 14.sp
            )
        }
        
        Surface(
            color = preferenceColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = preferenceText,
                color = preferenceColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun LearningStatisticsCard(stats: PreferenceLearningStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WayyColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Learning Statistics",
                color = WayyColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = stats.totalRoutesAnalyzed.toString(),
                    label = "Routes\nAnalyzed"
                )
                StatItem(
                    value = stats.recentRouteChoices.toString(),
                    label = "Recent\nChoices"
                )
                StatItem(
                    value = "${(stats.confidenceScore * 100).toInt()}%",
                    label = "Confidence"
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = WayyColors.Primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = WayyColors.PrimaryMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun PreferenceActionsCard(
    onCalibrationClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WayyColors.Surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actions",
                color = WayyColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            // Quick Calibration Button
            PreferenceActionButton(
                icon = Icons.Default.Psychology,
                title = "Quick Calibration",
                subtitle = "Set your preferences manually",
                onClick = onCalibrationClick
            )
            
            // Reset Button
            PreferenceActionButton(
                icon = Icons.Default.Refresh,
                title = "Reset Preferences",
                subtitle = "Clear all learned preferences",
                onClick = onResetClick,
                isDestructive = true
            )
        }
    }
}

@Composable
private fun PreferenceActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val tintColor = if (isDestructive) WayyColors.Error else WayyColors.Accent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = tintColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isDestructive) WayyColors.Error else WayyColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = WayyColors.PrimaryMuted,
                fontSize = 12.sp
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = WayyColors.PrimaryMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun QuickCalibrationDialog(
    onDismiss: () -> Unit,
    onProfileSelected: (UserPreferenceProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Calibration", color = WayyColors.Primary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose your preferred route style:",
                    color = WayyColors.PrimaryMuted,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Fastest option
                CalibrationOption(
                    title = "Fastest Route",
                    description = "Prioritize speed over everything",
                    icon = Icons.Default.Bolt,
                    color = WayyColors.Accent,
                    onClick = { onProfileSelected(UserPreferenceProfile.fastest()) }
                )
                
                // Simplest option
                CalibrationOption(
                    title = "Simplest Route",
                    description = "Fewer turns and easier navigation",
                    icon = Icons.Default.Navigation,
                    color = WayyColors.Warning,
                    onClick = { onProfileSelected(UserPreferenceProfile.simplest()) }
                )
                
                // Balanced option
                CalibrationOption(
                    title = "Balanced",
                    description = "Good balance of speed and simplicity",
                    icon = Icons.Default.Balance,
                    color = WayyColors.Success,
                    onClick = { onProfileSelected(UserPreferenceProfile.balanced()) }
                )
                
                // Scenic option
                CalibrationOption(
                    title = "Scenic Route",
                    description = "Take the scenic path when possible",
                    icon = Icons.Default.Landscape,
                    color = Color(0xFF8B5CF6),
                    onClick = { onProfileSelected(UserPreferenceProfile.scenic()) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WayyColors.PrimaryMuted)
            }
        },
        containerColor = WayyColors.Surface
    )
}

@Composable
private fun CalibrationOption(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = WayyColors.SurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WayyColors.Primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = WayyColors.PrimaryMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}
