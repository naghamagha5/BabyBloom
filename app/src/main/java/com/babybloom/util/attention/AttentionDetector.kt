package com.babybloom.util.attention

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.coroutines.resume

data class AttentionSample(
    val isAttentive: Boolean,
    val eulerY: Float,
    val eulerX: Float,
    val eyeOpenProbability: Float
)

@Singleton
class AttentionDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        detector = FaceDetection.getClient(options)
    }

    // Call this from the shell on a 2-second ticker
    @SuppressLint("UnsafeOptInUsageError")
    @ExperimentalGetImage
    suspend fun analyze(imageProxy: ImageProxy): AttentionSample? =
        suspendCancellableCoroutine { cont ->
            val mediaImage = imageProxy.image ?: run {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull()
                    if (face == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }
                    val eulerY = face.headEulerAngleY
                    val eulerX = face.headEulerAngleX
                    val leftEye  = face.leftEyeOpenProbability  ?: 0f
                    val rightEye = face.rightEyeOpenProbability ?: 0f

                    val headOk  = abs(eulerY) < 12f && abs(eulerX) < 12f
                    val eyesOk  = leftEye > 0.5f && rightEye > 0.5f
                    val avgEye  = (leftEye + rightEye) / 2f

                    cont.resume(AttentionSample(
                        isAttentive = headOk && eyesOk,
                        eulerY = eulerY,
                        eulerX = eulerX,
                        eyeOpenProbability = avgEye
                    ))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
}
