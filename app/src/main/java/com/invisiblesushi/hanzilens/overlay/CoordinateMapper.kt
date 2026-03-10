package com.invisiblesushi.hanzilens.overlay

import android.graphics.RectF

/**
 * Maps ML Kit bounding boxes (image pixel space) to normalized screen space [0,1].
 *
 * The raw camera image is in landscape orientation. [rotationDegrees] is the
 * clockwise rotation needed to make it appear upright on screen
 * (from ImageProxy.imageInfo.rotationDegrees — typically 90 or 270 on phones).
 *
 * Normalized output: (0,0) = top-left of screen, (1,1) = bottom-right.
 */
object CoordinateMapper {

    fun imageRectToNormalized(
        rect: RectF,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): RectF {
        val corners = listOf(
            mapPoint(rect.left, rect.top,    imageWidth, imageHeight, rotationDegrees),
            mapPoint(rect.right, rect.bottom, imageWidth, imageHeight, rotationDegrees),
        )
        return RectF(
            corners.minOf { it.first },
            corners.minOf { it.second },
            corners.maxOf { it.first },
            corners.maxOf { it.second }
        )
    }

    // Normalized image coords → normalized screen coords, accounting for rotation.
    private fun mapPoint(
        x: Float, y: Float,
        imageWidth: Int, imageHeight: Int,
        rotationDegrees: Int
    ): Pair<Float, Float> {
        val nx = x / imageWidth
        val ny = y / imageHeight
        return when (rotationDegrees) {
            90  -> Pair(1f - ny, nx)       // sensor landscape → portrait
            180 -> Pair(1f - nx, 1f - ny)  // upside-down
            270 -> Pair(ny, 1f - nx)       // sensor landscape-right → portrait
            else -> Pair(nx, ny)           // 0°, no rotation
        }
    }
}
