package com.wayy.ui.components.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors

@Composable
fun SpeedLimitIndicator(
    speedLimitMph: Int,
    currentSpeedMph: Float,
    modifier: Modifier = Modifier
) {
    val isOverLimit = currentSpeedMph > speedLimitMph
    val isApproachingLimit = currentSpeedMph > speedLimitMph * 0.9f && !isOverLimit

    val bgColor = when {
        isOverLimit -> WayyColors.Error
        isApproachingLimit -> WayyColors.PrimaryOrange
        else -> Color.White
    }

    val textColor = when {
        isOverLimit || isApproachingLimit -> Color.White
        else -> Color.Black
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(3.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = speedLimitMph.toString(),
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "MPH",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun SpeedLimitWarning(
    speedLimitMph: Int,
    currentSpeedMph: Float,
    modifier: Modifier = Modifier
) {
    val isOverLimit = currentSpeedMph > speedLimitMph
    val overAmount = (currentSpeedMph - speedLimitMph).toInt()

    if (isOverLimit && overAmount > 5) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WayyColors.Error.copy(alpha = 0.9f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "+$overAmount",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "MPH over",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CurrentRoadLabel(
    roadName: String,
    modifier: Modifier = Modifier
) {
    if (roadName.isEmpty()) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WayyColors.BgSecondary.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = roadName,
            color = WayyColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CurrentRoadDisplay(
    roadName: String,
    nextRoadName: String? = null,
    modifier: Modifier = Modifier
) {
    if (roadName.isEmpty()) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WayyColors.BgSecondary.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = roadName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        if (!nextRoadName.isNullOrEmpty()) {
            Text(
                text = "→ $nextRoadName",
                color = WayyColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
