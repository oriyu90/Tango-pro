package com.example.tangopro.model

data class StudyGroupRecord(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
    val language: String = "en",
    val sortOrder: Int = 0,
)

data class WordRecord(
    val id: Long = 0,
    val groupId: Long = 0,
    val term: String,
    val meaning: String,
    val tag: String = "",
    val pronunciation: String = "",
    val studyCount: Int = 0,
    val isCorrectLast: Boolean = true,
    val lastStudiedAt: Long = 0,
)

data class StudyQuestion(
    val word: WordRecord,
    val questionText: String,
    val correctAnswer: String,
    val choices: List<String>,
    val directionForward: Boolean,
    val isMultipleChoice: Boolean,
)
