package com.biyahe.companion.safety

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Front camera + ML Kit face detection, on the phone only. Frames are looked at in memory and
 * thrown away: nothing is recorded, saved or uploaded.
 *
 * Low resolution (640x480) and about 10 frames a second, to save battery and heat on a
 * dashboard. [onFrame] gets the eye openness (0..1, or null when no face) on the main thread.
 */
class EyeCamera(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (timeMs: Long, openness: Float?) -> Unit,
    private val onError: (String) -> Unit
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)   // eye-open probability
            .setMinFaceSize(0.15f)
            .build()
    )
    private var provider: ProcessCameraProvider? = null
    private val bound = mutableListOf<UseCase>()
    private var lastFrameAt = 0L
    @Volatile private var released = false

    /** Starts the camera. Pass a preview surface to also show the picture (setup and test screens). */
    fun start(previewSurface: Preview.SurfaceProvider? = null) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (released) return@addListener
            try {
                val p = future.get()
                if (!p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    onError("This phone has no front camera, so eye watch can't run.")
                    return@addListener
                }
                provider = p
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, ::analyze) }
                val cases = mutableListOf<UseCase>(analysis)
                if (previewSurface != null) {
                    cases += Preview.Builder().build().also { it.setSurfaceProvider(previewSurface) }
                }
                p.unbind(*bound.toTypedArray())
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, *cases.toTypedArray())
                bound.clear(); bound += cases
            } catch (e: Exception) {
                onError("The front camera is busy or not available.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        released = true
        runCatching { provider?.unbind(*bound.toTypedArray()) }
        bound.clear()
        runCatching { detector.close() }
        executor.shutdown()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = proxy.image
        if (released || media == null || now - lastFrameAt < FRAME_GAP_MS) { proxy.close(); return }
        lastFrameAt = now
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (released) return@addOnSuccessListener
                // The driver is the biggest (closest) face; passengers may be in view too.
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                onFrame(now, face?.let { eyeOpenness(it.leftEyeOpenProbability, it.rightEyeOpenProbability) })
            }
            .addOnFailureListener { if (!released) onFrame(now, null) }
            .addOnCompleteListener { proxy.close() }
    }

    private companion object {
        const val FRAME_GAP_MS = 90L   // ~10 frames a second is plenty for eyelids
    }
}
