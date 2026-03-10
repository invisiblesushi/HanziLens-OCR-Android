package com.invisiblesushi.hanzilens.dictionary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Single source of truth for all dictionary and pinyin data.
 *
 * Two-tier storage:
 *  1. Room DB (hanzilens.db)   — full CEDICT data; queried for definitions.
 *  2. Binary cache file        — word→pinyin only; loaded into HashMap for
 *                                fast max-match segmentation (< 200ms startup).
 *
 * First-launch flow  (~30–60 s, one-time):
 *   assets/cedict_ts.u8 → parse → Room DB → binary cache
 *
 * Subsequent launches (< 200 ms):
 *   binary cache → HashMap in memory
 */
class DictionaryRepository(private val context: Context) {

    private val db  = AppDatabase.getInstance(context)
    private val dao = db.dictionaryDao()

    private val cacheFile: File get() = File(context.filesDir, "pinyin_cache.bin")

    @Volatile private var wordMap: HashMap<String, String>? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Must be called once at app startup (on a background coroutine).
     * [onProgress] receives a value in [0,1] and a status string.
     */
    suspend fun initialise(onProgress: (Float, String) -> Unit = { _, _ -> }) {
        withContext(Dispatchers.IO) {
            val populated = dao.count() > 0

            if (!populated) {
                onProgress(0.00f, "Reading dictionary file…")
                parseAndInsert(onProgress)
                onProgress(0.88f, "Building fast index…")
                buildBinaryCache()
            } else if (!cacheFile.exists()) {
                onProgress(0.50f, "Rebuilding fast index…")
                buildBinaryCache()
            }

            onProgress(0.95f, "Loading index…")
            wordMap = loadBinaryCache()
            onProgress(1.00f, "Ready")
        }
    }

    fun getWordMap(): HashMap<String, String>? = wordMap

    suspend fun findEntry(simplified: String): DictionaryEntry? =
        withContext(Dispatchers.IO) { dao.findBySimplified(simplified) }

    /** Try simplified first, then traditional — covers both script variants. */
    suspend fun findEntryAny(word: String): DictionaryEntry? =
        withContext(Dispatchers.IO) {
            dao.findBySimplified(word) ?: dao.findByTraditional(word)
        }

    // ── First-launch: parse assets file and populate Room DB ─────────────────

    private suspend fun parseAndInsert(onProgress: (Float, String) -> Unit) {
        val entries = withContext(Dispatchers.IO) {
            context.assets.open("cedict_ts.u8")
                .bufferedReader(Charsets.UTF_8)
                .lineSequence()
                .mapNotNull { CedictParser.parseLine(it) }
                .toList()
        }

        val batchSize = 500
        val batches   = entries.chunked(batchSize)
        batches.forEachIndexed { i, batch ->
            dao.insertAll(batch)
            val progress = 0.05f + (i.toFloat() / batches.size) * 0.80f
            onProgress(progress, "Importing… ${(i + 1) * batchSize} / ${entries.size}")
        }
    }

    // ── Binary cache ─────────────────────────────────────────────────────────

    /**
     * Writes a compact binary file: count(4B) + [wordLen(1B) word pinyinLen(1B) pinyin] …
     * Typical size ~2 MB for 120 k entries.
     */
    private suspend fun buildBinaryCache() = withContext(Dispatchers.IO) {
        val rows = dao.getAllSimplifiedPinyin()
        DataOutputStream(cacheFile.outputStream().buffered()).use { out ->
            out.writeInt(rows.size)
            rows.forEach { row ->
                val wordBytes   = row.simplified.toByteArray(Charsets.UTF_8)
                val pinyinBytes = row.pinyin.toByteArray(Charsets.UTF_8)
                out.writeByte(wordBytes.size)
                out.write(wordBytes)
                out.writeByte(pinyinBytes.size)
                out.write(pinyinBytes)
            }
        }
    }

    private fun loadBinaryCache(): HashMap<String, String> {
        val map = HashMap<String, String>(150_000)
        DataInputStream(cacheFile.inputStream().buffered()).use { input ->
            val count = input.readInt()
            repeat(count) {
                val word   = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                val pinyin = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                map[String(word, Charsets.UTF_8)] = String(pinyin, Charsets.UTF_8)
            }
        }
        return map
    }
}
