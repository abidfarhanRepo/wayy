package com.wayy.ui.components.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun CameraPreviewSurface(
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {},
    onVideoCaptureReady: ((VideoCapture<Recorder>) -> Unit)? = null,
    frameAnalyzer: ImageAnalysis.Analyzer? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(cameraProviderFuture) {
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val analysis = frameAnalyzer?.let {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor, it)
                        }
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    if (analysis != null) {
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture, analysis)
                    } else {
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)
                    }
                    onVideoCaptureReady?.invoke(videoCapture)
                } catch (e: Exception) {
                    Log.e("WayyCamera", "Camera bind failed", e)
                    onError("Camera failed to start")
                }
            },
            executor
        )
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
