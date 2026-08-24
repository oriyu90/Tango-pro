package com.example.domain

object AnswerNormalizer {
    fun normalize(value: String): String = com.example.tangopro.domain.AnswerNormalizer.normalize(value)

    fun matches(userAnswer: String, correctAnswer: String): Boolean =
        com.example.tangopro.domain.AnswerNormalizer.matches(userAnswer, correctAnswer)
}
