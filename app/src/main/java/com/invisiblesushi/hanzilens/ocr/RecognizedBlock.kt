package com.invisiblesushi.hanzilens.ocr

import android.graphics.RectF

data class RecognizedBlock(
    val text: String,
    val boundingBox: RectF
)
