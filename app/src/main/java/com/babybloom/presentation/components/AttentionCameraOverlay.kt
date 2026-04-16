package com.babybloom.presentation.components

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.babybloom.presentation.viewmodels.ActivityViewModel
import com.babybloom.util.attention.AttentionDetector
import com.babybloom.util.attention.AttentionSample
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlinx.coroutines.guava.await

@Composable
fun AttentionCameraOverlay(
    onSample: (AttentionSample?) -> Unit,
    attentionDetector: AttentionDetector = hiltViewModel<ActivityViewModel>()
        .let { hiltViewModel() }  // inject directly
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
            // Only sample every 2 seconds
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        cameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, imageAnalyzer
        )

        // Ticker: sample every 2 seconds
        while (true) {
            delay(2_000)
            // imageProxy is captured inside analyzer — see full implementation note below
        }
    }
}