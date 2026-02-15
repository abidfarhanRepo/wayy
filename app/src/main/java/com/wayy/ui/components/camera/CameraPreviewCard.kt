package com.wayy.ui.components.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.components.glass.GlassCardElevated
import com.wayy.ui.components.navigation.Direction
import com.wayy.ui.theme.WayyColors
import com.wayy.ml.MlDetection
import com.wayy.ml.LanePoint

@Composable
fun CameraPreviewCard(
    direction: Direction,
    distanceToTurnMeters: Double,
    deviceBearing: Float,
    turnBearing: Float,
    isApproaching: Boolean,
    showGuidance: Boolean = true,
    hasCameraPermission: Boolean,
    onVideoCaptureReady: ((androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>) -> Unit)? = null,
    frameAnalyzer: androidx.camera.core.ImageAnalysis.Analyzer? = null,
    modifier: Modifier = Modifier
) {
    GlassCardElevated(
        modifier = modifier
            .size(width = 180.dp, height = 135.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                HiddenCameraForML(
                    onVideoCaptureReady = onVideoCaptureReady,
                    frameAnalyzer = frameAnalyzer
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = WayyColors.Surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera permission required",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showGuidance) {
                TurnArrowOverlay(
                    direction = direction,
                    distanceToTurnMeters = distanceToTurnMeters,
                    deviceBearing = deviceBearing,
                    turnBearing = turnBearing,
                    isApproaching = isApproaching,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                )

                // Lane guidance removed (AR cleanup)
            }

            // AR vision overlay removed (AR cleanup)
        }
    }
}
