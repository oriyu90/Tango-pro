package com.example.domain

import com.example.data.Word

object StudyProgress {
    fun isLearned(word: Word): Boolean = word.studyCount >= 2 && word.isCorrectLast

    fun isVague(word: Word): Boolean = word.studyCount == 1 && word.isCorrectLast

    fun needsReview(word: Word): Boolean = !isLearned(word) && !isVague(word)
}
