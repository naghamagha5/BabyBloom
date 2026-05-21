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
    val eyeOpenProbability: Float,
    val attentionScore: Float
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

                    val avgEye  = (leftEye + rightEye) / 2f
                    val minEye = minOf(leftEye, rightEye)

                    val screenFacingScore = softScore(
                        value = abs(eulerY),
                        fullCreditUntil = 8f,
                        zeroCreditAt = 24f
                    )
                    val eyeScore = softScore(
                        value = avgEye,
                        fullCreditUntil = 0.80f,
                        zeroCreditAt = 0.35f,
                        higherIsBetter = true
                    )
                    val pitchPenalty = pitchPenalty(eulerX)
                    val bothEyesOpenPenalty = if (minEye >= 0.45f) 1f else 0.65f
                    val rawScore = (
                        screenFacingScore * 0.62f +
                            eyeScore * 0.28f +
                            pitchPenalty * 0.10f
                        ) * bothEyesOpenPenalty
                    val attentionScore = rawScore
                        .coerceAtMost(screenFacingCap(screenFacingScore))
                        .coerceAtMost(eyeOpenCap(avgEye, minEye))
                        .coerceIn(0f, 1f)

                    cont.resume(AttentionSample(
                        isAttentive = attentionScore >= 0.60f,
                        eulerY = eulerY,
                        eulerX = eulerX,
                        eyeOpenProbability = avgEye,
                        attentionScore = attentionScore
                    ))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }

    private fun softScore(
        value: Float,
        fullCreditUntil: Float,
        zeroCreditAt: Float,
        higherIsBetter: Boolean = false
    ): Float {
        val score = if (higherIsBetter) {
            (value - zeroCreditAt) / (fullCreditUntil - zeroCreditAt)
        } else {
            (zeroCreditAt - value) / (zeroCreditAt - fullCreditUntil)
        }
        return score.coerceIn(0f, 1f)
    }

    private fun pitchPenalty(eulerX: Float): Float {
        val pitch = abs(eulerX)
        return softScore(
            value = pitch,
            fullCreditUntil = 18f,
            zeroCreditAt = 42f
        )
    }

    private fun screenFacingCap(screenFacingScore: Float): Float =
        when {
            screenFacingScore < 0.15f -> 0.18f
            screenFacingScore < 0.35f -> 0.35f
            screenFacingScore < 0.55f -> 0.55f
            else -> 1f
        }

    private fun eyeOpenCap(avgEye: Float, minEye: Float): Float =
        when {
            avgEye < 0.25f -> 0.15f
            avgEye < 0.40f -> 0.35f
            minEye < 0.25f -> 0.45f
            else -> 1f
        }
}
