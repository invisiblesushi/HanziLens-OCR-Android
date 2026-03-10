package com.invisiblesushi.hanzilens.pinyin

/**
 * Converts a string of Chinese text to pinyin using greedy forward
 * maximum-match segmentation against [wordMap] (built from CC-CEDICT).
 *
 * Why max-match?
 *  - CEDICT entries are at word level, so a 2-character lookup gives the
 *    correct reading for polyphonic characters (e.g. 银行 → yín háng, not xíng).
 *  - Greedy forward max-match correctly handles the vast majority of common
 *    Chinese text without requiring a statistical model.
 *
 * Characters not found in [wordMap] are returned with an empty pinyin string
 * so they are still visible in the output.
 */
class PinyinConverter(private val wordMap: HashMap<String, String>) {

    /** Longest word in CEDICT is ~7 characters. */
    private val maxLen = 7

    fun convert(text: String): PinyinResult {
        val tokens = mutableListOf<PinyinToken>()
        var i = 0

        while (i < text.length) {
            val remaining = text.length - i
            var matched   = false

            // Try longest match first, shrink until found
            for (len in minOf(maxLen, remaining) downTo 1) {
                val candidate = text.substring(i, i + len)
                val pinyin    = wordMap[candidate]
                if (pinyin != null) {
                    tokens += PinyinToken(candidate, pinyin)
                    i      += len
                    matched = true
                    break
                }
            }

            if (!matched) {
                // Unknown character — pass through with no pinyin
                tokens += PinyinToken(text[i].toString(), "")
                i++
            }
        }

        return PinyinResult(original = text, tokens = tokens)
    }
}
