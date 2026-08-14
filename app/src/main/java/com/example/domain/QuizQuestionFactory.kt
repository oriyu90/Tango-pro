package com.example.domain

import com.example.data.StudyQuestion
import com.example.data.Word

object QuizQuestionFactory {
    private val japaneseFallbacks = listOf("りんご", "本", "走る", "犬", "机", "山", "海", "学校", "花", "車")
    private val targetLanguageFallbacks = listOf("apple", "book", "run", "dog", "desk", "mountain", "sea", "school", "flower", "car")

    fun create(
        sessionWords: List<Word>,
        allWords: List<Word>,
        directionForward: Boolean,
        isMultipleChoice: Boolean
    ): List<StudyQuestion> = sessionWords.map { word ->
        val questionText = if (directionForward) word.english else word.japanese
        val correctAnswer = if (directionForward) word.japanese else word.english
        val choices = if (isMultipleChoice) {
            buildChoices(word, correctAnswer, allWords, directionForward)
        } else {
            emptyList()
        }
        StudyQuestion(
            word = word,
            questionText = questionText,
            correctAnswer = correctAnswer,
            choices = choices,
            directionForward = directionForward,
            isMultipleChoice = isMultipleChoice
        )
    }

    private fun buildChoices(
        word: Word,
        correctAnswer: String,
        allWords: List<Word>,
        directionForward: Boolean
    ): List<String> {
        val choices = allWords.asSequence()
            .filter { it.id != word.id }
            .map { if (directionForward) it.japanese else it.english }
            .filter { it != correctAnswer }
            .distinct()
            .shuffled()
            .take(3)
            .toMutableList()

        val fallbacks = if (directionForward) japaneseFallbacks else targetLanguageFallbacks
        choices += fallbacks.asSequence()
            .filter { it != correctAnswer && it !in choices }
            .shuffled()
            .take(3 - choices.size)

        choices += correctAnswer
        return choices.distinct().shuffled()
    }
}
