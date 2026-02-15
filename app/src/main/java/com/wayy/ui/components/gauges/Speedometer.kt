package com.wayy.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors

@Composable
fun Speedometer(
    speed: Float,
    maxSpeed: Float = 120f,
    modifier: Modifier = Modifier,
    unit: String = "km/h"
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "speed"
    )

    val width = 160.dp
    val height = 80.dp

    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(WayyColors.Surface),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barHeight = 12.dp.toPx()
                val barWidth = size.width - 32.dp.toPx()
                val startX = 16.dp.toPx()
                val bottomY = size.height - 16.dp.toPx()

                drawRoundRect(
                    color = WayyColors.SurfaceVariant,
                    topLeft = Offset(startX, bottomY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
                val progressWidth = barWidth * progress

                val progressColor = when {
                    progress > 0.9f -> WayyColors.Error
                    progress > 0.7f -> WayyColors.Warning
                    else -> WayyColors.Accent
                }

                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(startX, bottomY - barHeight),
                    size = Size(progressWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = animatedSpeed.toInt().toString(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = WayyColors.Primary
                )
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WayyColors.PrimaryMuted
                )
            }
        }
    }
}

@Composable
fun SpeedometerSmall(
    speed: Float,
    maxSpeed: Float = 120f,
    modifier: Modifier = Modifier,
    unit: String = "km/h"
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "speed_small"
    )

    val width = 110.dp
    val height = 64.dp

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(WayyColors.Surface),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barHeight = 8.dp.toPx()
            val barWidth = size.width - 20.dp.toPx()
            val startX = 10.dp.toPx()
            val bottomY = size.height - 10.dp.toPx()

            drawRoundRect(
                color = WayyColors.SurfaceVariant,
                topLeft = Offset(startX, bottomY - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
            val progressWidth = barWidth * progress

            val progressColor = when {
                progress > 0.9f -> WayyColors.Error
                progress > 0.7f -> WayyColors.Warning
                else -> WayyColors.Accent
            }

            drawRoundRect(
                color = progressColor,
                topLeft = Offset(startX, bottomY - barHeight),
                size = Size(progressWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = animatedSpeed.toInt().toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = WayyColors.Primary
            )
            Text(
                text = unit,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WayyColors.PrimaryMuted
            )
        }
    }
}
