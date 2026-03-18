package com.invisiblesushi.hanzilens.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var currentLensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    /**
     * [displayRotation] should be one of [Surface.ROTATION_0], [Surface.ROTATION_90],
     * [Surface.ROTATION_180], or [Surface.ROTATION_270], obtained from
     * `view.display?.rotation`. This tells CameraX which orientation the device is
     * currently in so that [ImageProxy.imageInfo.rotationDegrees] is correct for every
     * frame. Call [updateTargetRotation] whenever the display rotation changes.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onFrame: (ImageProxy) -> Unit,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        displayRotation: Int = Surface.ROTATION_0
    ) {
        currentLensFacing = lensFacing
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()

            // No explicit setTargetRotation on Preview — PreviewView handles its own
            // orientation transforms automatically via the display manager.
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(displayRotation)
                .build()
                .also { it.setAnalyzer(analysisExecutor, FrameAnalyzer(onFrame)) }

            imageAnalysis = analysis

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            runCatching {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner, selector, preview, analysis
                )
            }.onFailure { it.printStackTrace() }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Call this whenever the device display rotation changes (e.g. from a
     * [LaunchedEffect] keyed on the current display rotation). Updates the
     * [ImageAnalysis] target rotation so that [ImageProxy.imageInfo.rotationDegrees]
     * is always correct for the current orientation.
     */
    fun updateTargetRotation(rotation: Int) {
        imageAnalysis?.targetRotation = rotation
    }

    /** Tap-to-focus using a point from the PreviewView's MeteringPointFactory. */
    fun focusAt(x: Float, y: Float, factory: MeteringPointFactory) {
        val point  = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /** Linear zoom in [0, 1] — CameraX maps this to the device's zoom range. */
    fun setLinearZoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
    }

    /** Current linear zoom [0, 1] from CameraX (best baseline for pinch gestures). */
    fun getCurrentLinearZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.linearZoom ?: 0f
    }

    /** Current zoom ratio (e.g. 1.0, 2.0, 3.5). */
    fun getCurrentZoomRatio(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        imageAnalysis = null
        analysisExecutor.shutdown()
    }
}
