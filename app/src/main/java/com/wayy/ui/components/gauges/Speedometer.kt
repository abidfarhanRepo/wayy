package com.wayy.ui.components.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors
import kotlin.math.min

/**
 * Animated speedometer gauge with circular progress
 *
 * @param speed Current speed value
 * @param maxSpeed Maximum speed for the gauge
 * @param modifier Modifier for the speedometer
 * @param unit Speed unit label (default: "mph")
 */
@Composable
fun Speedometer(
    speed: Float,
    maxSpeed: Float = 120f,
    modifier: Modifier = Modifier,
    unit: String = "mph"
) {
    // Animated speed value with spring physics
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "speed"
    )

    val size = 128.dp

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(40.dp)
                .background(
                    color = WayyColors.PrimaryLime.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        // Main gauge with progress ring
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WayyColors.BgTertiary.copy(alpha = 0.95f),
                            WayyColors.BgSecondary.copy(alpha = 0.95f)
                        )
                    ),
                    shape = CircleShape
                )
                .drawBehind {
                    // Background ring
                    drawCircle(
                        color = WayyColors.GlassBorder,
                        radius = size.toPx() / 2 - 4.dp.toPx(),
                        style = Stroke(width = 4.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Progress ring
            Canvas(modifier = Modifier.matchParentSize()) {
                val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
                val sweepAngle = 360f * progress
                val radius = min(this.size.width, this.size.height) / 2 - 8.dp.toPx()
                val strokeWidth = 6.dp.toPx()

                drawArc(
                    color = WayyColors.PrimaryLime,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    ),
                    alpha = 0.9f
                )
            }

            // Speed display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = animatedSpeed.toInt().toString(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WayyColors.PrimaryLime,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Compact speedometer variant for smaller displays
 */
@Composable
fun SpeedometerSmall(
    speed: Float,
    maxSpeed: Float = 120f,
    modifier: Modifier = Modifier,
    unit: String = "mph"
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "speed_small"
    )

    val size = 96.dp

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WayyColors.BgTertiary.copy(alpha = 0.9f),
                            WayyColors.BgSecondary.copy(alpha = 0.9f)
                        )
                    ),
                    shape = CircleShape
                )
                .drawBehind {
                    drawCircle(
                        color = WayyColors.GlassBorder,
                        radius = size.toPx() / 2 - 3.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val progress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
                val sweepAngle = 360f * progress
                val radius = min(this.size.width, this.size.height) / 2 - 6.dp.toPx()

                drawArc(
                    color = WayyColors.PrimaryLime,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    alpha = 0.9f
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = animatedSpeed.toInt().toString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    color = WayyColors.PrimaryLime
                )
            }
        }
    }
}
