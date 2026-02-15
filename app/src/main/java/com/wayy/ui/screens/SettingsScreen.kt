package com.wayy.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.data.settings.MapSettings
import com.wayy.data.settings.MapSettingsRepository
import com.wayy.data.settings.MlSettings
import com.wayy.data.settings.MlSettingsRepository
import androidx.activity.compose.BackHandler
import com.wayy.data.settings.DEFAULT_LANE_MODEL_PATH
import com.wayy.data.settings.DEFAULT_MODEL_PATH
import com.wayy.ui.theme.WayyColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapSettingsRepository = remember { MapSettingsRepository(context) }
    val mapSettings by mapSettingsRepository.settingsFlow.collectAsState(initial = MapSettings())
    val mlSettingsRepository = remember { MlSettingsRepository(context) }
    val mlSettings by mlSettingsRepository.settingsFlow.collectAsState(initial = MlSettings())

    var tilejsonInput by remember { mutableStateOf(mapSettings.tilejsonUrl) }
    var styleUrlInput by remember { mutableStateOf(mapSettings.mapStyleUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WayyColors.Background)
    ) {
        SettingsTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "Map") {
                SettingsTextField(
                    value = tilejsonInput,
                    onValueChange = { tilejsonInput = it },
                    label = "TileJSON URL",
                    placeholder = "Override map tiles"
                )

                SettingsTextField(
                    value = styleUrlInput,
                    onValueChange = { styleUrlInput = it },
                    label = "Map Style URL",
                    placeholder = "Override map style"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                mapSettingsRepository.setTilejsonUrl(tilejsonInput)
                                mapSettingsRepository.setMapStyleUrl(styleUrlInput)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WayyColors.Accent
                        )
                    ) {
                        Text("Apply")
                    }

                    OutlinedButton(
                        onClick = {
                            tilejsonInput = ""
                            styleUrlInput = ""
                            scope.launch {
                                mapSettingsRepository.clearTilejsonUrl()
                                mapSettingsRepository.clearMapStyleUrl()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WayyColors.PrimaryMuted
                        )
                    ) {
                        Text("Clear")
                    }
                }
            }

            SettingsSection(title = "Navigation") {
                SwitchSetting(
                    title = "Voice Guidance",
                    subtitle = "Audio instructions for turns",
                    checked = true,
                    onCheckedChange = { }
                )

                SwitchSetting(
                    title = "Speed Alerts",
                    subtitle = "Alert when exceeding speed limit",
                    checked = true,
                    onCheckedChange = { }
                )

                SwitchSetting(
                    title = "Camera Alerts",
                    subtitle = "Warn about speed cameras",
                    checked = false,
                    onCheckedChange = { }
                )
            }

            SettingsSection(title = "ML & Recording") {
                Text(
                    text = "Detection Model",
                    color = WayyColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                val defaultSelected = mlSettings.modelPath == DEFAULT_MODEL_PATH
                val exeedModelPath = "file:///android_asset/exeed.tflite"
                val exeedSelected = mlSettings.modelPath == exeedModelPath

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        text = "Default",
                        selected = defaultSelected,
                        onClick = { scope.launch { mlSettingsRepository.setModelPath(DEFAULT_MODEL_PATH) } },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "Exeed",
                        selected = exeedSelected,
                        onClick = { scope.launch { mlSettingsRepository.setModelPath(exeedModelPath) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Lane Model",
                    color = WayyColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                val laneDefaultSelected = mlSettings.laneModelPath == DEFAULT_LANE_MODEL_PATH
                val customLaneModelPath = "file:///android_asset/lane_custom.tflite"
                val laneCustomSelected = mlSettings.laneModelPath == customLaneModelPath

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        text = "Default",
                        selected = laneDefaultSelected,
                        onClick = { scope.launch { mlSettingsRepository.setLaneModelPath(DEFAULT_LANE_MODEL_PATH) } },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "Custom",
                        selected = laneCustomSelected,
                        onClick = { scope.launch { mlSettingsRepository.setLaneModelPath(customLaneModelPath) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0"
                )

                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "Build",
                    subtitle = "Debug"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
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
            text = "Settings",
            color = WayyColors.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            color = WayyColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = WayyColors.Surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    Column {
        Text(text = label, color = WayyColors.PrimaryMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = WayyColors.PrimaryMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = WayyColors.Primary,
                unfocusedTextColor = WayyColors.Primary,
                focusedBorderColor = WayyColors.Accent,
                unfocusedBorderColor = WayyColors.SurfaceVariant,
                cursorColor = WayyColors.Accent
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = WayyColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = WayyColors.PrimaryMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WayyColors.Primary,
                checkedTrackColor = WayyColors.Accent,
                uncheckedThumbColor = WayyColors.PrimaryMuted,
                uncheckedTrackColor = WayyColors.SurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = WayyColors.PrimaryMuted, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = WayyColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = WayyColors.PrimaryMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) WayyColors.Accent else WayyColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) WayyColors.Primary else WayyColors.PrimaryMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
