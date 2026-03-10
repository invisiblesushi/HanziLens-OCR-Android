package com.invisiblesushi.hanzilens.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Sits on CameraX's ImageAnalysis use case.
 * Forwards each camera frame to [onFrame].
 *
 * Closing responsibility belongs to [onFrame] — do NOT close the proxy here.
 * OcrAnalyzer closes it inside addOnCompleteListener after ML Kit finishes its
 * async processing. Closing early (e.g. in a finally block) would destroy the
 * pixel buffer before ML Kit reads it, resulting in 0 detections.
 *
 * The analyzer runs on a background executor supplied by CameraManager —
 * never on the main thread.
 */
class FrameAnalyzer(
    private val onFrame: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        onFrame(imageProxy)
    }
}
