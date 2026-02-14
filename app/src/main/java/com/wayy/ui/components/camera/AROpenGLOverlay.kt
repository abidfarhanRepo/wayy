package com.wayy.ui.components.camera

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.wayy.ar.model.ARFrame
import com.wayy.ar.renderer.ARDataConverter
import com.wayy.ar.renderer.AROverlayView
import com.wayy.ml.LanePoint
import com.wayy.ml.MlDetection

private const val TAG = "AROpenGLComposable"

@Composable
fun AROpenGLOverlay(
    detections: List<MlDetection>,
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var arView by remember { mutableStateOf<AROverlayView?>(null) }

    val arFrame = remember(detections, leftLane, rightLane, imageWidth, imageHeight) {
        ARDataConverter.convertToARFrame(
            detections = detections,
            leftLane = leftLane,
            rightLane = rightLane,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    LaunchedEffect(arFrame) {
        arView?.let { view ->
            if (view.isRendererReady()) {
                view.updateARFrame(arFrame)
                Log.v(TAG, "Updated AR frame: ${arFrame.lanes.size} lanes, ${arFrame.objects.size} objects")
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            Log.i(TAG, "Creating AROverlayView")
            AROverlayView(ctx).also {
                arView = it
            }
        },
        update = { view ->
            if (view.isRendererReady()) {
                view.updateARFrame(arFrame)
            }
        },
        onRelease = {
            Log.i(TAG, "Releasing AROverlayView")
            arView = null
        }
    )
}

@Composable
fun ARDebugOverlay(
    detections: List<MlDetection>,
    leftLane: List<LanePoint>,
    rightLane: List<LanePoint>,
    showOpenGL: Boolean = true,
    modifier: Modifier = Modifier
) {
    val imageWidth = 640
    val imageHeight = 480

    if (showOpenGL && (detections.isNotEmpty() || leftLane.isNotEmpty() || rightLane.isNotEmpty())) {
        AROpenGLOverlay(
            detections = detections,
            leftLane = leftLane,
            rightLane = rightLane,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = modifier
        )
    }
}
