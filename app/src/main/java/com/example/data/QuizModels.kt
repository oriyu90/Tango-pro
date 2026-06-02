package com.example.data

data class StudyQuestion(
    val word: Word,
    val questionText: String,
    val correctAnswer: String,
    val choices: List<String>,
    val directionForward: Boolean,
    val isMultipleChoice: Boolean
)

data class QuizResult(
    val questionText: String,
    val correctAnswer: String,
    val userAnswer: String,
    val isCorrect: Boolean,
    val wordId: Long
)
