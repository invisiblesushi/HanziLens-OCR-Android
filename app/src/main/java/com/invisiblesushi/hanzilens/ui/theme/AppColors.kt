package com.invisiblesushi.hanzilens.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Shared colour and typography constants used across the app.
 * Import from here instead of scattering magic Color(0xFF…) literals in each file.
 */
object AppColors {
    // ── Primary palette ───────────────────────────────────────────────────────
    val Green         = Color(0xFF00FF88)   // primary accent, OCR borders, active states
    val Amber         = Color(0xFFFFBB33)   // pinyin labels
    val Dim           = Color(0xFFAAAAAA)   // secondary / muted text
    val Error         = Color(0xFFFF4444)   // errors, frozen badge

    // ── Surfaces ──────────────────────────────────────────────────────────────
    val Surface       = Color(0xFF111111)   // bottom sheet background
    val SurfaceHigh   = Color(0xFF1A1A1A)   // header / elevated surface
    val SurfaceBlock  = Color(0xFF1C1C1C)   // block section separator
    val DebugBg       = Color.Black.copy(alpha = 0.70f)

    // ── Dividers ──────────────────────────────────────────────────────────────
    val Divider       = Color(0xFF222222)
    val DividerStrong = Color(0xFF333333)

    // ── Shared typography ─────────────────────────────────────────────────────
    val Mono          = FontFamily.Monospace
}
