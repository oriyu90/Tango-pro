package com.example.data

import com.squareup.moshi.JsonClass

const val STUDY_ARCHIVE_FORMAT = "tango-pro-study-archive"
const val STUDY_ARCHIVE_VERSION = 1

@JsonClass(generateAdapter = true)
data class StudyArchiveManifest(
    val format: String,
    val version: Int,
    val exportedAtEpochMillis: Long,
    val appVersion: String,
    val groups: List<StudyArchiveGroupManifest>
)

@JsonClass(generateAdapter = true)
data class StudyArchiveGroupManifest(
    val id: String,
    val name: String,
    val language: String,
    val csvPath: String,
    val progressPath: String,
    val csvSha256: String
)

@JsonClass(generateAdapter = true)
data class StudyArchiveProgress(
    val version: Int,
    val csvSha256: String,
    val records: List<StudyArchiveRecord>
)

@JsonClass(generateAdapter = true)
data class StudyArchiveRecord(
    val row: Int,
    val studyCount: Int,
    val isCorrectLast: Boolean,
    val lastStudiedAt: Long
)

data class StudyArchiveWord(
    val term: String,
    val meaning: String,
    val tag: String,
    val pronunciation: String,
    val progress: StudyArchiveRecord
) {
    fun csvFields(): List<String> = listOf(term, meaning, tag, pronunciation)
}

data class StudyArchiveExportGroup(
    val name: String,
    val language: String,
    val words: List<StudyArchiveWord>
)

data class StudyArchiveImportedGroup(
    val name: String,
    val language: String,
    val words: List<StudyArchiveWord>,
    val canonicalCsv: String
)

data class StudyArchiveImportSummary(
    val mergedGroups: Int,
    val addedGroups: Int,
    val mergedWords: Int,
    val addedWords: Int,
    val firstAddedGroupId: Long?
)
