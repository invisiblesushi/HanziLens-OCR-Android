package com.invisiblesushi.hanzilens.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invisiblesushi.hanzilens.ocr.RecognizedBlock
import com.invisiblesushi.hanzilens.pinyin.PinyinResult
import kotlin.math.max
import kotlin.math.roundToInt

private val BORDER_COLOR = Color(0xFF00FF88)
private val LABEL_BG     = Color.Black.copy(alpha = 0.80f)
private val PINYIN_COLOR = Color(0xFFFFBB33)

/**
 * Returns true if the string contains at least one CJK character.
 * Used to skip purely-Latin blocks returned by the Chinese ML Kit model.
 */
private fun String.hasChinese(): Boolean = any {
    it.code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
    it.code in 0x3400..0x4DBF ||   // CJK Extension A
    it.code in 0xF900..0xFAFF      // CJK Compatibility Ideographs
}

/**
 * Draws green bounding-box borders and amber pinyin labels over the camera feed.
 *
 * [imageWidth] / [imageHeight] are the UPRIGHT (post-rotation) dimensions —
 * OcrAnalyzer already swaps them so callers never need to handle rotation here.
 *
 * Coordinate mapping accounts for PreviewView's FILL_CENTER scale type,
 * which zooms the preview to fill the view and crops the excess horizontally
 * or vertically.
 */
@Composable
fun OcrOverlay(
    blocks: List<RecognizedBlock>,
    pinyinResults: Map<String, PinyinResult>,
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,          // kept for future use; currently always 0 from OcrAnalyzer
    modifier: Modifier = Modifier,
    /** When true, pinyin font size scales to match the on-screen character height. */
    pinyinAutoSize: Boolean = false,
    /** When non-null and the overlay is in interactive mode, tap a block to invoke this. */
    onBlockTap: ((RecognizedBlock) -> Unit)? = null
) {
    if (blocks.isEmpty() || imageWidth == 0 || imageHeight == 0) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sw      = constraints.maxWidth.toFloat()
        val sh      = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        val effW = imageWidth.toFloat()
        val effH = imageHeight.toFloat()

        // PreviewView uses FILL_CENTER: scale up so both dimensions meet or exceed the view,
        // then center and crop the overflow.
        val scale = max(sw / effW, sh / effH)
        val cropX = max(0f, (effW * scale - sw) / 2f)
        val cropY = max(0f, (effH * scale - sh) / 2f)

        // Map a point in upright image space → screen pixel coordinates.
        fun sx(x: Float) = (x * scale - cropX)
        fun sy(y: Float) = (y * scale - cropY)

        // ── Pre-filter and map ────────────────────────────────────────────────
        data class Mapped(
            val block: RecognizedBlock,
            val sl: Float, val st: Float, val sr: Float, val sb: Float
        )

        val mapped = blocks.mapNotNull { block ->
            if (!block.text.hasChinese()) return@mapNotNull null
            val sl = sx(block.boundingBox.left)
            val st = sy(block.boundingBox.top)
            val sr = sx(block.boundingBox.right)
            val sb = sy(block.boundingBox.bottom)
            // Skip boxes that are entirely off-screen
            if (sr < 0 || sb < 0 || sl > sw || st > sh) return@mapNotNull null
            if ((sr - sl) < 6f || (sb - st) < 6f)       return@mapNotNull null
            Mapped(block, sl, st, sr, sb)
        }

        // ── Canvas: borders + fills ───────────────────────────────────────────
        Canvas(Modifier.fillMaxSize()) {
            mapped.forEach { (_, sl, st, sr, sb) ->
                val w = sr - sl
                val h = sb - st

                drawRect(
                    color   = BORDER_COLOR.copy(alpha = 0.07f),
                    topLeft = Offset(sl, st), size = Size(w, h)
                )
                drawRect(
                    color   = BORDER_COLOR.copy(alpha = 0.90f),
                    topLeft = Offset(sl, st), size = Size(w, h),
                    style   = Stroke(width = 2.2f)
                )
                // Corner ticks
                val tick = minOf(w, h, 14f)
                listOf(
                    Offset(sl, st)       to Pair( 1f,  1f),
                    Offset(sr, st)       to Pair(-1f,  1f),
                    Offset(sl, sb)       to Pair( 1f, -1f),
                    Offset(sr, sb)       to Pair(-1f, -1f)
                ).forEach { (c, d) ->
                    drawLine(BORDER_COLOR, c, Offset(c.x + d.first * tick, c.y),  strokeWidth = 3.5f)
                    drawLine(BORDER_COLOR, c, Offset(c.x, c.y + d.second * tick), strokeWidth = 3.5f)
                }
            }
        }

        // ── Labels: pinyin pill above each box ────────────────────────────────
        val padPx = with(density) { 3.dp.toPx() }

        mapped.forEach { (block, sl, st, sr, sb) ->
            val pinyin = pinyinResults[block.text] ?: return@forEach

            // Only show tokens that have actual pinyin — skip "?" for unmatched chars
            val validPinyin = pinyin.tokens.filter { it.pinyin.isNotBlank() }
            if (validPinyin.isEmpty()) return@forEach

            val displayText = if (validPinyin.size <= 1)
                validPinyin.joinToString(" ") { it.pinyin }
            else
                validPinyin.joinToString("  ") { it.pinyin }

            // Dynamic font size: 35 % of the block's screen pixel height, clamped 9–28 sp.
            // Falls back to a fixed 13 sp when auto-sizing is off.
            val fontSizeSp = if (pinyinAutoSize) {
                with(density) { ((sb - st) * 0.35f).toSp() }.value.coerceIn(9f, 28f)
            } else {
                13f
            }
            val lineHeightSp = fontSizeSp * 1.25f

            // Label height follows font size so the above/inside decision stays accurate
            val labelH = with(density) { (fontSizeSp * density.density + padPx * 2) }

            // Place label above the box if room, else inside the top edge
            val labelY = if (st >= labelH + padPx) (st - labelH - padPx).roundToInt()
                         else                       (st + padPx).roundToInt()
            val labelX    = sl.coerceAtLeast(padPx).roundToInt()
            val maxLabelW = (sw - labelX).coerceAtLeast(60f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(labelX, labelY) }
                    .widthIn(max = with(density) { maxLabelW.toDp() })
                    .background(
                        LABEL_BG,
                        RoundedCornerShape(
                            topStart    = 4.dp, topEnd     = 4.dp,
                            bottomEnd   = 4.dp,
                            bottomStart = if (st < labelH) 4.dp else 0.dp
                        )
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text       = displayText,
                    color      = PINYIN_COLOR,
                    fontSize   = fontSizeSp.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 2,
                    lineHeight = lineHeightSp.sp
                )
            }
        }

        // ── Invisible tap targets (active when onBlockTap is provided) ─────────
        if (onBlockTap != null) {
            mapped.forEach { (block, sl, st, sr, sb) ->
                val bw = (sr - sl).coerceAtLeast(48f)
                val bh = (sb - st).coerceAtLeast(48f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(sl.roundToInt(), st.roundToInt()) }
                        .size(
                            width  = with(density) { bw.toDp() },
                            height = with(density) { bh.toDp() }
                        )
                        .background(
                            // Subtle highlight so user knows it's tappable
                            BORDER_COLOR.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp)
                        )
                        .pointerInput(block.text) {
                            detectTapGestures { onBlockTap(block) }
                        }
                )
            }
        }
    }
}
