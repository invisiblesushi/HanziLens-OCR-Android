package com.invisiblesushi.hanzilens.pinyin

/**
 * A single segmented unit — one word (possibly multiple characters) and its pinyin.
 * e.g. word="银行" pinyin="yín háng"
 */
data class PinyinToken(
    val word: String,
    val pinyin: String   // tone-marked, e.g. "yín háng"
)

/**
 * Full pinyin conversion result for a block of text.
 * [tokens] preserves word boundaries from max-match segmentation.
 */
data class PinyinResult(
    val original: String,
    val tokens: List<PinyinToken>
) {
    /** Space-separated full pinyin string, e.g. "nǐ hǎo zhōng guó" */
    val fullPinyin: String get() = tokens.joinToString(" ") { it.pinyin }.trim()
}
