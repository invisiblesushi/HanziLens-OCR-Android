package com.invisiblesushi.hanzilens.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wraps ML Kit's Chinese text recognizer.
 *
 * Frames faster than [throttleMs] are dropped immediately.
 * Latency and frame counters are reported back via [onResults] so the
 * ViewModel can surface them in the debug panel.
 */
class OcrAnalyzer(
    @Volatile var throttleMs: Long = 1_000L,
    private val onResults: (
        blocks: List<RecognizedBlock>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
        latencyMs: Long
    ) -> Unit
) {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var lastAnalyzedAt = 0L

    /** When true, all incoming frames are dropped and the last OCR result is preserved. */
    @Volatile var paused: Boolean = false

    val framesAnalyzed = AtomicInteger(0)
    val framesSkipped  = AtomicInteger(0)

    @ExperimentalGetImage
    fun analyze(imageProxy: ImageProxy) {
        if (paused) {
            framesSkipped.incrementAndGet()
            imageProxy.close()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAnalyzedAt < throttleMs) {
            framesSkipped.incrementAndGet()
            imageProxy.close()
            return
        }
        lastAnalyzedAt = now
        framesAnalyzed.incrementAndGet()

        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }

        val sensorRotation = imageProxy.imageInfo.rotationDegrees
        val sensorWidth    = imageProxy.width
        val sensorHeight   = imageProxy.height

        val effWidth  = if (sensorRotation == 90 || sensorRotation == 270) sensorHeight else sensorWidth
        val effHeight = if (sensorRotation == 90 || sensorRotation == 270) sensorWidth  else sensorHeight

        val input   = InputImage.fromMediaImage(mediaImage, sensorRotation)
        val startMs = System.currentTimeMillis()

        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                val latencyMs = System.currentTimeMillis() - startMs
                val blocks = visionText.textBlocks
                    .filter { it.text.isNotBlank() }
                    .map { block ->
                        val rect = block.boundingBox
                        RecognizedBlock(
                            text        = block.text,
                            boundingBox = if (rect != null) android.graphics.RectF(rect)
                                          else android.graphics.RectF()
                        )
                    }
                onResults(blocks, effWidth, effHeight, 0, latencyMs)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onResults(emptyList(), effWidth, effHeight, 0, -1L)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() { recognizer.close() }
}
