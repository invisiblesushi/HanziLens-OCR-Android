package com.invisiblesushi.hanzilens.dictionary

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entries",
    indices = [Index(value = ["simplified"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val traditional: String,
    val simplified: String,
    val pinyin: String,         // tone-marked: "nǐ hǎo"
    val pinyinNumbered: String, // numbered:    "ni3 hao3" (for search/sort)
    val definitions: String     // pipe-separated: "Hello!|Hi!|How are you?"
)
