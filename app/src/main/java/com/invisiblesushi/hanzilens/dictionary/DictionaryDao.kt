package com.invisiblesushi.hanzilens.dictionary

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    @Query("SELECT * FROM entries WHERE simplified = :word LIMIT 1")
    suspend fun findBySimplified(word: String): DictionaryEntry?

    @Query("SELECT * FROM entries WHERE traditional = :word LIMIT 1")
    suspend fun findByTraditional(word: String): DictionaryEntry?

    /** Used to build the in-memory segmentation HashMap on startup. */
    @Query("SELECT simplified, pinyin FROM entries")
    suspend fun getAllSimplifiedPinyin(): List<SimplifiedPinyinRow>
}

/** Lightweight projection — only what PinyinConverter needs. */
data class SimplifiedPinyinRow(
    val simplified: String,
    val pinyin: String
)
