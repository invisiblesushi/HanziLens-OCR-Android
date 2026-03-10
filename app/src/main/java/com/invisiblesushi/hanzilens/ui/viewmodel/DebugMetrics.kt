package com.invisiblesushi.hanzilens.ui.viewmodel

data class DebugMetrics(
    // ML Kit
    val ocrLatencyMs: Long   = 0L,
    val framesAnalyzed: Int  = 0,
    val framesSkipped: Int   = 0,
    val blocksDetected: Int  = 0,
    // Camera image
    val imageWidth: Int      = 0,
    val imageHeight: Int     = 0,
    val rotationDegrees: Int = 0,
    // Process memory (MB)
    val heapUsedMb: Float    = 0f,
    val heapMaxMb: Float     = 0f,
    val nativeHeapMb: Float  = 0f,
    // Process CPU (0–100 %)
    val cpuPercent: Float    = 0f,
    // Misc
    val threadCount: Int     = 0
)
