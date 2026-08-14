package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SaveDataExport(
    val version: Int = 1,
    val groups: List<StudyGroupExport>
)

@JsonClass(generateAdapter = true)
data class StudyGroupExport(
    val name: String,
    val language: String,
    val words: List<WordExport>
)

@JsonClass(generateAdapter = true)
data class WordExport(
    val english: String,
    val japanese: String,
    val tag: String,
    val pronunciation: String,
    val studyCount: Int,
    val isCorrectLast: Boolean,
    val lastStudiedAt: Long
)
