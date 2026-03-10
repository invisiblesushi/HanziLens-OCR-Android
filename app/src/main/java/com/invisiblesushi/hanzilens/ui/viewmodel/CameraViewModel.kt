package com.invisiblesushi.hanzilens.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Debug
import androidx.camera.core.CameraSelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.invisiblesushi.hanzilens.camera.CameraManager
import com.invisiblesushi.hanzilens.dictionary.DictionaryRepository
import com.invisiblesushi.hanzilens.ocr.OcrAnalyzer
import com.invisiblesushi.hanzilens.ocr.RecognizedBlock
import com.invisiblesushi.hanzilens.overlay.CoordinateMapper
import com.invisiblesushi.hanzilens.pinyin.PinyinConverter
import com.invisiblesushi.hanzilens.pinyin.PinyinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AppState { LOADING, READY, ERROR }

/** One segmented word and its dictionary entry, shown in the lookup sheet. */
data class WordLookup(
    val word: String,
    val traditional: String,       // empty string when same as simplified
    val pinyin: String,
    val definitions: List<String>  // empty if no dictionary entry
)

/**
 * All looked-up words that belong to one OCR text block.
 * When multiple blocks are visible on freeze, the sheet shows one group per block.
 */
data class BlockLookupGroup(
    val rawText: String,    // original OCR block text (hanzi)
    val fullPinyin: String, // joined pinyin for the whole block
    val words: List<WordLookup>
)

private fun String.containsChinese(): Boolean = any {
    it.code in 0x4E00..0x9FFF ||
    it.code in 0x3400..0x4DBF ||
    it.code in 0xF900..0xFAFF
}

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    // ── Persisted settings ────────────────────────────────────────────────────
    private val prefs = app.getSharedPreferences("hanzilens_settings", android.content.Context.MODE_PRIVATE)

    // ── Dictionary / Pinyin ───────────────────────────────────────────────────
    private val repository = DictionaryRepository(app)
    private var pinyinConverter: PinyinConverter? = null

    // ── App init state ────────────────────────────────────────────────────────
    private val _appState       = MutableStateFlow(AppState.LOADING)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _loadProgress   = MutableStateFlow(0f)
    val loadProgress: StateFlow<Float> = _loadProgress.asStateFlow()

    private val _loadingMessage = MutableStateFlow("Initializing…")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    // ── OCR results ───────────────────────────────────────────────────────────
    private val _ocrResults    = MutableStateFlow<List<RecognizedBlock>>(emptyList())
    val ocrResults: StateFlow<List<RecognizedBlock>> = _ocrResults.asStateFlow()

    private val _pinyinResults = MutableStateFlow<Map<String, PinyinResult>>(emptyMap())
    val pinyinResults: StateFlow<Map<String, PinyinResult>> = _pinyinResults.asStateFlow()

    private val _roi = MutableStateFlow(RectF(0f, 0f, 1f, 1f))
    val roi: StateFlow<RectF> = _roi.asStateFlow()

    // ── Camera settings ───────────────────────────────────────────────────────
    private val _linearZoom    = MutableStateFlow(0f)
    val linearZoom: StateFlow<Float> = _linearZoom.asStateFlow()

    private val _lensFacing    = MutableStateFlow(prefs.getInt("lensFacing", CameraSelector.LENS_FACING_BACK))
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _ocrThrottleMs = MutableStateFlow(prefs.getLong("ocrThrottleMs", 1_000L))
    val ocrThrottleMs: StateFlow<Long> = _ocrThrottleMs.asStateFlow()

    private val _pinyinAutoSize = MutableStateFlow(prefs.getBoolean("pinyinAutoSize", true))
    val pinyinAutoSize: StateFlow<Boolean> = _pinyinAutoSize.asStateFlow()

    private val _showDebug = MutableStateFlow(prefs.getBoolean("showDebug", false))
    val showDebug: StateFlow<Boolean> = _showDebug.asStateFlow()

    private val _showOcrDebugText = MutableStateFlow(prefs.getBoolean("showOcrDebugText", false))
    val showOcrDebugText: StateFlow<Boolean> = _showOcrDebugText.asStateFlow()

    // ── Word lookup (tap on frozen block) ────────────────────────────────────
    private val _wordLookups = MutableStateFlow<List<BlockLookupGroup>>(emptyList())
    val wordLookups: StateFlow<List<BlockLookupGroup>> = _wordLookups.asStateFlow()

    private val _frozenAnalyzing = MutableStateFlow(false)
    val frozenAnalyzing: StateFlow<Boolean> = _frozenAnalyzing.asStateFlow()

    // ── Debug metrics ─────────────────────────────────────────────────────────
    private val _debugMetrics = MutableStateFlow(DebugMetrics())
    val debugMetrics: StateFlow<DebugMetrics> = _debugMetrics.asStateFlow()

    // ── Refs set by composables ───────────────────────────────────────────────
    var ocrAnalyzerRef:  OcrAnalyzer?  = null
    var cameraManagerRef: CameraManager? = null


    private var lastCpuTicks = 0L
    private var lastWallMs   = 0L

    init {
        initDictionary()
        viewModelScope.launch {
            lastCpuTicks = readProcessCpuTicks()
            lastWallMs   = System.currentTimeMillis()
            while (true) { delay(1_000); pollSystemMetrics() }
        }
    }

    /** Re-run dictionary initialisation — called from the error splash screen. */
    fun retryInit() {
        _appState.value       = AppState.LOADING
        _loadProgress.value   = 0f
        _loadingMessage.value = "Retrying…"
        initDictionary()
    }

    private fun initDictionary() {
        viewModelScope.launch {
            try {
                repository.initialise { progress, message ->
                    _loadProgress.value   = progress
                    _loadingMessage.value = message
                }
                pinyinConverter = PinyinConverter(repository.getWordMap() ?: hashMapOf())
                _appState.value = AppState.READY
            } catch (e: Exception) {
                _loadingMessage.value = "Error: ${e.message}"
                _appState.value       = AppState.ERROR
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun onRoiChanged(normalized: RectF) { _roi.value = normalized }

    fun setZoom(linear: Float) {
        _linearZoom.value = linear
        cameraManagerRef?.setLinearZoom(linear)
    }

    fun setLensFacing(facing: Int) {
        _lensFacing.value = facing
        prefs.edit().putInt("lensFacing", facing).apply()
    }

    /**
     * Segment [blockText] using the pinyin converter and look up each word
     * in CC-CEDICT. Falls back to traditional-script lookup when simplified
     * has no entry. Results are emitted on [wordLookups].
     */
    fun lookupBlock(blockText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val converter = pinyinConverter ?: return@launch
            val result    = converter.convert(blockText)
            val tokens    = result.tokens.filter { it.word.containsChinese() }

            val words = tokens.map { token ->
                val entry = repository.findEntryAny(token.word)
                WordLookup(
                    word        = token.word,
                    traditional = entry?.traditional?.takeIf { it != token.word } ?: "",
                    pinyin      = token.pinyin.ifBlank { entry?.pinyin ?: "" },
                    definitions = entry?.definitions
                        ?.split("|")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                )
            }.filter { it.pinyin.isNotBlank() || it.definitions.isNotEmpty() }

            _wordLookups.value = listOf(
                BlockLookupGroup(
                    rawText    = blockText,
                    fullPinyin = result.fullPinyin,
                    words      = words
                )
            )
        }
    }

    fun clearLookup() { _wordLookups.value = emptyList() }

    /**
     * Resets all transient camera state back to "live, unfrozen" baseline.
     * Call this whenever the camera screen enters composition so that any
     * frozen results left over from a previous visit are discarded.
     */
    fun resetCameraState() {
        _ocrResults.value    = emptyList()
        _pinyinResults.value = emptyMap()
        _wordLookups.value   = emptyList()
    }

    /**
     * Runs a fresh ML Kit OCR pass on the frozen [bitmap] (full display resolution,
     * already correctly oriented — no rotation needed). Then converts pinyin and
     * looks up every Chinese word in CC-CEDICT automatically.
     *
     * Because the bitmap is static there is no motion blur or frame-to-frame
     * inconsistency, and the full display resolution gives ML Kit more detail
     * than the 640×480 live-analysis stream — results are noticeably more accurate.
     */
    fun analyzeFrozenBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _frozenAnalyzing.value = true
            val roi = _roi.value
            try {
                // Scale down very large bitmaps (e.g. >1920px) to avoid OOM
                // while still giving ML Kit a higher-resolution image than the
                // 640×480 live-analysis stream.
                val safeBitmap = scaleBitmapIfNeeded(bitmap, maxDim = 1920)
                val blocks = runMlKitOnBitmap(safeBitmap)
                    .filter { block ->
                        block.text.containsChinese() &&
                        RectF.intersects(
                            CoordinateMapper.imageRectToNormalized(
                                block.boundingBox,
                                safeBitmap.width, safeBitmap.height, 0
                            ), roi
                        )
                    }

                // Replace live OCR results with the higher-quality freeze results.
                // Also update image dimensions in metrics so OcrOverlay maps bounding
                // boxes against the frozen bitmap space (e.g. 1080×2400) instead of
                // the stale 640×480 live-stream dimensions — otherwise every box is off-screen.
                _ocrResults.value = blocks
                _debugMetrics.value = _debugMetrics.value.copy(
                    imageWidth      = safeBitmap.width,
                    imageHeight     = safeBitmap.height,
                    rotationDegrees = 0,
                    blocksDetected  = blocks.size
                )
                val converter = pinyinConverter
                if (converter != null) {
                    _pinyinResults.value = blocks.associate { it.text to converter.convert(it.text) }
                }

                // Build one BlockLookupGroup per OCR block so the sheet
                // can show them as separate sections.
                val groups = blocks.mapNotNull { block ->
                    val result = converter?.convert(block.text) ?: return@mapNotNull null
                    val words  = result.tokens
                        .filter { it.word.containsChinese() }
                        .map { token ->
                            val entry = repository.findEntryAny(token.word)
                            WordLookup(
                                word        = token.word,
                                traditional = entry?.traditional?.takeIf { it != token.word } ?: "",
                                pinyin      = token.pinyin.ifBlank { entry?.pinyin ?: "" },
                                definitions = entry?.definitions
                                    ?.split("|")
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotEmpty() }
                                    ?: emptyList()
                            )
                        }
                        .filter { it.pinyin.isNotBlank() || it.definitions.isNotEmpty() }

                    BlockLookupGroup(
                        rawText    = block.text,
                        fullPinyin = result.fullPinyin,
                        words      = words
                    ).takeIf { it.words.isNotEmpty() }
                }

                _wordLookups.value = groups

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _frozenAnalyzing.value = false
            }
        }
    }

    private fun scaleBitmapIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale  = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private suspend fun runMlKitOnBitmap(bitmap: Bitmap): List<RecognizedBlock> =
        suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            val input = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(input)
                .addOnSuccessListener { visionText ->
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
                    cont.resume(blocks)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCompleteListener  { recognizer.close() }
        }

    fun setOcrThrottleMs(ms: Long) {
        _ocrThrottleMs.value       = ms
        ocrAnalyzerRef?.throttleMs = ms
        prefs.edit().putLong("ocrThrottleMs", ms).apply()
    }

    fun setPinyinAutoSize(enabled: Boolean) {
        _pinyinAutoSize.value = enabled
        prefs.edit().putBoolean("pinyinAutoSize", enabled).apply()
    }

    fun setShowDebug(enabled: Boolean) {
        _showDebug.value = enabled
        prefs.edit().putBoolean("showDebug", enabled).apply()
    }

    fun setShowOcrDebugText(enabled: Boolean) {
        _showOcrDebugText.value = enabled
        prefs.edit().putBoolean("showOcrDebugText", enabled).apply()
    }

    fun onOcrResults(
        blocks: List<RecognizedBlock>,
        imageWidth: Int, imageHeight: Int,
        rotationDegrees: Int, latencyMs: Long
    ) {
        val roi      = _roi.value
        // rotationDegrees is 0 here (OcrAnalyzer already passes upright dims),
        // so CoordinateMapper simply normalises x/effW, y/effH — correct for ROI comparison.
        val filtered = blocks.filter { block ->
            block.text.containsChinese() &&
            RectF.intersects(
                CoordinateMapper.imageRectToNormalized(
                    block.boundingBox, imageWidth, imageHeight, rotationDegrees
                ), roi
            )
        }
        _ocrResults.value = filtered

        val converter = pinyinConverter
        if (converter != null) {
            _pinyinResults.value = filtered.associate { it.text to converter.convert(it.text) }
        }

        _debugMetrics.value = _debugMetrics.value.copy(
            ocrLatencyMs    = latencyMs,
            blocksDetected  = filtered.size,
            imageWidth      = imageWidth,
            imageHeight     = imageHeight,
            rotationDegrees = rotationDegrees,
            framesAnalyzed  = ocrAnalyzerRef?.framesAnalyzed?.get() ?: 0,
            framesSkipped   = ocrAnalyzerRef?.framesSkipped?.get()  ?: 0,
        )
    }

    // ── System metrics ────────────────────────────────────────────────────────

    private fun pollSystemMetrics() {
        val rt = Runtime.getRuntime()
        _debugMetrics.value = _debugMetrics.value.copy(
            heapUsedMb   = (rt.totalMemory() - rt.freeMemory()) / MB,
            heapMaxMb    = rt.maxMemory() / MB,
            nativeHeapMb = Debug.getNativeHeapAllocatedSize() / MB,
            cpuPercent   = calcCpuPercent(),
            threadCount  = Thread.activeCount(),
            framesAnalyzed = ocrAnalyzerRef?.framesAnalyzed?.get() ?: 0,
            framesSkipped  = ocrAnalyzerRef?.framesSkipped?.get()  ?: 0,
        )
    }

    private fun readProcessCpuTicks(): Long = try {
        File("/proc/self/stat").readText().split(" ").let { it[13].toLong() + it[14].toLong() }
    } catch (_: Exception) { 0L }

    private fun calcCpuPercent(): Float {
        val nowTicks   = readProcessCpuTicks()
        val nowMs      = System.currentTimeMillis()
        val ticksDelta = nowTicks - lastCpuTicks
        val wallDelta  = nowMs   - lastWallMs
        lastCpuTicks   = nowTicks
        lastWallMs     = nowMs
        return if (wallDelta > 0) (ticksDelta * 1_000f / wallDelta).coerceIn(0f, 100f) else 0f
    }

    private companion object { const val MB = 1_048_576f }
}
