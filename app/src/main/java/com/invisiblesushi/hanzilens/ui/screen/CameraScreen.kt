package com.invisiblesushi.hanzilens.ui.screen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import android.view.Surface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.invisiblesushi.hanzilens.camera.CameraManager
import com.invisiblesushi.hanzilens.ocr.OcrAnalyzer
import com.invisiblesushi.hanzilens.ocr.RecognizedBlock
import com.invisiblesushi.hanzilens.pinyin.PinyinResult
import com.invisiblesushi.hanzilens.ui.component.OcrOverlay
import com.invisiblesushi.hanzilens.ui.theme.AppColors
import com.invisiblesushi.hanzilens.ui.viewmodel.CameraViewModel
import com.invisiblesushi.hanzilens.ui.viewmodel.DebugMetrics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ─── Top-level entry ─────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    showCameraSettings: Boolean       = false,
    onCameraSettingsDismiss: () -> Unit = {}
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        cameraPermission.status.isGranted -> {
            CameraPreview(
                viewModel               = viewModel,
                showCameraSettings      = showCameraSettings,
                onCameraSettingsDismiss = onCameraSettingsDismiss
            )
        }
        cameraPermission.status.shouldShowRationale -> PermissionRationale(
            message       = "HanziLens needs the camera to detect Chinese characters.",
            showSettings  = false,
            onRequest     = { cameraPermission.launchPermissionRequest() }
        )
        else -> PermissionRationale(
            message       = "Camera permission was denied. Please enable it in App Settings.",
            showSettings  = true,
            onRequest     = { cameraPermission.launchPermissionRequest() }
        )
    }
}

// ─── Camera preview ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraPreview(
    viewModel: CameraViewModel,
    showCameraSettings: Boolean,
    onCameraSettingsDismiss: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view           = LocalView.current
    // Read LocalConfiguration so this composable recomposes on every orientation change.
    // We then derive displayRotation from the live display object so all 4 rotations
    // (portrait, landscape, reverse-portrait, reverse-landscape) are detected correctly.
    @Suppress("UNUSED_VARIABLE")
    val configuration  = LocalConfiguration.current
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0

    val ocrResults    by viewModel.ocrResults.collectAsState()
    val pinyinResults by viewModel.pinyinResults.collectAsState()
    val metrics       by viewModel.debugMetrics.collectAsState()
    val lensFacing    by viewModel.lensFacing.collectAsState()
    val linearZoom    by viewModel.linearZoom.collectAsState()
    val wordLookups      by viewModel.wordLookups.collectAsState()
    val frozenAnalyzing  by viewModel.frozenAnalyzing.collectAsState()
    val pinyinAutoSize   by viewModel.pinyinAutoSize.collectAsState()
    val showDebug        by viewModel.showDebug.collectAsState()
    val showOcrDebugText by viewModel.showOcrDebugText.collectAsState()

    val ocrAnalyzer = remember {
        OcrAnalyzer { blocks, w, h, rot, latMs ->
            viewModel.onOcrResults(blocks, w, h, rot, latMs)
        }.also { viewModel.ocrAnalyzerRef = it }
    }
    val cameraManager = remember { CameraManager(context).also { viewModel.cameraManagerRef = it } }
    val previewView   = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Restart camera when lens facing changes; pass the current display rotation
    // so ImageAnalysis.rotationDegrees is correct from the very first frame.
    DisposableEffect(lifecycleOwner, lensFacing) {
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        cameraManager.startCamera(
            lifecycleOwner  = lifecycleOwner,
            previewView     = previewView,
            onFrame         = { ocrAnalyzer.analyze(it) },
            lensFacing      = lensFacing,
            displayRotation = rotation
        )
        onDispose {
            cameraManager.shutdown()
            ocrAnalyzer.close()
            viewModel.ocrAnalyzerRef   = null
            viewModel.cameraManagerRef = null
        }
    }

    // Keep ImageAnalysis target rotation in sync whenever the display rotates.
    // This ensures imageProxy.imageInfo.rotationDegrees (and therefore effWidth/
    // effHeight in OcrAnalyzer) is always correct for all 4 device orientations.
    LaunchedEffect(displayRotation) {
        cameraManager.updateTargetRotation(displayRotation)
    }

    // Reset all frozen-scan state every time this screen enters composition.
    // NavHost fully disposes CameraScreen when navigating away, so any stale
    // ocrResults / wordLookups from a previous freeze are cleared on re-entry,
    // preventing the pinyin overlay from drawing frozen boxes over a live feed.
    LaunchedEffect(Unit) {
        viewModel.resetCameraState()
    }

    // UI state
    val context2     = LocalContext.current          // for clipboard
    var paused       by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var focusTap     by remember { mutableStateOf<Offset?>(null) }
    var focusVisible by remember { mutableStateOf(false) }
    // Controls whether the lookup sheet is fully open vs. collapsed to the peek tab
    var sheetVisible by remember { mutableStateOf(false) }

    // Infinite pulse used to animate the freeze button while scanning
    val scanInfinite  = rememberInfiniteTransition(label = "scan_pulse")
    val scanPulseAlpha by scanInfinite.animateFloat(
        initialValue = 0.55f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_alpha"
    )

    // Keep OcrAnalyzer.paused in sync
    LaunchedEffect(paused) { ocrAnalyzer.paused = paused }

    // Auto-show sheet when lookups arrive; collapse peek tab if lookups are cleared
    LaunchedEffect(wordLookups.isNotEmpty()) {
        sheetVisible = wordLookups.isNotEmpty()
    }

    LaunchedEffect(focusTap) {
        if (focusTap != null) {
            focusVisible = true
            delay(1_400)
            focusVisible = false
        }
    }

    // Back button tiers: collapse sheet → hide peek tab → unfreeze
    BackHandler(enabled = sheetVisible || wordLookups.isNotEmpty() || paused) {
        when {
            sheetVisible           -> sheetVisible = false          // sheet → peek tab
            wordLookups.isNotEmpty() -> viewModel.clearLookup()     // peek tab → gone
            paused                 -> { paused = false; frozenBitmap = null }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Camera feed / frozen frame ────────────────────────────────────────
        if (paused && frozenBitmap != null) {
            // Show the captured bitmap — identical framing to the live preview
            Image(
                bitmap           = frozenBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale     = ContentScale.Crop,
                modifier         = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory  = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            focusTap = tapOffset
                            cameraManager.focusAt(
                                tapOffset.x, tapOffset.y,
                                previewView.meteringPointFactory
                            )
                        }
                    }
            )
        }

        // ── Live OCR overlay (pinyin labels + bounding boxes) ────────────────
        OcrOverlay(
            blocks          = ocrResults,
            pinyinResults   = pinyinResults,
            imageWidth      = metrics.imageWidth,
            imageHeight     = metrics.imageHeight,
            rotationDegrees = metrics.rotationDegrees,
            pinyinAutoSize  = pinyinAutoSize,
            onBlockTap      = if (paused) { block -> viewModel.lookupBlock(block.text) } else null
        )

        // ── Resizable ROI box ─────────────────────────────────────────────────
        ResizableRoiBox(
            modifier     = Modifier.fillMaxSize(),
            onRoiChanged = viewModel::onRoiChanged
        )

        // ── Frozen-frame dim overlay ──────────────────────────────────────────
        if (paused) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        }

        // ── Empty state: frozen + scan done + no results ──────────────────────
        if (paused && !frozenAnalyzing && ocrResults.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text       = "No Chinese text detected.\nAdjust the ROI box and try again.",
                    color      = Color.White.copy(alpha = 0.85f),
                    fontSize   = 13.sp,
                    fontFamily = MONO,
                    lineHeight = 20.sp
                )
            }
        }

        // ── Focus ring indicator ──────────────────────────────────────────────
        if (focusVisible && focusTap != null && !paused) {
            FocusRing(center = focusTap!!)
        }

        // ── Debug overlay: performance HUD and/or OCR text strip (top of camera) ─
        if (showDebug || (showOcrDebugText && ocrResults.isNotEmpty())) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
            ) {
                if (showDebug) {
                    PerformanceHud(metrics = metrics)
                }
                if (showOcrDebugText && ocrResults.isNotEmpty()) {
                    OcrTextStrip(blocks = ocrResults, pinyinResults = pinyinResults)
                }
            }
        }

        // ── FROZEN / SCANNING badge (top-right) ──────────────────────────────
        if (paused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(
                        if (frozenAnalyzing) Color(0xFFFFBB33).copy(alpha = 0.92f)
                        else Color(0xFFFF4444).copy(alpha = 0.90f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = if (frozenAnalyzing) "⟳ SCANNING…" else "● SCANNED",
                    color      = Color.Black,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MONO
                )
            }
        }

        // ── Bottom stack: freeze button → peek tab → debug panel ──────────────
        Column(
            modifier              = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            // Freeze / resume button
            IconButton(
                onClick  = {
                    if (paused) {
                        paused       = false
                        frozenBitmap = null
                        viewModel.clearLookup()
                    } else {
                        val bmp = runCatching { previewView.bitmap }.getOrNull()
                        if (bmp != null) {
                            frozenBitmap = bmp
                            paused       = true
                            viewModel.analyzeFrozenBitmap(bmp)
                        }
                    }
                },
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .size(56.dp)
                    .background(
                        when {
                            frozenAnalyzing -> AppColors.Amber.copy(alpha = scanPulseAlpha)
                            paused          -> AppColors.Error.copy(alpha = 0.85f)
                            else            -> Color.Black.copy(alpha = 0.50f)
                        },
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector        = if (paused) Icons.Default.Close else Icons.Default.DocumentScanner,
                    contentDescription = if (paused) "Close scan" else "Scan",
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp)
                )
            }

            // Lookup peek tab — visible when results exist but sheet is collapsed
            if (wordLookups.isNotEmpty() && !sheetVisible) {
                LookupPeekTab(
                    groups    = wordLookups,
                    onClick   = { sheetVisible = true }
                )
            }

            // (detected text is now shown in the top debug overlay)
        }
    }

    // Word lookup sheet — dragging down collapses to peek tab, not a full dismiss
    if (sheetVisible && wordLookups.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState       = sheetState,
            containerColor   = Color(0xFF111111)
        ) {
            WordLookupSheet(
                groups    = wordLookups,
                context   = context2,
                onDismiss = { sheetVisible = false }
            )
        }
    }

    // Camera settings bottom sheet
    if (showCameraSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest  = onCameraSettingsDismiss,
            sheetState        = sheetState,
            containerColor    = Color(0xFF111111),
            dragHandle        = null
        ) {
            CameraSettingsSheet(
                viewModel  = viewModel,
                linearZoom = linearZoom,
                lensFacing = lensFacing,
                onDismiss  = onCameraSettingsDismiss
            )
        }
    }
}

// ─── Focus ring ───────────────────────────────────────────────────────────────

@Composable
private fun FocusRing(center: Offset) {
    val density = LocalDensity.current
    val sizePx  = with(density) { 72.dp.toPx() }
    val half    = (sizePx / 2).roundToInt()
    Box(
        modifier = Modifier
            .offset { IntOffset(center.x.roundToInt() - half, center.y.roundToInt() - half) }
            .size(72.dp)
            .border(2.dp, Color.White, RoundedCornerShape(4.dp))
    )
}

// ─── Resizable ROI box ────────────────────────────────────────────────────────

@Composable
private fun ResizableRoiBox(onRoiChanged: (RectF) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val screenW  = constraints.maxWidth.toFloat()
        val screenH  = constraints.maxHeight.toFloat()
        val density  = LocalDensity.current
        val handlePx = with(density) { 28.dp.toPx() }
        val minBoxPx = handlePx * 2

        var left   by remember { mutableFloatStateOf(screenW * 0.10f) }
        var top    by remember { mutableFloatStateOf(screenH * 0.20f) }
        var right  by remember { mutableFloatStateOf(screenW * 0.90f) }
        var bottom by remember { mutableFloatStateOf(screenH * 0.55f) }

        fun notify() = onRoiChanged(
            RectF(left / screenW, top / screenH, right / screenW, bottom / screenH)
        )

        // Re-anchor the ROI box whenever the screen dimensions change (device rotation).
        // Without this the pixel positions are stale because `remember` persists across
        // recompositions and the Activity never restarts (android:configChanges is set).
        LaunchedEffect(screenW, screenH) {
            left   = screenW * 0.10f
            top    = screenH * 0.20f
            right  = screenW * 0.90f
            bottom = screenH * 0.55f
            notify()
        }

        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
            drawRect(Color.White.copy(alpha = 0.08f), Offset(left, top),
                Size(right - left, bottom - top))
            drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top),
                style = Stroke(2.5f, pathEffect = dash))
            val cx = (left + right) / 2f; val cy = (top + bottom) / 2f; val arm = 18f
            for ((dx, dy) in listOf(-arm to 0f, arm to 0f, 0f to -arm, 0f to arm)) {
                drawLine(Color.White.copy(alpha = 0.7f),
                    Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 2f)
            }
        }

        CornerHandle(left,  top,    handlePx) { dx, dy ->
            left  = (left  + dx).coerceIn(0f,  right  - minBoxPx); top   = (top   + dy).coerceIn(0f, bottom - minBoxPx); notify() }
        CornerHandle(right, top,    handlePx) { dx, dy ->
            right = (right + dx).coerceIn(left + minBoxPx, screenW); top   = (top   + dy).coerceIn(0f, bottom - minBoxPx); notify() }
        CornerHandle(left,  bottom, handlePx) { dx, dy ->
            left   = (left   + dx).coerceIn(0f, right - minBoxPx); bottom = (bottom + dy).coerceIn(top + minBoxPx, screenH); notify() }
        CornerHandle(right, bottom, handlePx) { dx, dy ->
            right  = (right  + dx).coerceIn(left + minBoxPx, screenW); bottom = (bottom + dy).coerceIn(top + minBoxPx, screenH); notify() }

        val movePx = with(density) { 48.dp.toPx() }; val mh = (movePx / 2).roundToInt()
        val cx = ((left + right) / 2).roundToInt(); val cy = ((top + bottom) / 2).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(cx - mh, cy - mh) }
                .size(with(LocalDensity.current) { movePx.toDp() })
                .background(Color.Transparent, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { _, d ->
                        val w = right - left; val h = bottom - top
                        left = (left + d.x).coerceIn(0f, screenW - w); top = (top + d.y).coerceIn(0f, screenH - h)
                        right = left + w; bottom = top + h; notify()
                    }
                }
        )
    }
}

@Composable
private fun CornerHandle(px: Float, py: Float, handlePx: Float, onDrag: (Float, Float) -> Unit) {
    val half = (handlePx / 2).roundToInt()
    Box(
        modifier = Modifier
            .offset { IntOffset((px - half).roundToInt(), (py - half).roundToInt()) }
            .size(with(LocalDensity.current) { handlePx.toDp() })
            .background(Color.White, CircleShape)
            .pointerInput(Unit) { detectDragGestures { _, d -> onDrag(d.x, d.y) } }
    )
}

// ─── Camera settings sheet ────────────────────────────────────────────────────

@Composable
private fun CameraSettingsSheet(
    viewModel: CameraViewModel,
    linearZoom: Float,
    lensFacing: Int,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Text("Camera Settings", color = Color.White, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))

        // Zoom
        Text("Zoom  —  ${(linearZoom * 100).roundToInt()}%",
            color = Color(0xFFAAAAAA), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Slider(
            value         = linearZoom,
            onValueChange = { viewModel.setZoom(it) },
            valueRange    = 0f..1f,
            colors        = SliderDefaults.colors(
                thumbColor         = Color(0xFF00FF88),
                activeTrackColor   = Color(0xFF00FF88),
                inactiveTrackColor = Color(0xFF333333)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Lens toggle
        Text("Lens", color = Color(0xFFAAAAAA), fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 20.dp)) {
            LensButton(
                label    = "Back",
                icon     = Icons.Default.CameraRear,
                selected = lensFacing == CameraSelector.LENS_FACING_BACK,
                onClick  = { viewModel.setLensFacing(CameraSelector.LENS_FACING_BACK) }
            )
            LensButton(
                label    = "Front",
                icon     = Icons.Default.CameraFront,
                selected = lensFacing == CameraSelector.LENS_FACING_FRONT,
                onClick  = { viewModel.setLensFacing(CameraSelector.LENS_FACING_FRONT) }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LensButton(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors  = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF00FF88) else Color(0xFF222222)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(icon, contentDescription = label,
            tint = if (selected) Color.Black else Color.White,
            modifier = Modifier.size(18.dp))
        Text(label, color = if (selected) Color.Black else Color.White,
            modifier = Modifier.padding(start = 6.dp))
    }
}

// ─── Lookup peek tab ─────────────────────────────────────────────────────────

@Composable
private fun LookupPeekTab(
    groups: List<com.invisiblesushi.hanzilens.ui.viewmodel.BlockLookupGroup>,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF1A1A1A),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Pinyin summary
            Text(
                text       = groups.joinToString("   ") { it.fullPinyin },
                color      = Color(0xFFFFBB33),
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1
            )
            // Hanzi
            Text(
                text       = groups.joinToString("  ") { it.rawText },
                color      = Color.White,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines   = 1
            )
        }
        Icon(
            Icons.Default.ExpandLess,
            contentDescription = "Expand lookup",
            tint               = Color(0xFFAAAAAA),
            modifier           = Modifier.size(24.dp)
        )
    }
}

// ─── Debug constants ──────────────────────────────────────────────────────────

private val MONO  = AppColors.Mono
private val GREEN = AppColors.Green
private val AMBER = AppColors.Amber
private val DIM   = AppColors.Dim
private val BG    = AppColors.DebugBg

private fun cpuColor(cpu: Float) = when {
    cpu > 60f -> AppColors.Error; cpu > 30f -> AMBER; else -> GREEN
}

// ─── Performance HUD (top of camera) ─────────────────────────────────────────

@Composable
private fun PerformanceHud(metrics: DebugMetrics, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.60f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Row 1: timing + block count
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HudCell("ML",  "${metrics.ocrLatencyMs} ms",
                if (metrics.ocrLatencyMs > 500) AMBER else GREEN)
            HudCell("CPU", "${"%.1f".format(metrics.cpuPercent)} %",
                cpuColor(metrics.cpuPercent))
            HudCell("HEAP","${"%.0f".format(metrics.heapUsedMb)} / ${"%.0f".format(metrics.heapMaxMb)} MB", GREEN)
            HudCell("NAT", "${"%.0f".format(metrics.nativeHeapMb)} MB", GREEN)
        }
        // Row 2: frame counts + image info
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HudCell("BLK",  "${metrics.blocksDetected}", GREEN)
            HudCell("✓ FRM","${metrics.framesAnalyzed}", GREEN)
            HudCell("✗ SKP","${metrics.framesSkipped}", DIM)
            HudCell("THR",  "${metrics.threadCount}", DIM)
            HudCell("IMG",
                "${metrics.imageWidth}×${metrics.imageHeight} @${metrics.rotationDegrees}°", DIM)
        }
    }
}

@Composable
private fun HudCell(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DIM,        fontSize = 9.sp,  fontFamily = MONO)
        Text(value, color = valueColor, fontSize = 10.sp, fontFamily = MONO,
            fontWeight = FontWeight.Bold)
    }
}

// ─── OCR text strip (static, under performance HUD when debug on) ─────────────

@Composable
private fun OcrTextStrip(
    blocks: List<RecognizedBlock>,
    pinyinResults: Map<String, PinyinResult>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        HorizontalDivider(color = AppColors.DividerStrong, thickness = 0.5.dp,
            modifier = Modifier.padding(bottom = 5.dp))
        blocks.forEachIndexed { i, block ->
            val pinyin = pinyinResults[block.text]
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (pinyin != null && pinyin.fullPinyin.isNotBlank()) {
                        Text(pinyin.fullPinyin, color = AMBER, fontSize = 10.sp, fontFamily = MONO)
                    }
                    Text(block.text, color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "bbox ${block.boundingBox.width().toInt()}×${block.boundingBox.height().toInt()}",
                    color = DIM.copy(alpha = 0.5f), fontSize = 8.sp, fontFamily = MONO
                )
            }
            if (i < blocks.lastIndex) {
                HorizontalDivider(color = AppColors.DividerStrong.copy(alpha = 0.4f),
                    thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

// ─── Word lookup sheet ────────────────────────────────────────────────────────

@Composable
private fun WordLookupSheet(
    groups: List<com.invisiblesushi.hanzilens.ui.viewmodel.BlockLookupGroup>,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val clipboard   = context.getSystemService(android.content.ClipboardManager::class.java)
    val allText     = groups.joinToString("  ") { it.rawText }
    val allPinyin   = groups.joinToString("   ") { it.fullPinyin }
    val multiBlock  = groups.size > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // ── Combined header (shown in partial-expand peek) ────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Pinyin — always shown above the combined characters
                    Text(allPinyin, color = AMBER, fontSize = 12.sp,
                        fontFamily = MONO, lineHeight = 17.sp)
                    Text(
                        text       = allText,
                        color      = Color.White,
                        fontSize   = if (multiBlock) 18.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = if (multiBlock) 24.sp else 28.sp,
                        modifier   = Modifier.padding(top = 3.dp)
                    )
                    // For multiple blocks show a small summary line
                    if (multiBlock) {
                        Text(
                            "${groups.size} blocks  •  drag up for definitions",
                            color      = DIM,
                            fontSize   = 10.sp,
                            fontFamily = MONO,
                            modifier   = Modifier.padding(top = 3.dp)
                        )
                    }
                }
                IconButton(onClick = {
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText("Chinese text", allText)
                    )
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy all",
                        tint = DIM, modifier = Modifier.size(20.dp))
                }
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        // ── Per-block sections — each block collapses/expands as a unit ────────
        // Collapsed (default): shows block's rawText + pinyin + chevron
        // Expanded: shows word list with pinyin + definitions
        val blockExpanded = remember { mutableStateMapOf<Int, Boolean>() }

        LazyColumn(
            modifier       = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            groups.forEachIndexed { groupIdx, group ->
                val isBlockExpanded = blockExpanded[groupIdx] == true

                // ── Block header row — always visible, tap to expand ──────────
                item(key = "header_$groupIdx") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { blockExpanded[groupIdx] = !isBlockExpanded }
                            .background(AppColors.SurfaceBlock)
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (multiBlock) {
                                    Text(
                                        "Block ${groupIdx + 1}",
                                        color      = DIM,
                                        fontSize   = 10.sp,
                                        fontFamily = MONO,
                                        fontWeight = FontWeight.Bold,
                                        modifier   = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                                Text(
                                    group.fullPinyin,
                                    color      = AMBER,
                                    fontSize   = 12.sp,
                                    fontFamily = MONO,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    group.rawText,
                                    color      = Color.White,
                                    fontSize   = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(top = 2.dp)
                                )
                            }
                            // Chevron + optional copy button
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (multiBlock) {
                                    IconButton(onClick = {
                                        clipboard?.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "Chinese text", group.rawText)
                                        )
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy",
                                            tint = DIM, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Icon(
                                    imageVector        = if (isBlockExpanded) Icons.Default.ExpandLess
                                                        else Icons.Default.ExpandMore,
                                    contentDescription = if (isBlockExpanded) "Collapse" else "Expand",
                                    tint               = DIM,
                                    modifier           = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                }

                // ── Word list — only shown when block is expanded ─────────────
                if (isBlockExpanded) {
                    items(group.words, key = { "${groupIdx}_${it.word}" }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.word, color = Color.White, fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold)
                                if (entry.traditional.isNotBlank()) {
                                    Text(" / ${entry.traditional}", color = DIM, fontSize = 15.sp,
                                        modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                            if (entry.pinyin.isNotBlank()) {
                                Text(entry.pinyin, color = AMBER, fontSize = 13.sp,
                                    fontFamily = MONO, modifier = Modifier.padding(top = 2.dp))
                            }
                            if (entry.definitions.isNotEmpty()) {
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    entry.definitions.take(5).forEachIndexed { i, def ->
                                        Text(
                                            text       = "${i + 1}. $def",
                                            color      = Color.White.copy(alpha = 0.85f),
                                            fontSize   = 14.sp,
                                            lineHeight = 20.sp,
                                            modifier   = Modifier.padding(bottom = 2.dp)
                                        )
                                    }
                                }
                            } else {
                                Text("No definition found", color = DIM, fontSize = 11.sp,
                                    fontFamily = MONO, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        HorizontalDivider(color = AppColors.Divider)
                    }
                }

                if (multiBlock && groupIdx < groups.lastIndex) {
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }
}

// ─── Permission UI ────────────────────────────────────────────────────────────

@Composable
private fun PermissionRationale(
    message: String,
    showSettings: Boolean,
    onRequest: () -> Unit
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text     = message,
                color    = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            if (showSettings) {
                Button(
                    onClick = {
                        val intent = Intent(
                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GREEN)
                ) {
                    Text("Open App Settings", color = Color.Black)
                }
            } else {
                Button(
                    onClick = onRequest,
                    colors  = ButtonDefaults.buttonColors(containerColor = GREEN)
                ) {
                    Text("Grant Camera Permission", color = Color.Black)
                }
            }
        }
    }
}
