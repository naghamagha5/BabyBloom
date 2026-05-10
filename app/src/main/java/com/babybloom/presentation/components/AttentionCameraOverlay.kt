package com.babybloom.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.babybloom.util.attention.AttentionSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
fun AttentionCameraOverlay(
    onSample: (AttentionSample?) -> Unit,
    analyzeImage: suspend (ImageProxy) -> AttentionSample?
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val currentOnSample = rememberUpdatedState(onSample)
    val currentAnalyzeImage = rememberUpdatedState(analyzeImage)

    DisposableEffect(context, lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return@DisposableEffect onDispose { }
        }

        val executor = Executors.newSingleThreadExecutor()
        val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lastSampleMs = AtomicLong(0L)
        val isAnalyzing = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalyzer.setAnalyzer(executor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (now - lastSampleMs.get() < 2_000L || !isAnalyzing.compareAndSet(false, true)) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastSampleMs.set(now)
            analysisScope.launch {
                try {
                    currentOnSample.value(currentAnalyzeImage.value(imageProxy))
                } finally {
                    runCatching { imageProxy.close() }
                    isAnalyzing.set(false)
                }
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    cameraProvider = cameraProviderFuture.get().also { provider ->
                        provider.unbind(imageAnalyzer)
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            imageAnalyzer
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            cameraProvider?.unbind(imageAnalyzer)
            imageAnalyzer.clearAnalyzer()
            analysisScope.cancel()
            executor.shutdown()
        }
    }
}
