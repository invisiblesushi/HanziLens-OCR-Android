package com.invisiblesushi.hanzilens.dictionary

/**
 * Parses CC-CEDICT lines into [DictionaryEntry] objects.
 *
 * CEDICT format:
 *   Traditional Simplified [pin1 yin1] /definition1/definition2/
 *
 * Pinyin in CEDICT uses tone numbers (ni3 hao3).
 * [numberedToToneMarks] converts them to Unicode diacritics (nǐ hǎo).
 */
object CedictParser {

    private val LINE_REGEX = Regex("""^(\S+)\s+(\S+)\s+\[([^\]]+)]\s+/(.+)/$""")

    fun parseLine(line: String): DictionaryEntry? {
        if (line.startsWith('#') || line.isBlank()) return null
        val match = LINE_REGEX.matchEntire(line.trim()) ?: return null

        val traditional     = match.groupValues[1]
        val simplified      = match.groupValues[2]
        val pinyinNumbered  = match.groupValues[3]
        val definitions     = match.groupValues[4].split('/').joinToString("|")

        return DictionaryEntry(
            traditional    = traditional,
            simplified     = simplified,
            pinyin         = numberedToToneMarks(pinyinNumbered),
            pinyinNumbered = pinyinNumbered,
            definitions    = definitions
        )
    }

    // ── Tone-mark conversion ──────────────────────────────────────────────────

    fun numberedToToneMarks(pinyin: String): String =
        pinyin.split(' ').joinToString(" ") { convertSyllable(it) }

    private fun convertSyllable(raw: String): String {
        if (raw.isEmpty()) return raw

        val tone = raw.last().digitToIntOrNull() ?: return raw
        // Normalise: lower-case, replace CEDICT ü spellings
        val base = raw.dropLast(1)
            .lowercase()
            .replace("u:", "ü")
            .replace("v", "ü")

        // Tone 5 (neutral) — no diacritic
        if (tone == 5) return base

        // Rule 1 — 'a' or 'e' always takes the mark
        val aIdx = base.indexOf('a')
        val eIdx = base.indexOf('e')
        if (aIdx >= 0) return base.replaceRange(aIdx, aIdx + 1, MARKS['a']!![tone - 1])
        if (eIdx >= 0) return base.replaceRange(eIdx, eIdx + 1, MARKS['e']!![tone - 1])

        // Rule 2 — "ou" → mark the 'o'
        val ouIdx = base.indexOf("ou")
        if (ouIdx >= 0) return base.replaceRange(ouIdx, ouIdx + 1, MARKS['o']!![tone - 1])

        // Rule 3 — last vowel in the syllable
        val lastVowel = base.indexOfLast { it in MARKS }
        if (lastVowel >= 0) {
            val vowel = base[lastVowel]
            return base.replaceRange(lastVowel, lastVowel + 1, MARKS[vowel]!![tone - 1])
        }

        return base
    }

    /** tone index 0 = tone 1, 1 = tone 2, 2 = tone 3, 3 = tone 4 */
    private val MARKS: Map<Char, List<String>> = mapOf(
        'a' to listOf("ā", "á", "ǎ", "à"),
        'e' to listOf("ē", "é", "ě", "è"),
        'i' to listOf("ī", "í", "ǐ", "ì"),
        'o' to listOf("ō", "ó", "ǒ", "ò"),
        'u' to listOf("ū", "ú", "ǔ", "ù"),
        'ü' to listOf("ǖ", "ǘ", "ǚ", "ǜ")
    )
}
